/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Provider-agnostic data model for widget state transport.
 * Serialized to JSON and persisted via GlanceStateDefinition.
 */

package com.vynce.app.widget

import org.json.JSONObject

/**
 * Immutable snapshot of the current playback state, designed for widget display.
 * This model is provider-agnostic — it carries no JioSaavn-specific types.
 */
data class PlayerInfo(
    val songTitle: String = "",
    val artistName: String = "",
    val albumArtUri: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val repeatMode: Int = 0, // Player.REPEAT_MODE_OFF
    val isShuffleEnabled: Boolean = false,
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("songTitle", songTitle)
            put("artistName", artistName)
            put("albumArtUri", albumArtUri ?: JSONObject.NULL)
            put("isPlaying", isPlaying)
            put("currentPositionMs", currentPositionMs)
            put("totalDurationMs", totalDurationMs)
            put("isFavorite", isFavorite)
            put("repeatMode", repeatMode)
            put("isShuffleEnabled", isShuffleEnabled)
        }.toString()
    }

    companion object {
        /** Default empty state shown when no song is loaded. */
        val EMPTY = PlayerInfo()

        fun fromJson(json: String): PlayerInfo {
            return try {
                val obj = JSONObject(json)
                PlayerInfo(
                    songTitle = obj.optString("songTitle", ""),
                    artistName = obj.optString("artistName", ""),
                    albumArtUri = obj.optString("albumArtUri", "")
                        .takeIf { it.isNotBlank() && it != "null" },
                    isPlaying = obj.optBoolean("isPlaying", false),
                    currentPositionMs = obj.optLong("currentPositionMs", 0L),
                    totalDurationMs = obj.optLong("totalDurationMs", 0L),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    repeatMode = obj.optInt("repeatMode", 0),
                    isShuffleEnabled = obj.optBoolean("isShuffleEnabled", false),
                )
            } catch (e: Exception) {
                EMPTY
            }
        }
    }
}
