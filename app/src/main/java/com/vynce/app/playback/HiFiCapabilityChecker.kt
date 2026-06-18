/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Ported from PixelMusic's HiFiCapabilityChecker.
 * Checks whether the device's audio stack can accept PCM_FLOAT output.
 */

package com.vynce.app.playback

import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Checks whether the device's audio stack can actually accept PCM_FLOAT output.
 *
 * Two-stage test:
 *  1. [AudioTrack.getMinBufferSize] — fast, allocation-free. Returns ERROR_BAD_VALUE on
 *     devices that don't support float encoding at all.
 *  2. Attempt to instantiate an [AudioTrack] with ENCODING_PCM_FLOAT. Some devices pass
 *     stage 1 but throw or produce STATE_UNINITIALIZED at creation time (driver bug).
 *
 * Result is cached after the first call; device capabilities don't change at runtime.
 */
object HiFiCapabilityChecker {
    private const val TAG = "HiFiCapability"

    @Volatile private var cachedResult: Boolean? = null

    fun isSupported(): Boolean {
        cachedResult?.let { return it }
        return runCheck().also {
            cachedResult = it
            Log.i(TAG, "PCM_FLOAT AudioTrack supported: $it")
        }
    }

    private fun runCheck(): Boolean {
        // Stage 1: buffer-size probe (no allocation)
        val minBuf = AudioTrack.getMinBufferSize(
            44_100,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuf <= 0 || minBuf == AudioTrack.ERROR || minBuf == AudioTrack.ERROR_BAD_VALUE) {
            Log.w(TAG, "getMinBufferSize rejected PCM_FLOAT (result=$minBuf)")
            return false
        }

        // Stage 2: actual AudioTrack creation (definitive hardware check)
        return try {
            val track = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(44_100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val initialized = track.state == AudioTrack.STATE_INITIALIZED
            track.release()
            if (!initialized) Log.w(TAG, "AudioTrack created but state != INITIALIZED")
            initialized
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack PCM_FLOAT creation threw", e)
            false
        }
    }
}
