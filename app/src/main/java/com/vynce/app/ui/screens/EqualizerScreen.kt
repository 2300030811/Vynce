/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.R
import com.vynce.app.constants.EqualizerBandsKey
import com.vynce.app.constants.EqualizerBassBoostKey
import com.vynce.app.constants.EqualizerEnabledKey
import com.vynce.app.constants.EqualizerPreampKey
import com.vynce.app.constants.EqualizerPresetKey
import com.vynce.app.constants.EqualizerVirtualizerKey
import com.vynce.app.utils.rememberPreference
import kotlin.math.roundToInt

data class StudioBand(
    val freq: String,
    val role: String,
    val centerHz: Int
)

val STUDIO_BANDS = listOf(
    StudioBand("31Hz", "Sub-Bass", 31),
    StudioBand("62Hz", "Punch", 62),
    StudioBand("125Hz", "Upper Bass", 125),
    StudioBand("250Hz", "Warmth", 250),
    StudioBand("500Hz", "Body", 500),
    StudioBand("1kHz", "Vocal", 1000),
    StudioBand("2kHz", "Clarity", 2000),
    StudioBand("4kHz", "Presence", 4000),
    StudioBand("8kHz", "Treble", 8000),
    StudioBand("16kHz", "Air", 16000),
)

val STUDIO_PRESETS = mapOf(
    "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Bass Boost" to listOf(7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vocal" to listOf(-2f, -1f, 0f, 1f, 3f, 5f, 4f, 3f, 2f, 1f),
    "Rock" to listOf(5f, 3f, -1f, -2f, 0f, 2f, 4f, 5f, 4f, 3f),
    "Pop" to listOf(-1f, 2f, 4f, 5f, 3f, 0f, -1f, -2f, 2f, 3f),
    "Jazz" to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 2f, 3f, 3f),
    "Electronic" to listOf(6f, 5f, 2f, 0f, -2f, 2f, 1f, 3f, 5f, 6f),
    "Hip-Hop" to listOf(6f, 5f, 3f, 1f, -1f, 0f, 1f, 2f, 3f, 4f),
    "Classical" to listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
    "Acoustic" to listOf(3f, 2f, 1f, 1f, 2f, 2f, 3f, 3f, 4f, 3f),
    "Dance" to listOf(6f, 4f, 2f, 0f, 0f, 2f, 4f, 4f, 3f, 0f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val (isEqEnabled, onIsEqEnabledChange) = rememberPreference(EqualizerEnabledKey, defaultValue = true)
    val (savedPreset, onSavedPresetChange) = rememberPreference(EqualizerPresetKey, defaultValue = "Flat")
    val (savedBandsStr, onSavedBandsStrChange) = rememberPreference(EqualizerBandsKey, defaultValue = "0,0,0,0,0,0,0,0,0,0")
    val (bassBoost, onBassBoostChange) = rememberPreference(EqualizerBassBoostKey, defaultValue = 0)
    val (virtualizer, onVirtualizerChange) = rememberPreference(EqualizerVirtualizerKey, defaultValue = 0)
    val (preampGain, onPreampGainChange) = rememberPreference(EqualizerPreampKey, defaultValue = 0)

    val bandGains = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) }
    var currentPreset by remember { mutableStateOf(savedPreset) }

    // Sync saved band levels into state
    LaunchedEffect(savedBandsStr) {
        val parsed = savedBandsStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (parsed.size == 10) {
            parsed.forEachIndexed { idx, v -> bandGains[idx] = v }
        }
    }

    fun updateBandsAndSave(newGains: List<Float>, presetName: String) {
        currentPreset = presetName
        onSavedPresetChange(presetName)
        newGains.forEachIndexed { i, g -> bandGains[i] = g }
        onSavedBandsStrChange(newGains.joinToString(","))
    }

    fun updateSingleBand(index: Int, gain: Float) {
        bandGains[index] = gain
        currentPreset = "Custom"
        onSavedPresetChange("Custom")
        onSavedBandsStrChange(bandGains.joinToString(","))
    }

    fun resetAll() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        updateBandsAndSave(STUDIO_PRESETS["Flat"] ?: List(10) { 0f }, "Flat")
        onBassBoostChange(0)
        onVirtualizerChange(0)
        onPreampGainChange(0)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val playerPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.equalizer),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (isEqEnabled) "DSP Active • $currentPreset" else "Bypassed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isEqEnabled) primaryColor else onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { resetAll() }) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset Equalizer",
                            tint = onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEqEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            onIsEqEnabledChange(it)
                        },
                        modifier = Modifier.padding(end = 12.dp),
                        thumbContent = {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = playerPadding.calculateBottomPadding() + 24.dp)
        ) {
            // ── 1. Pro Studio Frequency Response Curve (Canvas) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(surfaceContainer.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f

                    // 1. Reference dB Grid Lines & Left Labels
                    val dbLevels = listOf(12, 6, 0, -6, -12)
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(120, 255, 255, 255)
                        textSize = 9.dp.toPx()
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                    }

                    dbLevels.forEach { db ->
                        val y = centerY - (db / 12f) * (height * 0.38f)
                        drawLine(
                            color = if (db == 0) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                            start = Offset(28.dp.toPx(), y),
                            end = Offset(width - 8.dp.toPx(), y),
                            strokeWidth = if (db == 0) 1.5f else 1f
                        )
                        val text = if (db > 0) "+$db" else "$db"
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            4.dp.toPx(),
                            y + 3.dp.toPx(),
                            labelPaint
                        )
                    }

                    // 2. Smooth Catmull-Rom Spline Curve
                    if (bandGains.isNotEmpty()) {
                        val paddingLeft = 36.dp.toPx()
                        val paddingRight = 14.dp.toPx()
                        val usableWidth = width - paddingLeft - paddingRight
                        val stepX = usableWidth / (bandGains.size - 1)

                        val points = bandGains.mapIndexed { idx, gain ->
                            val activeGain = if (isEqEnabled) gain else 0f
                            val x = paddingLeft + idx * stepX
                            val y = centerY - (activeGain / 12f) * (height * 0.38f)
                            Offset(x, y)
                        }

                        val curvePath = Path()
                        val fillPath = Path()

                        if (points.size >= 2) {
                            curvePath.moveTo(points[0].x, points[0].y)
                            fillPath.moveTo(points[0].x, height)
                            fillPath.lineTo(points[0].x, points[0].y)

                            for (i in 0 until points.size - 1) {
                                val p0 = if (i > 0) points[i - 1] else points[i]
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                val p3 = if (i < points.size - 2) points[i + 2] else p2

                                val cp1x = p1.x + (p2.x - p0.x) / 5.5f
                                val cp1y = (p1.y + (p2.y - p0.y) / 5.5f).coerceIn(8f, height - 8f)
                                val cp2x = p2.x - (p3.x - p1.x) / 5.5f
                                val cp2y = (p2.y - (p3.y - p1.y) / 5.5f).coerceIn(8f, height - 8f)

                                curvePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                                fillPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }

                            fillPath.lineTo(points.last().x, height)
                            fillPath.close()

                            // Gradient Fill under curve
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        if (isEqEnabled) primaryColor.copy(alpha = 0.38f) else Color.Gray.copy(alpha = 0.12f),
                                        if (isEqEnabled) primaryColor.copy(alpha = 0.08f) else Color.Transparent,
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = height
                                ),
                                style = Fill
                            )

                            // Spline curve stroke
                            drawPath(
                                path = curvePath,
                                color = if (isEqEnabled) primaryColor else Color.Gray.copy(alpha = 0.5f),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Frequency Node Markers
                            points.forEach { pt ->
                                drawCircle(
                                    color = if (isEqEnabled) Color.White else Color.LightGray,
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = if (isEqEnabled) primaryColor else Color.DarkGray,
                                    radius = 2.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── 2. Acoustic Profiles / Presets Row ──
            Text(
                text = "Acoustic Profiles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(STUDIO_PRESETS.keys.toList()) { preset ->
                    val isSelected = currentPreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            STUDIO_PRESETS[preset]?.let { levels ->
                                updateBandsAndSave(levels, preset)
                            }
                        },
                        label = {
                            Text(
                                text = preset,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 3. Master DSP Enhancers ──
            Text(
                text = "Master DSP Enhancers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sub-Bass Booster Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceContainer.copy(alpha = 0.5f),
                    border = borderStyle()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Waves,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = primaryColor
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sub-Bass", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (bassBoost > 0) "+$bassBoost%" else "Off",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bassBoost > 0) primaryColor else onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = bassBoost.toFloat(),
                            onValueChange = { onBassBoostChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            enabled = isEqEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }

                // Vocal Clarity & Virtualizer Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = surfaceContainer.copy(alpha = 0.5f),
                    border = borderStyle()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.SurroundSound,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = primaryColor
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Surround", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (virtualizer > 0) "+$virtualizer%" else "Off",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (virtualizer > 0) primaryColor else onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = virtualizer.toFloat(),
                            onValueChange = { onVirtualizerChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            enabled = isEqEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryColor,
                                activeTrackColor = primaryColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preamp Headroom Gain Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = surfaceContainer.copy(alpha = 0.5f),
                border = borderStyle()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = primaryColor
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Preamp Headroom Gain", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${if (preampGain > 0) "+" else ""}${preampGain} dB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (preampGain != 0) primaryColor else onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = preampGain.toFloat(),
                        onValueChange = { onPreampGainChange(it.roundToInt()) },
                        valueRange = -12f..12f,
                        enabled = isEqEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 4. 10-Band Studio Hardware Fader Console ──
            Text(
                text = "10-Band Studio Faders",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(surfaceContainer.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 16.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                STUDIO_BANDS.forEachIndexed { index, band ->
                    val gain = bandGains.getOrElse(index) { 0f }
                    VerticalStudioFader(
                        band = band,
                        gain = gain,
                        enabled = isEqEnabled,
                        primaryColor = primaryColor,
                        onGainChange = { newGain ->
                            updateSingleBand(index, newGain)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Hardware-grade Custom Vertical Studio Fader.
 * Solves Compose Slider rotation clipping / hitbox / thumb distortions.
 */
@Composable
private fun VerticalStudioFader(
    band: StudioBand,
    gain: Float,
    enabled: Boolean,
    primaryColor: Color,
    onGainChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val faderHeight = 150.dp
    val faderWidth = 34.dp
    val density = LocalDensity.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        // Gain Readout Badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (gain != 0f && enabled) primaryColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            val gainText = if (gain > 0) "+${gain.roundToInt()}" else "${gain.roundToInt()}"
            Text(
                text = gainText,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (gain != 0f && enabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Custom Canvas Vertical Fader Channel
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(faderHeight)
                .width(faderWidth)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val heightPx = with(density) { faderHeight.toPx() }
                    val paddingPx = with(density) { 14.dp.toPx() }
                    val usableHeight = heightPx - (paddingPx * 2)

                    fun handleTouch(y: Float) {
                        val clampedY = y.coerceIn(paddingPx, heightPx - paddingPx)
                        val fraction = 1f - ((clampedY - paddingPx) / usableHeight) // 0.0 at bottom, 1.0 at top
                        val calculatedGain = ((fraction * 24f) - 12f).roundToInt().toFloat().coerceIn(-12f, 12f)
                        if (calculatedGain != gain) {
                            if (calculatedGain == 0f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            onGainChange(calculatedGain)
                        }
                    }

                    detectTapGestures { offset ->
                        handleTouch(offset.y)
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val heightPx = with(density) { faderHeight.toPx() }
                    val paddingPx = with(density) { 14.dp.toPx() }
                    val usableHeight = heightPx - (paddingPx * 2)

                    detectDragGestures { change, _ ->
                        change.consume()
                        val clampedY = change.position.y.coerceIn(paddingPx, heightPx - paddingPx)
                        val fraction = 1f - ((clampedY - paddingPx) / usableHeight)
                        val calculatedGain = ((fraction * 24f) - 12f).roundToInt().toFloat().coerceIn(-12f, 12f)
                        if (calculatedGain != gain) {
                            if (calculatedGain == 0f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                            onGainChange(calculatedGain)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val padY = 14.dp.toPx()
                val usableH = h - (padY * 2)
                val trackWidth = 5.dp.toPx()
                val centerX = w / 2f
                val centerY = h / 2f

                // 1. Groove background track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(centerX - trackWidth / 2f, padY),
                    size = Size(trackWidth, usableH),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                // 2. Center 0dB notch marker
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(centerX - 10.dp.toPx(), centerY),
                    end = Offset(centerX + 10.dp.toPx(), centerY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // 3. Thumb Position Calculation
                val norm = ((gain + 12f) / 24f).coerceIn(0f, 1f)
                val thumbY = padY + (1f - norm) * usableH

                // Active level fill from center to thumb
                if (enabled && gain != 0f) {
                    val fillTop = minOf(centerY, thumbY)
                    val fillHeight = kotlin.math.abs(centerY - thumbY)
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                        size = Size(trackWidth, fillHeight),
                        cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                    )
                }

                // 4. Hardware Studio Knob / Thumb Pill
                val knobWidth = 28.dp.toPx()
                val knobHeight = 14.dp.toPx()
                val knobCorner = 6.dp.toPx()

                // Knob shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(centerX - knobWidth / 2f, thumbY - knobHeight / 2f + 2.dp.toPx()),
                    size = Size(knobWidth, knobHeight),
                    cornerRadius = CornerRadius(knobCorner, knobCorner)
                )

                // Knob body
                drawRoundRect(
                    color = if (enabled) {
                        if (gain != 0f) primaryColor else Color(0xFFE2DDF0)
                    } else Color.Gray,
                    topLeft = Offset(centerX - knobWidth / 2f, thumbY - knobHeight / 2f),
                    size = Size(knobWidth, knobHeight),
                    cornerRadius = CornerRadius(knobCorner, knobCorner)
                )

                // Knob center line indicator
                drawLine(
                    color = if (enabled && gain != 0f) Color.White else Color.Black.copy(alpha = 0.7f),
                    start = Offset(centerX - 6.dp.toPx(), thumbY),
                    end = Offset(centerX + 6.dp.toPx(), thumbY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Frequency Label
        Text(
            text = band.freq,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Band Role Subtitle
        Text(
            text = band.role,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun borderStyle() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    color = Color.White.copy(alpha = 0.08f)
)
