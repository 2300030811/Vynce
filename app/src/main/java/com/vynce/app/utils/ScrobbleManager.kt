/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Inspired by PixelMusic's ScrobbleManager.
 * Proper Last.fm scrobble lifecycle management with pause/resume awareness.
 */

package com.vynce.app.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages Last.fm scrobble lifecycle with proper pause/resume awareness.
 *
 * Implements the Last.fm specification:
 * - Song must be at least 30 seconds long
 * - Song must be listened to for at least 50% of duration OR 4 minutes (240s), whichever is less
 * - Timer pauses when playback pauses
 * - "Now Playing" is sent immediately on song start
 *
 * State machine:
 * ```
 * IDLE -> onSongStart() -> PLAYING
 * PLAYING -> onPause() -> PAUSED
 * PAUSED -> onResume() -> PLAYING
 * PLAYING/PAUSED -> onSongStart() (new song) -> scrobble old + PLAYING
 * PLAYING/PAUSED -> onSongStop() -> scrobble + IDLE
 * ```
 */
class ScrobbleManager(
    private val scope: CoroutineScope,
    private val scrobbler: LastFmScrobbler,
) {
    companion object {
        private const val TAG = "ScrobbleManager"

        /** Minimum song duration to be eligible for scrobbling (Last.fm spec) */
        private const val MIN_SONG_DURATION_MS = 30_000L

        /** Maximum listen time required before scrobbling (Last.fm spec: 4 minutes) */
        private const val MAX_SCROBBLE_THRESHOLD_MS = 240_000L
    }

    private var currentArtist: String = ""
    private var currentTrack: String = ""
    private var currentAlbum: String? = null
    private var currentDurationMs: Long = 0L
    private var startTimestamp: Long = 0L

    /** Accumulated play time in milliseconds (pauses don't count) */
    private var accumulatedPlayTimeMs: Long = 0L
    private var lastPlayStartMs: Long = 0L

    private var isPlaying: Boolean = false
    private var isScrobbled: Boolean = false
    private var nowPlayingSent: Boolean = false

    private var scrobbleCheckJob: Job? = null

    /** Whether scrobbling is enabled (set by preference) */
    var isEnabled: Boolean = false

    /**
     * Called when a new song starts playing.
     * This will:
     * 1. Scrobble the previous song if eligible
     * 2. Send "Now Playing" for the new song
     * 3. Start the scrobble timer
     */
    fun onSongStart(
        artist: String,
        track: String,
        album: String?,
        durationMs: Long,
    ) {
        if (!isEnabled) return

        // Scrobble previous track if eligible
        if (currentTrack.isNotBlank() && !isScrobbled) {
            finalizeAccumulation()
            maybeScrobble()
        }

        // Set up new track
        currentArtist = artist
        currentTrack = track
        currentAlbum = album
        currentDurationMs = durationMs
        startTimestamp = System.currentTimeMillis()
        accumulatedPlayTimeMs = 0L
        lastPlayStartMs = System.currentTimeMillis()
        isPlaying = true
        isScrobbled = false
        nowPlayingSent = false

        Log.d(TAG, "Song started: $artist - $track (duration=${durationMs}ms)")

        // Send "Now Playing"
        if (track.isNotBlank()) {
            nowPlayingSent = true
            scope.launch {
                scrobbler.updateNowPlaying(artist, track, album)
            }
        }

        // Start background check
        startScrobbleCheck()
    }

    /**
     * Called when playback pauses.
     * Freezes the accumulated play time counter.
     */
    fun onPause() {
        if (!isPlaying) return
        isPlaying = false
        finalizeAccumulation()
        Log.d(TAG, "Paused. Accumulated play time: ${accumulatedPlayTimeMs}ms")
    }

    /**
     * Called when playback resumes.
     * Resumes the accumulated play time counter.
     */
    fun onResume() {
        if (isPlaying || !isEnabled) return
        isPlaying = true
        lastPlayStartMs = System.currentTimeMillis()
        Log.d(TAG, "Resumed. Will continue accumulating from ${accumulatedPlayTimeMs}ms")
        startScrobbleCheck()
    }

    /**
     * Called when playback stops completely (e.g. queue end, user stop).
     * Scrobbles the current track if eligible.
     */
    fun onSongStop() {
        if (currentTrack.isBlank()) return
        finalizeAccumulation()
        maybeScrobble()
        reset()
    }

    /**
     * Called when play/pause state changes.
     * Convenience method that delegates to onPause/onResume.
     */
    fun onPlayerStateChanged(isNowPlaying: Boolean) {
        if (isNowPlaying) {
            onResume()
        } else {
            onPause()
        }
    }

    private fun finalizeAccumulation() {
        if (isPlaying && lastPlayStartMs > 0) {
            accumulatedPlayTimeMs += System.currentTimeMillis() - lastPlayStartMs
            lastPlayStartMs = System.currentTimeMillis()
        }
    }

    private fun maybeScrobble() {
        if (isScrobbled) return
        if (currentTrack.isBlank()) return

        // Last.fm spec: song must be at least 30 seconds
        if (currentDurationMs < MIN_SONG_DURATION_MS) {
            Log.d(TAG, "Not scrobbling: song too short (${currentDurationMs}ms < ${MIN_SONG_DURATION_MS}ms)")
            return
        }

        // Last.fm spec: listened to >= 50% or 4 minutes, whichever is less
        val threshold = minOf(currentDurationMs / 2, MAX_SCROBBLE_THRESHOLD_MS)
        if (accumulatedPlayTimeMs >= threshold) {
            isScrobbled = true
            Log.d(TAG, "Scrobbling: $currentArtist - $currentTrack (listened ${accumulatedPlayTimeMs}ms, threshold ${threshold}ms)")
            scope.launch {
                scrobbler.scrobble(
                    artist = currentArtist,
                    track = currentTrack,
                    album = currentAlbum,
                    timestamp = startTimestamp,
                )
            }
        } else {
            Log.d(TAG, "Not scrobbling yet: ${accumulatedPlayTimeMs}ms < ${threshold}ms threshold")
        }
    }

    private fun startScrobbleCheck() {
        scrobbleCheckJob?.cancel()
        if (isScrobbled || currentDurationMs < MIN_SONG_DURATION_MS) return

        val threshold = minOf(currentDurationMs / 2, MAX_SCROBBLE_THRESHOLD_MS)
        val remainingMs = threshold - accumulatedPlayTimeMs

        if (remainingMs <= 0) {
            maybeScrobble()
            return
        }

        scrobbleCheckJob = scope.launch {
            delay(remainingMs + 500) // small buffer for accuracy
            finalizeAccumulation()
            maybeScrobble()
        }
    }

    private fun reset() {
        scrobbleCheckJob?.cancel()
        currentArtist = ""
        currentTrack = ""
        currentAlbum = null
        currentDurationMs = 0L
        accumulatedPlayTimeMs = 0L
        lastPlayStartMs = 0L
        isPlaying = false
        isScrobbled = false
        nowPlayingSent = false
    }

    fun release() {
        reset()
    }
}
