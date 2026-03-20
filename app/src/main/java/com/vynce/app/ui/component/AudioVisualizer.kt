package com.vynce.app.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF9B72F8),
    barCount: Int = 28
) {
    val bars = remember { List(barCount) { Animatable(0.08f) } }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            bars.forEachIndexed { i, bar ->
                launch {
                    bar.animateTo(
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween((300..700).random(), easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(i * 40)
                        )
                    )
                }
            }
        } else {
            bars.forEach { it.animateTo(0.08f, tween(300)) }
        }
    }
    val heights = bars.map { it.value }
    Canvas(modifier = modifier) {
        val barWidth = (size.width / barCount) * 0.6f
        val gap = (size.width / barCount) * 0.4f
        heights.forEachIndexed { i, h ->
            val bh = size.height * h.coerceIn(0.05f, 1f)
            drawRoundRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(i * (barWidth + gap), (size.height - bh) / 2f),
                size = Size(barWidth, bh),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}
