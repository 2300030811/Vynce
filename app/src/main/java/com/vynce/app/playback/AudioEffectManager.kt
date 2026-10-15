/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.vynce.app.constants.EqualizerBandsKey
import com.vynce.app.constants.EqualizerBassBoostKey
import com.vynce.app.constants.EqualizerEnabledKey
import com.vynce.app.constants.EqualizerPreampKey
import com.vynce.app.constants.EqualizerVirtualizerKey
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class AudioEffectManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "AudioEffectManager"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = 0

    // Standard 10 studio band center frequencies in Hz
    val studioFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    companion object {
        @Volatile
        private var INSTANCE: AudioEffectManager? = null

        fun getInstance(context: Context, scope: CoroutineScope): AudioEffectManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioEffectManager(context.applicationContext, scope).also { INSTANCE = it }
            }
        }

        fun getInstance(): AudioEffectManager? = INSTANCE
    }

    init {
        INSTANCE = this
        observePreferences()
    }

    private fun observePreferences() {
        scope.launch(Dispatchers.Default) {
            context.dataStore.data.collectLatest { prefs ->
                val enabled = prefs[EqualizerEnabledKey] ?: true
                val bandsStr = prefs[EqualizerBandsKey] ?: "0,0,0,0,0,0,0,0,0,0"
                val bass = prefs[EqualizerBassBoostKey] ?: 0
                val virt = prefs[EqualizerVirtualizerKey] ?: 0
                val preamp = prefs[EqualizerPreampKey] ?: 0

                val gains = bandsStr.split(",").mapNotNull { it.trim().toFloatOrNull() }

                applyEffects(
                    enabled = enabled,
                    gains = gains,
                    bassBoostStrength = bass,
                    virtualizerStrength = virt,
                    preampDb = preamp
                )
            }
        }
    }

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentAudioSessionId) return
        currentAudioSessionId = audioSessionId

        releaseEffects()

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Equalizer AudioEffect: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BassBoost AudioEffect: ${e.message}")
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Virtualizer AudioEffect: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize LoudnessEnhancer AudioEffect: ${e.message}")
            }
        }

        // Apply saved preference state immediately to new session
        val enabled = context.dataStore.get(EqualizerEnabledKey, true)
        val bandsStr = context.dataStore.get(EqualizerBandsKey, "0,0,0,0,0,0,0,0,0,0")
        val bass = context.dataStore.get(EqualizerBassBoostKey, 0)
        val virt = context.dataStore.get(EqualizerVirtualizerKey, 0)
        val preamp = context.dataStore.get(EqualizerPreampKey, 0)

        val gains = bandsStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
        applyEffects(enabled, gains, bass, virt, preamp)
    }

    private fun applyEffects(
        enabled: Boolean,
        gains: List<Float>,
        bassBoostStrength: Int,
        virtualizerStrength: Int,
        preampDb: Int
    ) {
        // 1. Equalizer bands mapping
        equalizer?.let { eq ->
            try {
                eq.enabled = enabled
                if (enabled) {
                    val numHwBands = eq.numberOfBands.toInt()
                    val bandRange = eq.bandLevelRange // [minLevel, maxLevel] in millibels
                    val minMb = if (bandRange.size >= 2) bandRange[0].toInt() else -1500
                    val maxMb = if (bandRange.size >= 2) bandRange[1].toInt() else 1500

                    for (hwBand in 0 until numHwBands) {
                        val hwCenterHz = eq.getCenterFreq(hwBand.toShort()) / 1000 // mHz to Hz

                        // Sample target gain from the closest 10-band studio frequency
                        val targetGainDb = sampleGainForFrequency(hwCenterHz, gains)
                        val targetGainMb = (targetGainDb * 100).roundToInt().coerceIn(minMb, maxMb)

                        eq.setBandLevel(hwBand.toShort(), targetGainMb.toShort())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error applying Equalizer settings: ${e.message}")
            }
        }

        // 2. Bass Boost
        bassBoost?.let { bb ->
            try {
                val shouldEnable = enabled && bassBoostStrength > 0
                bb.enabled = shouldEnable
                if (shouldEnable && bb.strengthSupported) {
                    val strength = (bassBoostStrength * 10).coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error applying BassBoost settings: ${e.message}")
            }
        }

        // 3. Virtualizer (Surround / Vocal clarity)
        virtualizer?.let { vz ->
            try {
                val shouldEnable = enabled && virtualizerStrength > 0
                vz.enabled = shouldEnable
                if (shouldEnable && vz.strengthSupported) {
                    val strength = (virtualizerStrength * 10).coerceIn(0, 1000).toShort()
                    vz.setStrength(strength)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error applying Virtualizer settings: ${e.message}")
            }
        }

        // 4. Preamp Gain (Loudness Enhancer)
        loudnessEnhancer?.let { le ->
            try {
                val shouldEnable = enabled && preampDb != 0
                le.enabled = shouldEnable
                if (shouldEnable) {
                    val targetGainMb = (preampDb * 100).coerceIn(-1200, 1200)
                    le.setTargetGain(targetGainMb)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error applying LoudnessEnhancer settings: ${e.message}")
            }
        }
    }

    private fun sampleGainForFrequency(targetHz: Int, gains: List<Float>): Float {
        if (gains.isEmpty()) return 0f
        if (gains.size == 1) return gains[0]

        // Find nearest studio band
        var closestIdx = 0
        var minDiff = abs(studioFrequencies[0] - targetHz)

        for (i in 1 until minOf(studioFrequencies.size, gains.size)) {
            val diff = abs(studioFrequencies[i] - targetHz)
            if (diff < minDiff) {
                minDiff = diff
                closestIdx = i
            }
        }
        return gains.getOrElse(closestIdx) { 0f }
    }

    fun release() {
        releaseEffects()
        INSTANCE = null
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Equalizer: ${e.message}")
        }
        equalizer = null

        try {
            bassBoost?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing BassBoost: ${e.message}")
        }
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Virtualizer: ${e.message}")
        }
        virtualizer = null

        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing LoudnessEnhancer: ${e.message}")
        }
        loudnessEnhancer = null
    }
}
