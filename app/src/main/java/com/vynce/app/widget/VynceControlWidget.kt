/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Full 4×2 control widget with album art, track info, and controls.
 * Now reads live playback state from PlayerInfoStateDefinition.
 */

package com.vynce.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.vynce.app.playback.MusicService
import com.vynce.app.MainActivity
import com.vynce.app.R

/**
 * Full 4×2 control widget with track info and full playback controls.
 * Reads playback state from [PlayerInfoStateDefinition].
 */
class VynceControlWidget : GlanceAppWidget() {

    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val playerInfo = currentState<PlayerInfo>()
                ControlWidgetContent(playerInfo)
            }
        }
    }

    @Composable
    private fun ControlWidgetContent(info: PlayerInfo) {
        val hasTrack = info.songTitle.isNotBlank()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    ColorProvider(
                        day = Color(0xFFF3F3FA),
                        night = Color(0xFF1B1B1F)
                    )
                )
                .cornerRadius(16.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Album art placeholder
                Image(
                    provider = ImageProvider(R.drawable.music_note),
                    contentDescription = "Album Art",
                    modifier = GlanceModifier.size(64.dp).cornerRadius(8.dp),
                )

                Spacer(modifier = GlanceModifier.width(16.dp))

                // Song info
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                ) {
                    Text(
                        text = if (hasTrack) info.songTitle else "Vynce",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ColorProvider(
                                day = Color(0xFF1B1B1F),
                                night = Color(0xFFE2E2E9)
                            ),
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = if (hasTrack) info.artistName else "Tap to open",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = ColorProvider(
                                day = Color(0xFF45464F),
                                night = Color(0xFFC4C6D0)
                            ),
                        ),
                        maxLines = 1,
                    )
                }
            }

            // Progress indicator (visual only)
            if (hasTrack && info.totalDurationMs > 0L) {
                Spacer(modifier = GlanceModifier.height(12.dp))
                val progress = (info.currentPositionMs.toFloat() / info.totalDurationMs)
                    .coerceIn(0f, 1f)
                // Simple time display
                Text(
                    text = "${formatTime(info.currentPositionMs)} / ${formatTime(info.totalDurationMs)}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(
                            day = Color(0xFF45464F),
                            night = Color(0xFFC4C6D0)
                        ),
                    ),
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Playback controls
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    provider = ImageProvider(R.drawable.skip_previous),
                    contentDescription = "Previous",
                    modifier = GlanceModifier.size(48.dp).padding(8.dp)
                        .clickable(actionRunCallback<ControlPreviousCallback>()),
                )
                Image(
                    provider = ImageProvider(
                        if (info.isPlaying) R.drawable.pause else R.drawable.play
                    ),
                    contentDescription = "Play/Pause",
                    modifier = GlanceModifier.size(56.dp).padding(8.dp)
                        .clickable(actionRunCallback<ControlPlayPauseCallback>()),
                )
                Image(
                    provider = ImageProvider(R.drawable.skip_next),
                    contentDescription = "Next",
                    modifier = GlanceModifier.size(48.dp).padding(8.dp)
                        .clickable(actionRunCallback<ControlNextCallback>()),
                )
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

class ControlPlayPauseCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
                controller.release()
            } catch (e: Exception) {
                android.util.Log.e("ControlPlayPauseCallback", "Failed to toggle play/pause", e)
            }
        }, MoreExecutors.directExecutor())
    }
}

class ControlNextCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                controller.seekToNext()
                controller.release()
            } catch (e: Exception) {
                android.util.Log.e("ControlNextCallback", "Failed to skip next", e)
            }
        }, MoreExecutors.directExecutor())
    }
}

class ControlPreviousCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                controller.seekToPreviousMediaItem()
                controller.release()
            } catch (e: Exception) {
                android.util.Log.e("ControlPreviousCallback", "Failed to skip previous", e)
            }
        }, MoreExecutors.directExecutor())
    }
}

/**
 * BroadcastReceiver that hosts the VynceControlWidget.
 */
class VynceControlWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VynceControlWidget()
}
