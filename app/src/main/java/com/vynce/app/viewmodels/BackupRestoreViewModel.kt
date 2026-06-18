package com.vynce.app.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import com.vynce.app.BuildConfig
import com.vynce.app.MainActivity
import com.vynce.app.R
import com.vynce.app.constants.AutoBackupFrequency
import com.vynce.app.constants.AutoBackupFrequencyKey
import com.vynce.app.constants.AutoBackupKey
import com.vynce.app.constants.LastAutoBackupKey
import com.vynce.app.constants.MaxAutoBackupsKey
import com.vynce.app.data.backup.BackupManifest
import com.vynce.app.data.backup.BackupSection
import com.vynce.app.data.backup.BackupValidator
import com.vynce.app.db.InternalDatabase
import com.vynce.app.db.MusicDatabase
import com.vynce.app.extensions.div
import com.vynce.app.extensions.toEnum
import com.vynce.app.extensions.zipInputStream
import com.vynce.app.extensions.zipOutputStream
import com.vynce.app.playback.MusicService
import com.vynce.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ContextDatabaseViewModel(context, database) {
    val TAG = BackupRestoreViewModel::class.simpleName.toString()

    /** Backup/restore progress (0.0 to 1.0) */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    /** Whether a backup/restore operation is in progress */
    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating

    /** Selected backup sections */
    val selectedSections = MutableStateFlow(BackupSection.DEFAULT_SELECTION)

    /** Validation result for the most recent file */
    private val _validationResult = MutableStateFlow<BackupValidator.ValidationResult?>(null)
    val validationResult: StateFlow<BackupValidator.ValidationResult?> = _validationResult

    val backupValidator = BackupValidator(context)

    fun backup(uri: Uri) {
        ioScope.launch(Dispatchers.IO) {
            _isOperating.value = true
            _progress.value = 0f
            runCatching {
                context.applicationContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    writeBackup(outputStream, selectedSections.value)
                } ?: error("Could not open backup destination")
            }.onSuccess {
                _progress.value = 1f
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                reportException(it)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
                }
            }
            _isOperating.value = false
        }
    }

    fun validateBackup(uri: Uri) {
        ioScope.launch(Dispatchers.IO) {
            _validationResult.value = backupValidator.validate(uri)
        }
    }

    fun restore(uri: Uri) {
        ioScope.launch(Dispatchers.IO) {
            _isOperating.value = true
            _progress.value = 0f
            runCatching {
                context.applicationContext.contentResolver.openInputStream(uri)?.use {
                    it.zipInputStream().use { inputStream ->
                        var entry = inputStream.nextEntry
                        val totalEntries = 4f // approximate
                        var processedEntries = 0
                        while (entry != null) {
                            when (entry.name) {
                                BackupManifest.MANIFEST_FILENAME -> {
                                    // Skip manifest during restore (it's for validation only)
                                    inputStream.readBytes() // consume entry
                                }
                                SETTINGS_FILENAME -> {
                                    if (selectedSections.value.contains(BackupSection.SETTINGS)) {
                                        (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream()
                                            .use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                    }
                                }

                                InternalDatabase.DB_NAME -> {
                                    if (selectedSections.value.contains(BackupSection.DATABASE)) {
                                        Log.i(TAG, "Starting database restore")
                                        database.checkpoint()
                                        database.close()

                                        Log.i(TAG, "Testing new database for compatibility...")
                                        val destFile = context.getDatabasePath(InternalDatabase.TEST_DB_NAME)
                                        destFile.parentFile?.apply {
                                            if (!exists()) mkdirs()
                                        }
                                        FileOutputStream(destFile).use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }

                                        val status = try {
                                            val t = InternalDatabase.newTestInstance(context, InternalDatabase.TEST_DB_NAME)
                                            val integrity = t.openHelper.writableDatabase.isDatabaseIntegrityOk
                                            t.close()
                                            integrity
                                        } catch (e: Exception) {
                                            Log.e(TAG, "DB validation failed", e)
                                            false
                                        }

                                        if (status) {
                                            Log.i(TAG, "Found valid database, proceeding with restore")
                                            destFile.inputStream().use { dbInputStream ->
                                                FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                                    dbInputStream.copyTo(outputStream)
                                                }
                                            }
                                        } else {
                                            Log.e(TAG, "Incompatible database, aborting restore")
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.err_restore_incompatible_database),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                            processedEntries++
                            _progress.value = (processedEntries / totalEntries).coerceAtMost(0.9f)
                            entry = inputStream.nextEntry
                        }
                    }
                }
                _progress.value = 1f

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val stopIntent = Intent(context, MusicService::class.java)
                    context.stopService(stopIntent)
                    val startIntent = Intent(context, MainActivity::class.java)
                    startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(startIntent)
                    exitProcess(0)
                }
            }.onFailure {
                reportException(it)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                }
            }
            _isOperating.value = false
        }
    }

    fun autoBackup() {
        ioScope.launch(Dispatchers.IO) {
            val enabled = dataStore.data.map { it[AutoBackupKey] ?: false }.first()
            if (!enabled) return@launch

            val frequency = dataStore.data
                .map { it[AutoBackupFrequencyKey].toEnum(AutoBackupFrequency.DAILY) }
                .first()
            val lastBackup = dataStore.data.map { it[LastAutoBackupKey] ?: 0L }.first()
            val now = System.currentTimeMillis()
            if (now - lastBackup < frequency.intervalMillis) return@launch

            val backupDir = File(context.filesDir, "backups/auto")
            if (!backupDir.exists()) backupDir.mkdirs()

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            val fileName = "vynce_auto_${LocalDateTime.now().format(formatter)}.zip"
            val backupFile = File(backupDir, fileName)
            val tempFile = File(backupDir, "$fileName.tmp")

            runCatching {
                FileOutputStream(tempFile).use { outputStream ->
                    writeBackup(outputStream)
                }
                if (backupFile.exists()) backupFile.delete()
                check(tempFile.renameTo(backupFile)) { "Could not finalize auto backup" }
                dataStore.edit { it[LastAutoBackupKey] = now }
                trimBackups()
                Log.i(TAG, "Auto backup created: $fileName")
            }.onFailure {
                tempFile.delete()
                Log.e(TAG, "Auto backup failed", it)
            }
        }
    }

    private fun writeBackup(
        outputStream: OutputStream,
        sections: Set<BackupSection> = BackupSection.DEFAULT_SELECTION
    ) {
        outputStream.buffered().zipOutputStream().use { zipStream ->
            zipStream.setLevel(Deflater.BEST_COMPRESSION)
            _progress.value = 0.1f

            // Write manifest first
            val manifest = BackupManifest(
                appVersion = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                dbSchemaVersion = MusicDatabase.MUSIC_DATABASE_VERSION,
                createdAt = System.currentTimeMillis(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidSdk = Build.VERSION.SDK_INT,
                includedSections = sections.map { it.name },
            )
            zipStream.putNextEntry(ZipEntry(BackupManifest.MANIFEST_FILENAME))
            zipStream.write(manifest.toJson().toByteArray())
            zipStream.closeEntry()
            _progress.value = 0.2f

            if (sections.contains(BackupSection.SETTINGS)) {
                val settingsFile = context.filesDir / "datastore" / SETTINGS_FILENAME
                if (settingsFile.exists()) {
                    settingsFile.inputStream().buffered().use { inputStream ->
                        zipStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        inputStream.copyTo(zipStream)
                        zipStream.closeEntry()
                    }
                }
            }
            _progress.value = 0.5f

            if (sections.contains(BackupSection.DATABASE)) {
                database.checkpoint()
                FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                    zipStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                    inputStream.copyTo(zipStream)
                    zipStream.closeEntry()
                }
            }
            _progress.value = 0.9f
        }
    }

    private suspend fun trimBackups() {
        val maxBackups = dataStore.data.map { it[MaxAutoBackupsKey] ?: 10 }.first()
        val backupDir = File(context.filesDir, "backups/auto")
        if (!backupDir.exists()) return

        val files = backupDir.listFiles { _, name -> name.startsWith("vynce_auto_") && name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() } ?: return

        if (files.size > maxBackups) {
            files.drop(maxBackups).forEach {
                it.delete()
                Log.i(TAG, "Trimmed old auto backup: ${it.name}")
            }
        }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
