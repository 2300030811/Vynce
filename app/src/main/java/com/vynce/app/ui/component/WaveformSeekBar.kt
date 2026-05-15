/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A seekbar that displays waveform-like bars representing audio amplitude.
 * The bars are filled from left to right based on the progress.
 *
 * @param waveform Normalized amplitude values (0.0 to 1.0)
 * @param progress Current playback progress (0.0 to 1.0)
 * @param activeColor Color for the played portion
 * @param inactiveColor Color for the unplayed portion
 * @param barWidth Width of each waveform bar
 * @param barGap Gap between bars
 * @param onSeek Callback when user seeks to a new position
 * @param modifier Compose modifier
 */
@Composable
fun WaveformSeekBar(
    waveform: List<Float>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    barWidth: Dp = 3.dp,
    barGap: Dp = 1.dp,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragProgress by remember { mutableFloatStateOf(progress) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(waveform) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
            .pointerInput(waveform) {
                detectHorizontalDragGestures { change, _ ->
                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                }
                detectTapGestures(onPress = {
                    onSeek(dragProgress)
                })
            }
    ) {
        if (waveform.isEmpty()) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidthPx = barWidth.toPx()
        val barGapPx = barGap.toPx()
        val totalBarWidth = barWidthPx + barGapPx
        val barCount = (canvasWidth / totalBarWidth).toInt().coerceAtMost(waveform.size)
        val startX = (canvasWidth - barCount * totalBarWidth + barGapPx) / 2f

        val progressX = startX + dragProgress * barCount * totalBarWidth

        for (i in 0 until barCount) {
            val amplitude = waveform.getOrElse(i) { 0f }
            val barHeight = (amplitude * canvasHeight * 0.8f).coerceAtLeast(4f)
            val x = startX + i * totalBarWidth
            val y = (canvasHeight - barHeight) / 2f

            val color = if (x < progressX) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f)
            )
        }
    }
}

/**
 * A smooth waveform seekbar that draws a continuous path through amplitude points.
 *
 * @param waveform Normalized amplitude values (0.0 to 1.0)
 * @param progress Current playback progress (0.0 to 1.0)
 * @param activeColor Color for the played portion
 * @param inactiveColor Color for the unplayed portion
 * @param lineWidth Width of the waveform line
 * @param onSeek Callback when user seeks to a new position
 * @param modifier Compose modifier
 */
@Composable
fun SmoothWaveformSeekBar(
    waveform: List<Float>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    lineWidth: Dp = 2.dp,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragProgress by remember { mutableFloatStateOf(progress) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(waveform) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
            .pointerInput(waveform) {
                detectHorizontalDragGestures { change, _ ->
                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                }
                detectTapGestures(onPress = {
                    onSeek(dragProgress)
                })
            }
    ) {
        if (waveform.size < 2) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f
        val progressX = dragProgress * canvasWidth
        val lineWidthPx = lineWidth.toPx()

        val stepX = canvasWidth / (waveform.size - 1)

        // Build inactive path
        val inactivePath = Path().apply {
            moveTo(0f, centerY)
            for (i in waveform.indices) {
                val x = i * stepX
                val amplitude = waveform[i] * centerY * 0.8f
                val y = centerY - amplitude
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        // Build active path (up to progress)
        val activePath = Path().apply {
            moveTo(0f, centerY)
            val activeCount = (dragProgress * (waveform.size - 1)).toInt().coerceAtLeast(0)
            for (i in 0..activeCount) {
                val x = i * stepX
                val amplitude = waveform[i] * centerY * 0.8f
                val y = centerY - amplitude
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        // Draw inactive waveform
        drawPath(
            path = inactivePath,
            color = inactiveColor,
            style = Stroke(width = lineWidthPx)
        )

        // Draw active waveform
        drawPath(
            path = activePath,
            color = activeColor,
            style = Stroke(width = lineWidthPx)
        )

        // Draw center line (subtle)
        drawLine(
            color = inactiveColor.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(canvasWidth, centerY),
            strokeWidth = 1f
        )
    }
}

/**
 * Generate mock waveform data for testing.
 * In production, this would come from audio analysis.
 */
fun generateMockWaveform(size: Int = 200): List<Float> {
    return List(size) { i ->
        val base = 0.3f + 0.4f * kotlin.math.abs(kotlin.math.sin(i * 0.1)).toFloat()
        base + (kotlin.random.Random.nextFloat() * 0.3f)
    }.map { it.coerceIn(0.05f, 1f) }
}
