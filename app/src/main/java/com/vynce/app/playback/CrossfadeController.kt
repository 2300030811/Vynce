/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Inspired by PixelMusic's TransitionController.
 * Simplified single-player volume-ramp crossfade approach.
 */

package com.vynce.app.playback

import android.util.Log
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Volume-ramp crossfade controller for smooth track transitions.
 *
 * Monitors the player's position and begins fading out the volume
 * when the track approaches its end. ExoPlayer's gapless playback
 * handles the actual transition, while we provide the auditory smoothness.
 *
 * Architecture notes (vs. PixelMusic's DualPlayerEngine):
 * - Uses a single ExoPlayer instance (no dual-player complexity)
 * - Relies on ExoPlayer's built-in gapless/crossfade capabilities
 * - Volume ramp provides the perceptual crossfade effect
 * - Much simpler and more stable than dual-player orchestration
 */
class CrossfadeController(
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "CrossfadeController"

        /** Minimum crossfade duration in milliseconds */
        const val MIN_CROSSFADE_MS = 0L

        /** Maximum crossfade duration in milliseconds */
        const val MAX_CROSSFADE_MS = 15_000L

        /** Default crossfade duration in milliseconds */
        const val DEFAULT_CROSSFADE_MS = 0L

        /** Polling interval during normal playback (1 second) */
        private const val POLL_NORMAL_MS = 1000L

        /** Polling interval when approaching transition (250ms for smooth ramp) */
        private const val POLL_TRANSITION_MS = 100L

        /** Minimum song duration to apply crossfade (don't crossfade very short tracks) */
        private const val MIN_SONG_DURATION_FOR_CROSSFADE_MS = 30_000L

        /** Guard window: don't start crossfade in the last 500ms (avoid glitches at track boundary) */
        private const val GUARD_WINDOW_MS = 500L
    }

    private var monitorJob: Job? = null
    private var player: Player? = null
    private var originalVolume: Float = 1f

    /** Current crossfade duration setting in milliseconds */
    var crossfadeDurationMs: Long = DEFAULT_CROSSFADE_MS
        set(value) {
            field = value.coerceIn(MIN_CROSSFADE_MS, MAX_CROSSFADE_MS)
        }

    /** Whether crossfade is currently active (fading) */
    private val _isFading = MutableStateFlow(false)
    val isFading: StateFlow<Boolean> = _isFading

    /** Whether the controller is enabled */
    val isEnabled: Boolean
        get() = crossfadeDurationMs > 0

    /**
     * Attach the controller to a player and start monitoring.
     */
    fun attach(player: Player) {
        this.player = player
        startMonitoring()
        Log.d(TAG, "Attached to player, crossfade=${crossfadeDurationMs}ms")
    }

    /**
     * Detach the controller and stop monitoring.
     */
    fun detach() {
        stopMonitoring()
        resetVolume()
        player = null
        Log.d(TAG, "Detached from player")
    }

    /**
     * Call when the player transitions to a new media item.
     * Resets the fade state so the new track starts at full volume.
     */
    fun onMediaItemTransition() {
        if (_isFading.value) {
            resetVolume()
            _isFading.value = false
            Log.d(TAG, "Media item transitioned, volume reset")
        }
    }

    /**
     * Call when play/pause state changes.
     * Pauses or resumes the monitor accordingly.
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (isPlaying && isEnabled) {
            startMonitoring()
        } else if (!isPlaying) {
            // Don't stop monitoring entirely on pause,
            // but the polling loop will naturally skip when not playing
        }
    }

    /**
     * Update the base volume (from the player's volume setting).
     * This is the volume we fade FROM.
     */
    fun setBaseVolume(volume: Float) {
        originalVolume = volume
    }

    /**
     * Smoothly fade out volume and perform action (e.g. pause).
     * ponytail: Lightweight volume ramp without extra audio nodes.
     */
    fun fadePause(durationMs: Long = 250L, onComplete: () -> Unit) {
        val p = player ?: run { onComplete(); return }
        scope.launch(Dispatchers.Main) {
            val steps = 10
            val stepDelay = durationMs / steps
            val startVol = p.volume
            for (i in steps downTo 0) {
                p.volume = startVol * (i.toFloat() / steps)
                delay(stepDelay)
            }
            onComplete()
            p.volume = originalVolume
        }
    }

    /**
     * Smoothly fade in volume when starting playback.
     */
    fun fadePlay(durationMs: Long = 250L, onStart: () -> Unit) {
        val p = player ?: run { onStart(); return }
        scope.launch(Dispatchers.Main) {
            p.volume = 0f
            onStart()
            val steps = 10
            val stepDelay = durationMs / steps
            for (i in 0..steps) {
                p.volume = originalVolume * (i.toFloat() / steps)
                delay(stepDelay)
            }
        }
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        if (!isEnabled) return

        monitorJob = scope.launch(Dispatchers.Main) {
            Log.d(TAG, "Monitor started (crossfade=${crossfadeDurationMs}ms)")
            while (isActive) {
                val p = player ?: break

                if (!p.isPlaying || p.duration <= 0) {
                    delay(POLL_NORMAL_MS)
                    continue
                }

                val remaining = p.duration - p.currentPosition
                val fadeStart = crossfadeDurationMs + GUARD_WINDOW_MS

                // Don't crossfade very short tracks
                if (p.duration < MIN_SONG_DURATION_FOR_CROSSFADE_MS) {
                    delay(POLL_NORMAL_MS)
                    continue
                }

                // Check if we're in the next item's range (shouldn't crossfade)
                if (p.currentMediaItemIndex >= p.mediaItemCount - 1 &&
                    p.repeatMode == Player.REPEAT_MODE_OFF
                ) {
                    // Last track in queue with no repeat — don't crossfade, let it end naturally
                    delay(POLL_NORMAL_MS)
                    continue
                }

                if (remaining <= fadeStart && remaining > GUARD_WINDOW_MS) {
                    // We're in the crossfade zone
                    if (!_isFading.value) {
                        _isFading.value = true
                        Log.d(TAG, "Crossfade started, remaining=${remaining}ms")
                    }

                    // Calculate fade progress (0.0 = start of fade, 1.0 = end of fade)
                    val fadeProgress = 1f - ((remaining - GUARD_WINDOW_MS).toFloat() /
                            (crossfadeDurationMs).toFloat()).coerceIn(0f, 1f)

                    // Apply exponential curve for more natural-sounding fade
                    // Humans perceive volume logarithmically
                    val volumeMultiplier = (1f - fadeProgress * fadeProgress).coerceIn(0f, 1f)
                    p.volume = originalVolume * volumeMultiplier

                    delay(POLL_TRANSITION_MS)
                } else {
                    // Not yet in crossfade zone
                    if (_isFading.value) {
                        resetVolume()
                        _isFading.value = false
                    }

                    // Adaptive polling: poll faster when getting closer to the crossfade zone
                    val pollInterval = if (remaining < fadeStart * 2) {
                        POLL_TRANSITION_MS * 2 // 200ms when approaching
                    } else {
                        POLL_NORMAL_MS // 1s normally
                    }
                    delay(pollInterval)
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun resetVolume() {
        player?.volume = originalVolume
    }

    /**
     * Release all resources.
     */
    fun release() {
        detach()
    }
}
