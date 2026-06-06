/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.extensions

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow

/**
 * Animate a Dp value with a default tween spec.
 */
@Composable
fun Dp.animateTo(
    targetValue: Dp,
    animationSpec: AnimationSpec<Dp> = tween(durationMillis = 300)
): Dp {
    val animatedValue by animateDpAsState(targetValue = targetValue, animationSpec = animationSpec)
    return animatedValue
}

/**
 * Debounce a flow's emissions.
 */
fun <T> Flow<T>.debounce(timeoutMillis: Long): Flow<T> = flow {
    var lastValue: T? = null
    var lastTime = 0L
    collectLatest { value ->
        val now = System.currentTimeMillis()
        if (now - lastTime >= timeoutMillis) {
            lastValue = value
            lastTime = now
            emit(value)
        }
    }
}

/**
 * Check if a LazyListState is scrolled to the top.
 */
fun LazyListState.isAtTop(): Boolean = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

/**
 * Check if a LazyGridState is scrolled to the top.
 */
fun LazyGridState.isAtTop(): Boolean = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

/**
 * Check if a LazyStaggeredGridState is scrolled to the top.
 */
fun LazyStaggeredGridState.isAtTop(): Boolean = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

/**
 * Scroll to top with a smooth animation, jumping closer first for perceived speed.
 */
suspend fun LazyListState.animateToTop() {
    if (firstVisibleItemIndex > 10) {
        scrollToItem(firstVisibleItemIndex - 10)
    }
    animateScrollToItem(0)
}

/**
 * Scroll to top with a smooth animation, jumping closer first for perceived speed.
 */
suspend fun LazyGridState.animateToTop() {
    if (firstVisibleItemIndex > 10) {
        scrollToItem(firstVisibleItemIndex - 10)
    }
    animateScrollToItem(0)
}

/**
 * Create a throttled click modifier that prevents rapid double-clicks.
 */
fun Modifier.throttleClick(
    throttleTime: Long = 500L,
    onClick: () -> Unit
): Modifier = composed {
    var lastClickTime by rememberSaveable { mutableStateOf(0L) }
    clickable {
        val now = System.currentTimeMillis()
        if (now - lastClickTime >= throttleTime) {
            lastClickTime = now
            onClick()
        }
    }
}

/**
 * Convert a Dp value to pixels based on the current density.
 */
fun Dp.toPx(density: Float): Float = this.value * density

/**
 * Convert a pixel value to Dp based on the current density.
 */
fun Float.toDp(density: Float): Dp = (this / density).dp

/**
 * Create a MutableStateFlow that automatically saves to a setter function.
 */
fun <T> autoSaveFlow(
    initialValue: T,
    onSave: suspend (T) -> Unit
): MutableStateFlow<T> {
    return object : MutableStateFlow<T> {
        private val _flow = MutableStateFlow(initialValue)

        override var value: T
            get() = _flow.value
            set(v) {
                _flow.value = v
            }

        override suspend fun emit(value: T) {
            _flow.emit(value)
            onSave(value)
        }

        override fun tryEmit(value: T): Boolean {
            val result = _flow.tryEmit(value)
            if (result) {
                // Note: can't call suspend function here, use launch in caller
            }
            return result
        }

        override val replayCache: List<T> get() = _flow.replayCache

        override fun compareAndSet(expect: T, update: T): Boolean = _flow.compareAndSet(expect, update)

        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<T>): Nothing = _flow.collect(collector)

        override fun resetReplayCache() = _flow.resetReplayCache()

        override val subscriptionCount: kotlinx.coroutines.flow.StateFlow<Int> get() = _flow.subscriptionCount
    }
}
