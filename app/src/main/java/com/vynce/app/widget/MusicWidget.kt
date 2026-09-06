/*
 * Copyright (C) 2026 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Native, responsive music widget with real-time controls,
 * album artwork, and progress bar adapted from AirBeats.
 */

package com.vynce.app.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.media3.common.Player
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.vynce.app.MainActivity
import com.vynce.app.R
import com.vynce.app.playback.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MusicWidget : AppWidgetProvider() {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var isUpdating = false

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        startProgressUpdater(context)
    }

    override fun onEnabled(context: Context) {
        startProgressUpdater(context)
    }

    override fun onDisabled(context: Context) {
        stopProgressUpdater()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val service = MusicService.instance
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (service != null) {
                    service.togglePlayPause()
                } else {
                    openApp(context)
                }
                updateAllWidgets(context)
            }

            ACTION_PREV -> {
                service?.player?.seekToPreviousMediaItem()
                updateAllWidgets(context)
            }

            ACTION_NEXT -> {
                service?.player?.seekToNext()
                updateAllWidgets(context)
            }

            ACTION_SHUFFLE -> {
                service?.toggleShuffle()
                updateAllWidgets(context)
            }

            ACTION_LIKE -> {
                service?.toggleLike()
                updateAllWidgets(context)
            }

            ACTION_OPEN_APP -> {
                openApp(context)
            }

            ACTION_STATE_CHANGED, ACTION_UPDATE_PROGRESS -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun openApp(context: Context) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            launchIntent?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressUpdater(context: Context) {
        if (isUpdating) return
        isUpdating = true

        runnable = Runnable {
            val player = MusicService.instance?.player

            if (player != null && (player.isPlaying || player.playbackState == Player.STATE_READY)) {
                updateAllWidgets(context)
                runnable?.let { handler.postDelayed(it, 1000) }
            } else {
                updateAllWidgets(context)
                runnable?.let { handler.postDelayed(it, 5000) }
            }
        }

        runnable?.let { handler.post(it) }
    }

    private fun stopProgressUpdater() {
        isUpdating = false
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.vynce.app.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.vynce.app.ACTION_PREV"
        const val ACTION_NEXT = "com.vynce.app.ACTION_NEXT"
        const val ACTION_SHUFFLE = "com.vynce.app.ACTION_SHUFFLE"
        const val ACTION_LIKE = "com.vynce.app.ACTION_LIKE"
        const val ACTION_OPEN_APP = "com.vynce.app.ACTION_OPEN_APP"
        const val ACTION_STATE_CHANGED = "com.vynce.app.ACTION_STATE_CHANGED"
        const val ACTION_UPDATE_PROGRESS = "com.vynce.app.ACTION_UPDATE_PROGRESS"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, MusicWidget::class.java)
                )
                if (widgetIds.isNotEmpty()) {
                    widgetIds.forEach { updateWidget(context, appWidgetManager, it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_music)
            val service = MusicService.instance
            val player = service?.player

            setPendingIntents(context, views)

            if (player != null && player.mediaItemCount > 0) {
                val vynceMetadata = service.currentMediaMetadata.value
                val media3Metadata = player.mediaMetadata

                val songTitle = vynceMetadata?.title
                    ?: media3Metadata.title?.toString()
                    ?: context.getString(R.string.song_title)

                val artist = vynceMetadata?.artists?.joinToString(", ") { it.name }
                    ?: media3Metadata.artist?.toString()
                    ?: context.getString(R.string.artist_name)

                views.setTextViewText(R.id.widget_song_title, songTitle)
                views.setTextViewText(R.id.widget_artist, artist)

                val playPauseIcon = if (player.isPlaying) R.drawable.pause else R.drawable.play
                views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

                val shuffleIcon =
                    if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle
                views.setImageViewResource(R.id.widget_shuffle, shuffleIcon)

                val isLiked = service.isCurrentSongLiked()
                val likeIcon = if (isLiked) R.drawable.favorite else R.drawable.favorite_border
                views.setImageViewResource(R.id.widget_like, likeIcon)

                val currentPos = player.currentPosition
                val duration = player.duration

                val currentTimeText = formatTime(currentPos)
                val durationText = formatTime(duration)

                views.setTextViewText(R.id.widget_current_time, currentTimeText)
                views.setTextViewText(R.id.widget_duration, durationText)

                val progress = if (duration > 0 && duration != Long.MAX_VALUE) {
                    (currentPos * 100 / duration).toInt()
                } else 0

                if (duration > 0 && duration != Long.MAX_VALUE) {
                    views.setProgressBar(R.id.widget_progress_bar, 100, progress, false)
                    views.setViewVisibility(R.id.widget_progress_bar, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_current_time, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_duration, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_progress_bar, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_current_time, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_duration, android.view.View.GONE)
                }

                val thumbnailUrl = vynceMetadata?.thumbnailUrl
                    ?: media3Metadata.artworkUri?.toString()
                if (!thumbnailUrl.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(thumbnailUrl)
                                .size(160, 160)
                                .build()
                            val result = context.imageLoader.execute(request)
                            val bitmap = result.image?.toBitmap()
                            if (bitmap != null) {
                                views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                            }
                        } catch (e: Exception) {
                            views.setImageViewResource(R.id.widget_album_art, R.drawable.music_note)
                            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                        }
                    }
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.music_note)
                }
            } else {
                views.setTextViewText(R.id.widget_song_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_artist, context.getString(R.string.tap_to_open))
                views.setImageViewResource(R.id.widget_album_art, R.drawable.music_note)
                views.setImageViewResource(R.id.widget_play_pause, R.drawable.play)
                views.setImageViewResource(R.id.widget_shuffle, R.drawable.shuffle)
                views.setImageViewResource(R.id.widget_like, R.drawable.favorite_border)
                views.setViewVisibility(R.id.widget_progress_bar, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_current_time, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_duration, android.view.View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setPendingIntents(context: Context, views: RemoteViews) {
            val playPausePendingIntent = getBroadcastPendingIntent(context, ACTION_PLAY_PAUSE)
            val prevPendingIntent = getBroadcastPendingIntent(context, ACTION_PREV)
            val nextPendingIntent = getBroadcastPendingIntent(context, ACTION_NEXT)
            val shufflePendingIntent = getBroadcastPendingIntent(context, ACTION_SHUFFLE)
            val likePendingIntent = getBroadcastPendingIntent(context, ACTION_LIKE)
            val openAppPendingIntent = getBroadcastPendingIntent(context, ACTION_OPEN_APP)

            views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_prev, prevPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_shuffle, shufflePendingIntent)
            views.setOnClickPendingIntent(R.id.widget_like, likePendingIntent)

            views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_song_title, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_artist, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_progress_bar, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
        }

        private fun getBroadcastPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicWidget::class.java).apply {
                this.action = action
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
        }

        @SuppressLint("DefaultLocale")
        private fun formatTime(millis: Long): String {
            return if (millis < 0 || millis == Long.MAX_VALUE) "0:00" else String.format(
                "%d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
            )
        }
    }
}
