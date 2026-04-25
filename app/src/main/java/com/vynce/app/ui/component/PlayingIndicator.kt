package com.vynce.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vynce.app.R
import com.vynce.app.constants.ThumbnailCornerRadius

/**
 * Efficient playing indicator using a single InfiniteTransition shared across all bars,
 * with staggered phase offsets. Replaces the previous per-bar Animatable + coroutine loop
 * (which created 3 coroutines firing every 50ms per visible list item).
 */
@Composable
fun PlayingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 3,
    barWidth: Dp = 4.dp,
    cornerRadius: Dp = ThumbnailCornerRadius,
    isPlaying: Boolean = true
) {
    // Use a single InfiniteTransition for all bars — much cheaper than N separate Animatables.
    val transition = rememberInfiniteTransition(label = "playing_indicator")

    // Bar heights as animated floats with staggered delays for a natural wave look.
    val barHeights = List(bars) { i ->
        val durationMs = 600 + i * 150 // stagger: 600ms, 750ms, 900ms
        val startVal = 0.15f + i * 0.1f
        if (isPlaying) {
            val height by transition.animateFloat(
                initialValue = startVal,
                targetValue = 0.9f - i * 0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            height
        } else {
            0.15f
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(barWidth * 1.5f),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        barHeights.forEach { heightFraction ->
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(barWidth)
            ) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x = 0f, y = size.height * (1f - heightFraction)),
                    size = size.copy(height = heightFraction * size.height),
                    cornerRadius = CornerRadius(cornerRadius.toPx())
                )
            }
        }
    }
}

@Composable
fun PlayingIndicatorBox(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    playWhenReady: Boolean,
    color: Color = Color.White,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
        ) {
            if (playWhenReady) {
                PlayingIndicator(
                    color = color,
                    modifier = Modifier.height(24.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = color
                )
            }
        }
    }
}
