/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Compact 4×1 bar widget showing current track + controls.
 * Now reads live playback state from PlayerInfoStateDefinition.
 */

package com.vynce.app.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.action.actionSendBroadcast
import com.vynce.app.MainActivity
import com.vynce.app.R

/**
 * Compact 4×1 bar widget: [Art] [Title / Artist] [⏮ ▶ ⏭]
 * Reads playback state from [PlayerInfoStateDefinition].
 */
class VynceBarWidget : GlanceAppWidget() {

    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val playerInfo = currentState<PlayerInfo>()
                BarWidgetContent(playerInfo)
            }
        }
    }

    @Composable
    private fun BarWidgetContent(info: PlayerInfo) {
        val hasTrack = info.songTitle.isNotBlank()

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(day = Color(0xFFF3F3FA), night = Color(0xFF1B1B1F)))
                .cornerRadius(16.dp)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Album art placeholder
            Image(
                provider = ImageProvider(R.drawable.music_note),
                contentDescription = "Album Art",
                modifier = GlanceModifier.size(48.dp).cornerRadius(8.dp),
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Song info
            Column(
                modifier = GlanceModifier.defaultWeight(),
            ) {
                Text(
                    text = if (hasTrack) info.songTitle else "Vynce",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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
                        fontSize = 12.sp,
                        color = ColorProvider(
                            day = Color(0xFF45464F),
                            night = Color(0xFFC4C6D0)
                        ),
                    ),
                    maxLines = 1,
                )
            }

            // Playback controls — send media button intents
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.skip_previous),
                    contentDescription = "Previous",
                    modifier = GlanceModifier.size(32.dp).padding(4.dp)
                        .clickable(
                            actionSendBroadcast(
                                Intent("android.intent.action.MEDIA_BUTTON")
                                    .setPackage("com.vynce.app")
                            )
                        ),
                )
                Image(
                    provider = ImageProvider(
                        if (info.isPlaying) R.drawable.pause else R.drawable.play
                    ),
                    contentDescription = "Play/Pause",
                    modifier = GlanceModifier.size(36.dp).padding(4.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                )
                Image(
                    provider = ImageProvider(R.drawable.skip_next),
                    contentDescription = "Next",
                    modifier = GlanceModifier.size(32.dp).padding(4.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                )
            }
        }
    }
}

/**
 * BroadcastReceiver that hosts the VynceBarWidget.
 */
class VynceBarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VynceBarWidget()
}
