/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Backup manifest for identifying backup contents and compatibility.
 */

package com.vynce.app.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Metadata manifest stored inside the backup zip.
 * Used for validation and display before restore.
 */
@Serializable
data class BackupManifest(
    /** Backup format version — increment when format changes */
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** App version name at time of backup */
    val appVersion: String,
    /** App version code at time of backup */
    val appVersionCode: Long,
    /** Database schema version at time of backup */
    val dbSchemaVersion: Int,
    /** Timestamp of backup creation (epoch millis) */
    val createdAt: Long,
    /** Device model info */
    val deviceModel: String,
    /** Android SDK version */
    val androidSdk: Int,
    /** Which sections are included in this backup */
    val includedSections: List<String>,
    /** Optional user-provided description */
    val description: String? = null,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val MANIFEST_FILENAME = "manifest.json"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(jsonString: String): BackupManifest {
            return json.decodeFromString<BackupManifest>(jsonString)
        }
    }

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }

    val includedBackupSections: Set<BackupSection>
        get() = includedSections.mapNotNull { name ->
            try { BackupSection.valueOf(name) } catch (_: Exception) { null }
        }.toSet()
}
