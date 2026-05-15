package com.vynce.app.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import com.vynce.app.MainActivity
import com.vynce.app.R
import com.vynce.app.constants.AutoBackupFrequency
import com.vynce.app.constants.AutoBackupFrequencyKey
import com.vynce.app.constants.AutoBackupKey
import com.vynce.app.constants.LastAutoBackupKey
import com.vynce.app.constants.MaxAutoBackupsKey
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

    fun backup(uri: Uri) {
        ioScope.launch(Dispatchers.IO) {
            runCatching {
                context.applicationContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    writeBackup(outputStream)
                } ?: error("Could not open backup destination")
            }.onSuccess {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                reportException(it)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun restore(uri: Uri) {
        ioScope.launch(Dispatchers.IO) {
            runCatching {
                context.applicationContext.contentResolver.openInputStream(uri)?.use {
                    it.zipInputStream().use { inputStream ->
                        var entry = inputStream.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                SETTINGS_FILENAME -> {
                                    (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream()
                                        .use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                }

                                InternalDatabase.DB_NAME -> {
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
                                        destFile.inputStream().use { inputStream ->
                                            FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                                inputStream.copyTo(outputStream)
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
                            entry = inputStream.nextEntry
                        }
                    }
                }

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

    private fun writeBackup(outputStream: OutputStream) {
        outputStream.buffered().zipOutputStream().use { zipStream ->
            zipStream.setLevel(Deflater.BEST_COMPRESSION)

            val settingsFile = context.filesDir / "datastore" / SETTINGS_FILENAME
            if (settingsFile.exists()) {
                settingsFile.inputStream().buffered().use { inputStream ->
                    zipStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                    inputStream.copyTo(zipStream)
                    zipStream.closeEntry()
                }
            }

            database.checkpoint()
            FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                zipStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                inputStream.copyTo(zipStream)
                zipStream.closeEntry()
            }
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

