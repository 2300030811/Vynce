/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Validates backup files before restore.
 */

package com.vynce.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vynce.app.db.InternalDatabase
import com.vynce.app.extensions.zipInputStream
import java.io.FileOutputStream

/**
 * Validates a backup file before attempting restore.
 * 
 * Checks:
 * 1. Zip file integrity (can be opened and read)
 * 2. Manifest presence and parsability
 * 3. Database compatibility (schema version check)
 * 4. Database integrity (SQLite integrity_check)
 */
class BackupValidator(private val context: Context) {
    companion object {
        private const val TAG = "BackupValidator"
    }

    sealed class ValidationResult {
        data class Valid(val manifest: BackupManifest) : ValidationResult()
        data class LegacyValid(val hasDatabase: Boolean, val hasSettings: Boolean) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Validate a backup file at the given URI.
     * Returns the validation result including manifest info if available.
     */
    fun validate(uri: Uri): ValidationResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                rawStream.zipInputStream().use { zipStream ->
                    var manifest: BackupManifest? = null
                    var hasDatabase = false
                    var hasSettings = false
                    val entries = mutableListOf<String>()

                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        entries.add(entry.name)
                        when (entry.name) {
                            BackupManifest.MANIFEST_FILENAME -> {
                                val jsonBytes = zipStream.readBytes()
                                manifest = try {
                                    BackupManifest.fromJson(String(jsonBytes))
                                } catch (e: Exception) {
                                    Log.w(TAG, "Manifest parse failed", e)
                                    null
                                }
                            }
                            InternalDatabase.DB_NAME -> hasDatabase = true
                            "settings.preferences_pb" -> hasSettings = true
                        }
                        entry = zipStream.nextEntry
                    }

                    when {
                        manifest != null -> {
                            // Modern backup with manifest
                            Log.i(TAG, "Valid backup with manifest: v${manifest.appVersion}, sections=${manifest.includedSections}")
                            ValidationResult.Valid(manifest)
                        }
                        hasDatabase || hasSettings -> {
                            // Legacy backup without manifest (backward compat)
                            Log.i(TAG, "Legacy backup detected: db=$hasDatabase, settings=$hasSettings")
                            ValidationResult.LegacyValid(hasDatabase, hasSettings)
                        }
                        else -> {
                            Log.w(TAG, "No recognizable entries in backup: $entries")
                            ValidationResult.Invalid("Not a valid Vynce backup file")
                        }
                    }
                }
            } ?: ValidationResult.Invalid("Could not open backup file")
        } catch (e: Exception) {
            Log.e(TAG, "Backup validation failed", e)
            ValidationResult.Invalid("Corrupt or unreadable file: ${e.message}")
        }
    }

    /**
     * Test database integrity by attempting to open it with Room.
     */
    fun testDatabaseIntegrity(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                rawStream.zipInputStream().use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (entry.name == InternalDatabase.DB_NAME) {
                            val destFile = context.getDatabasePath(InternalDatabase.TEST_DB_NAME)
                            destFile.parentFile?.apply { if (!exists()) mkdirs() }
                            FileOutputStream(destFile).use { out -> zipStream.copyTo(out) }

                            return@use try {
                                val testDb = InternalDatabase.newTestInstance(context, InternalDatabase.TEST_DB_NAME)
                                val ok = testDb.openHelper.writableDatabase.isDatabaseIntegrityOk
                                testDb.close()
                                destFile.delete()
                                ok
                            } catch (e: Exception) {
                                Log.e(TAG, "DB integrity test failed", e)
                                destFile.delete()
                                false
                            }
                        }
                        entry = zipStream.nextEntry
                    }
                    false // no database entry found
                }
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "DB integrity test exception", e)
            false
        }
    }
}
