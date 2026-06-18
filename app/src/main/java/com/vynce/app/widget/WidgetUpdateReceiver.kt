/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * BroadcastReceiver that triggers Glance widget updates when MusicService
 * broadcasts ACTION_WIDGET_UPDATE_PLAYBACK_STATE.
 */

package com.vynce.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for playback state change broadcasts from [MusicService] and triggers
 * all Glance widget updates. Registered in AndroidManifest.xml with action
 * `com.vynce.app.ACTION_WIDGET_UPDATE_PLAYBACK_STATE`.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_WIDGET_UPDATE_PLAYBACK_STATE) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                // Update all widget types — each reads PlayerInfo from shared state
                VynceBarWidget().updateAll(context)
                VynceControlWidget().updateAll(context)
            } catch (e: Exception) {
                Log.e("WidgetUpdateReceiver", "Failed to update widgets", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_UPDATE_PLAYBACK_STATE =
            "com.vynce.app.ACTION_WIDGET_UPDATE_PLAYBACK_STATE"
    }
}
