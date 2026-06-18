/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Inspired by PixelMusic's modular backup system.
 */

package com.vynce.app.data.backup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a selectable section of data that can be included
 * or excluded from a backup/restore operation.
 */
enum class BackupSection(
    val label: String,
    val description: String,
    val icon: ImageVector,
    /** Zip entry name pattern for this section */
    val zipEntryName: String,
) {
    DATABASE(
        label = "Music Library",
        description = "Songs, albums, artists, play history, and queue data",
        icon = Icons.Rounded.MusicNote,
        zipEntryName = "database.db",
    ),
    SETTINGS(
        label = "App Settings",
        description = "Theme, player, scanner, and interface preferences",
        icon = Icons.Rounded.Settings,
        zipEntryName = "settings.preferences_pb",
    ),
    PLAYLISTS(
        label = "Playlists",
        description = "Custom playlists and their song mappings",
        icon = Icons.Rounded.PlaylistPlay,
        zipEntryName = "playlists.json",
    ),
    STATS(
        label = "Listening Stats",
        description = "Play counts, listening time, and event history",
        icon = Icons.Rounded.Dataset,
        zipEntryName = "stats.json",
    );

    companion object {
        /** Default sections to include in a backup */
        val DEFAULT_SELECTION = entries.toSet()
    }
}
