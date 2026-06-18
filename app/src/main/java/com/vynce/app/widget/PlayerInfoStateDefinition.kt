/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * GlanceStateDefinition that persists PlayerInfo as JSON in a DataStore file.
 * This is the bridge between MusicService (data producer) and Glance widgets (data consumers).
 */

package com.vynce.app.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.glance.state.GlanceStateDefinition
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer that reads/writes [PlayerInfo] as JSON bytes.
 */
object PlayerInfoSerializer : Serializer<PlayerInfo> {
    override val defaultValue: PlayerInfo = PlayerInfo.EMPTY

    override suspend fun readFrom(input: InputStream): PlayerInfo {
        return try {
            val json = input.bufferedReader().use { it.readText() }
            if (json.isBlank()) PlayerInfo.EMPTY else PlayerInfo.fromJson(json)
        } catch (e: Exception) {
            PlayerInfo.EMPTY
        }
    }

    override suspend fun writeTo(t: PlayerInfo, output: OutputStream) {
        output.bufferedWriter().use { it.write(t.toJson()) }
    }
}

/**
 * GlanceStateDefinition for [PlayerInfo].
 *
 * Each widget instance gets its own DataStore file under the Glance state directory,
 * but all instances share the same serialization logic. The MusicService writes state
 * via [androidx.glance.appwidget.updateAppWidgetState] and then calls [GlanceAppWidget.update].
 */
object PlayerInfoStateDefinition : GlanceStateDefinition<PlayerInfo> {
    private const val FILE_NAME = "vynce_widget_player_info"

    private val Context.playerInfoDataStore by dataStore(
        fileName = FILE_NAME,
        serializer = PlayerInfoSerializer,
    )

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<PlayerInfo> {
        return context.playerInfoDataStore
    }

    override fun getLocation(context: Context, fileKey: String): File {
        return File(context.filesDir, "datastore/$FILE_NAME")
    }
}
