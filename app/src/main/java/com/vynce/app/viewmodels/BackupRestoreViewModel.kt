package com.vynce.app.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vynce.app.MainActivity
import com.vynce.app.R
import com.vynce.app.db.InternalDatabase
import com.vynce.app.db.MusicDatabase
import com.vynce.app.extensions.div
import com.vynce.app.extensions.zipInputStream
import com.vynce.app.extensions.zipOutputStream
import com.vynce.app.playback.MusicService
import com.vynce.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val TAG = BackupRestoreViewModel::class.simpleName.toString()
    fun backup(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                        it.buffered().zipOutputStream().use { outputStream ->
                            outputStream.setLevel(Deflater.BEST_COMPRESSION)
                            (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered()
                                .use { inputStream ->
                                    outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                                    inputStream.copyTo(outputStream)
                                }
                            database.checkpoint()
                            FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                                outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                }
            }
            result.onSuccess {
                Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
            }.onFailure {
                reportException(it)
                Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
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
                                            val t =
                                                InternalDatabase.newTestInstance(context, InternalDatabase.TEST_DB_NAME)
                                            t.openHelper.writableDatabase.isDatabaseIntegrityOk
                                            t.close()
                                            true
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
                                            withContext(Dispatchers.Main) {
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
                }
            }
            result.onSuccess {
                val stopIntent = Intent(context, MusicService::class.java)
                context.stopService(stopIntent)
                val startIntent = Intent(context, MainActivity::class.java)
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(startIntent)
                exitProcess(0)
            }.onFailure {
                reportException(it)
                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
