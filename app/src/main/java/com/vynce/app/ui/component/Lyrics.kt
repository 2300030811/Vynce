/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.ui.component

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import com.vynce.app.LocalMenuState
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.R
import com.vynce.app.constants.LyricClickable
import com.vynce.app.constants.LyricFontSizeKey
import com.vynce.app.constants.LyricKaraokeEnable
import com.vynce.app.constants.LyricOffsetKey
import com.vynce.app.constants.LyricUpdateSpeed
import com.vynce.app.constants.LyricsPosition
import com.vynce.app.constants.LyricsTextPositionKey
import com.vynce.app.constants.ShowLyricsKey
import com.vynce.app.constants.Speed
import com.vynce.app.db.entities.LyricsEntity
import com.vynce.app.db.entities.LyricsEntity.Companion.uninitializedLyric
import com.vynce.app.extensions.isPowerSaver
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.component.shimmer.ShimmerHost
import com.vynce.app.ui.component.shimmer.TextPlaceholder
import com.vynce.app.ui.dialog.CounterDialog
import com.vynce.app.ui.menu.LyricsMenu
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.vynce.app.ui.utils.fadingEdge
import com.vynce.app.utils.rememberEnumPreference
import com.vynce.app.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.LyricLine
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    var (showLyrics, onShowLyricsChange) = rememberPreference(ShowLyricsKey, false)
    val landscapeOffset = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val lyricsFontSize by rememberPreference(LyricFontSizeKey, 20)

    val lyricsClickable by rememberPreference(LyricClickable, true)
    val lyricsFancy by rememberPreference(LyricKaraokeEnable, false)
    val lyricsUpdateSpeed by rememberEnumPreference(LyricUpdateSpeed, Speed.MEDIUM)
    val (lyricOffset, onLyricOffsetChange) = rememberPreference(LyricOffsetKey, 0L)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    // NOTE: lyricsModel is the current display lyrics that is updated by playerLyrics AND/OR manually
    val playerLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    var lyricsModel by remember { mutableStateOf<SemanticLyrics?>(playerLyrics) }

    val lines: SnapshotStateList<LyricLine> = remember { mutableStateListOf<LyricLine>() }

    val isSynced = remember(lyricsModel) {
        lyricsModel is SemanticLyrics.SyncedLyrics
    }

    val dbLyrics by remember(mediaMetadata) {
        mediaMetadata?.id?.let { id ->
            playerConnection.service.database.lyrics(id)
        } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    val currentDbLyrics = remember(dbLyrics, mediaMetadata) {
        var lyrics = dbLyrics
        val metadata = mediaMetadata
        val localPath = metadata?.localPath
        if (lyrics == null && localPath != null) {
            LrcUtils.loadLyricsFile(File(localPath))?.let {
                lyrics = LyricsEntity(metadata.id, it)
            }
        }
        lyrics
    }

    LaunchedEffect(playerLyrics) {
        lyricsModel = playerLyrics
    }

    LaunchedEffect(lyricsModel) {
        lines.clear()
        lyricsModel?.let { model ->
            if (model is SemanticLyrics.SyncedLyrics) {
                lines.addAll(model.text)
            } else {
                lines.add(
                    LyricLine(
                        model.unsyncedText.joinToString(separator = "\n") { it.first }, 0L.toULong(), 0L.toULong(),
                        null, null, false
                    )
                )
            }
        }
    }

    val textColor = MaterialTheme.colorScheme.secondary
    val prevTextColor = MaterialTheme.colorScheme.primary

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }
    // Because LaunchedEffect has delay, which leads to inconsistent with current line color and scroll animation,
    // we use deferredCurrentLineIndex when user is scrolling
    var deferredCurrentLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }
    var currentPos by remember { mutableLongStateOf(0L) }
    var showLyricOffsetDialog by remember {
        mutableStateOf(false)
    }

    if (showLyricOffsetDialog) {
        CounterDialog(
            title = stringResource(R.string.lyrics_offset),
            description = stringResource(R.string.lyrics_offset_desc),
            initialValue = lyricOffset.toInt(),
            upperBound = 10000,
            lowerBound = -10000,
            unitDisplay = "ms",
            onDismiss = { showLyricOffsetDialog = false },
            onConfirm = {
                onLyricOffsetChange(it.toLong())
                showLyricOffsetDialog = false
            },
            onReset = { onLyricOffsetChange(0L) },
            onCancel = { showLyricOffsetDialog = false },
        )
    }


    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lyricsModel, lyricsFancy, lyricsUpdateSpeed, lyricOffset) {
        val syncedLines = (lyricsModel as? SemanticLyrics.SyncedLyrics)?.text.orEmpty()
        if (syncedLines.isEmpty()) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        val lyricRefreshRate = if (lyricsFancy && syncedLines.fastAny { it.words != null }) {
            lyricsUpdateSpeed.toLrcRefreshMillis()
        } else {
            Speed.SLOW.toLrcRefreshMillis()
        }
        while (isActive) {
            delay(lyricRefreshRate)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED).not()) continue
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            if (!playerConnection.isPlaying.value && !isSeeking) continue
            val playerPosition = sliderPosition ?: playerConnection.player.currentPosition
            val lyricPosition = (playerPosition - lyricOffset).coerceAtLeast(0L)
            currentLineIndex = findCurrentLineIndex(syncedLines, lyricPosition)
            currentPos = lyricPosition
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            lastPreviewTime = 0L
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentLineIndex, lastPreviewTime) {
        /**
         * Count number of new lines in a lyric
         */
        fun countNewLine(str: String) = str.count { it == '\n' }

        /**
         * Calculate the lyric offset Based on how many lines (\n chars)
         */
        fun calculateOffset() = with(density) {
            if (landscapeOffset) {
                16.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text) // landscape sits higher by default
            } else {
                20.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text)
            }
        }

        if (!isSynced) return@LaunchedEffect
        if (currentLineIndex in lines.indices) {
            deferredCurrentLineIndex = currentLineIndex
            if (lastPreviewTime == 0L) {
                if (isSeeking) {
                    lazyListState.scrollToItem(
                        currentLineIndex,
                        with(density) { 36.dp.toPx().toInt() } + calculateOffset())
                } else {
                    lazyListState.animateScrollToItem(
                        currentLineIndex,
                        with(density) { 36.dp.toPx().toInt() } + calculateOffset())
                }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                .asPaddingValues(),
            modifier = Modifier
                .fadingEdge(vertical = 64.dp)
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
        ) {
            val displayedCurrentLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex

            if (lyricsModel == null) {
                item {
                    ShimmerHost {
                        repeat(10) {
                            Box(
                                contentAlignment = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Alignment.CenterStart
                                    LyricsPosition.CENTER -> Alignment.Center
                                    LyricsPosition.RIGHT -> Alignment.CenterEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                TextPlaceholder()
                            }
                        }
                    }
                }
            } else if (lyricsModel != uninitializedLyric) {
                val maxW = maxWidth - 48.dp
                itemsIndexed(
                    items = lines
                ) { index, item ->
                    var lyricFontSizeAdjusted = lyricsFontSize
                    if (item.speaker?.isBackground == true) {
                        lyricFontSizeAdjusted = (lyricFontSizeAdjusted * 0.75).toInt()
                    }
                    if (item.isTranslated) {
                        lyricFontSizeAdjusted = (lyricFontSizeAdjusted * 0.75).toInt()
                    }

                    Column(
                        horizontalAlignment = when (lyricsTextPosition) {
                            LyricsPosition.LEFT -> Alignment.Start
                            LyricsPosition.CENTER -> Alignment.CenterHorizontally
                            LyricsPosition.RIGHT -> Alignment.End
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                top = if (item.isTranslated) 0.dp else 8.dp,
                                end = 24.dp,
                                bottom = if (item.isTranslated) 16.dp else 8.dp,
                            )
                            // we allow clicking on blank lyrics, ignore item.isClickable
                            .clickable(enabled = isSynced && lyricsClickable) {
                                playerConnection.player.seekTo((item.start.toLong() + lyricOffset).coerceAtLeast(0))
                                currentLineIndex = index
                                currentPos = item.start.toLong().coerceAtLeast(0)
                                lastPreviewTime = 0L
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                    ) {
                        val words = item.words
                        if (currentPos.toULong() in item.start..item.end + 100.toULong() && lyricsFancy
                            && words != null && !context.isPowerSaver()
                        ) { // word by word
                            FlowRow(
                                horizontalArrangement = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Arrangement.Start
                                    LyricsPosition.CENTER -> Arrangement.Center
                                    LyricsPosition.RIGHT -> Arrangement.End
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                words.forEach { word ->
                                    val wordText = item.text.substring(word.charRange)
                                    AnimatedWord(
                                        word = wordText,
                                        wordStartTimeMs = word.timeRange.start.toLong(),
                                        wordEndTimeMs = word.timeRange.endInclusive.toLong(),
                                        currentTimeMs = currentPos,
                                        fontSize = lyricFontSizeAdjusted.sp,
                                        textColor = textColor,
                                        isCurrent = true
                                    )
                                    // Add a small spacer for word separation
                                    Spacer(modifier = Modifier.size(4.dp))
                                }
                            }
                        } else { // regular
                            val isConsumed = currentPos.toULong() > (item.end + 100.toULong())
                            val isHighlighted =
                                ((index == displayedCurrentLineIndex || (index == displayedCurrentLineIndex + 1 && item.isTranslated)))
                            Text(
                                text = item.text,
                                fontSize = lyricFontSizeAdjusted.sp,
                                color = if (isConsumed && !isHighlighted) prevTextColor else textColor,
                                textAlign = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> TextAlign.Left
                                    LyricsPosition.CENTER -> TextAlign.Center
                                    LyricsPosition.RIGHT -> TextAlign.Right
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.alpha(
                                    if (!isSynced || isHighlighted) {
                                        1f
                                    } else if (isConsumed) {
                                        0.6f
                                    } else {
                                        0.5f
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }

        if (lyricsModel == uninitializedLyric) {
            Text(
                text = stringResource(R.string.lyrics_not_found),
                fontSize = lyricsFontSize.sp,
                color = textColor,
                textAlign = when (lyricsTextPosition) {
                    LyricsPosition.LEFT -> TextAlign.Left
                    LyricsPosition.CENTER -> TextAlign.Center
                    LyricsPosition.RIGHT -> TextAlign.Right
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        mediaMetadata?.let { mediaMetadata ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp)
            ) {
                IconButton(
                    onClick = { onShowLyricsChange(false) }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = textColor
                    )
                }
                IconButton(
                    onClick = {
                        menuState.show {
                            LyricsMenu(
                                lyricsEntity = currentDbLyrics,
                                mediaMetadataProvider = { mediaMetadata },
                                onRefreshRequest = { lyricsModel = it },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = null,
                        tint = textColor
                    )
                }
            }
        }

        if (isSynced && lyricsModel != null && lyricsModel != uninitializedLyric && lines.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(4.dp)
                ) {
                    val adjustOffset: (Long) -> Unit = { delta ->
                        onLyricOffsetChange((lyricOffset + delta).coerceIn(-10000L, 10000L))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    IconButton(
                        onClick = { adjustOffset(-50) },
                        onLongClick = { adjustOffset(-500) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "${if (lyricOffset > 0) "+" else ""}${lyricOffset}ms",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { showLyricOffsetDialog = true },
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { adjustOffset(50) },
                        onLongClick = { adjustOffset(500) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedWord(
    word: String,
    wordStartTimeMs: Long,
    wordEndTimeMs: Long,
    currentTimeMs: Long,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: Color,
    isCurrent: Boolean,
) {
    if (word.isEmpty()) return

    // Non-current lines render simply with dimmed color
    if (!isCurrent) {
        Text(
            text = word,
            fontSize = fontSize,
            color = textColor.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold
        )
        return
    }

    // Calculate word duration
    val wordDuration = (wordEndTimeMs - wordStartTimeMs).coerceAtLeast(100L)

    // Apple Music style: Character-level wipe effect
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        word.forEachIndexed { charIndex, char ->
            // Calculate timing for each character
            val charStartTimeMs = wordStartTimeMs + (wordDuration * charIndex / word.length)
            val charEndTimeMs = if (charIndex < word.length - 1) {
                wordStartTimeMs + (wordDuration * (charIndex + 1) / word.length)
            } else {
                wordEndTimeMs
            }

            // Calculate character progress
            val isPastChar = currentTimeMs >= charEndTimeMs
            val isFutureChar = currentTimeMs <= charStartTimeMs

            val rawProgress = when {
                isPastChar -> 1f
                isFutureChar -> 0f
                else -> ((currentTimeMs - charStartTimeMs).toFloat() / (charEndTimeMs - charStartTimeMs).toFloat()).coerceIn(0f, 1f)
            }

            // Animate character progress
            val animatedCharProgress by animateFloatAsState(
                targetValue = rawProgress,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "charProgress"
            )

            // Interpolate color
            val charColor = if (animatedCharProgress >= 1f) {
                textColor
            } else if (animatedCharProgress <= 0f) {
                textColor.copy(alpha = 0.5f)
            } else {
                val t = animatedCharProgress
                val alpha = 0.5f + (1f - 0.5f) * t
                textColor.copy(alpha = alpha)
            }

            Text(
                text = char.toString(),
                fontSize = fontSize,
                color = charColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HorizontalReveal(
    progress: Float,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.5f,
    rtl: Boolean = false,
    content: @Composable () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = LinearEasing)
    )

    Box(modifier = modifier.padding(start = 1.dp)) {
        Box(modifier = Modifier.alpha(backgroundAlpha)) {
            content()
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    clip = true
                    shape = RectangleShape
                }
                .drawWithContent {
                    val clipWidth = size.width * animatedProgress
                    val left = if (!rtl) 0f else size.width - clipWidth
                    val right = if (!rtl) clipWidth else size.width
                    clipRect(left, 0f, right, size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
fun splitTextToLines(
    text: String,
    style: TextStyle,
    maxWidth: Dp
): List<String> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
        val tentativeLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        val tentativeWidth = with(density) {
            textMeasurer.measure(
                tentativeLine, style,
            ).size.width.toDp()
        }

        if (tentativeWidth < maxWidth) {
            currentLine = tentativeLine
        } else {
            lines.add(currentLine)
            currentLine = word
        }
    }

    if (currentLine.isNotEmpty()) {
        lines.add(currentLine)
    }

    return lines
}

/**
 * Get current position in lyric line list
 */
fun findCurrentLineIndex(lines: List<LyricLine>, position: Long): Int {
    if (lines.isEmpty()) return -1
    val safePosition = position.coerceAtLeast(0L).toULong()
    for (index in lines.indices) {
        if (lines[index].start > safePosition) {
            return if (index > 0 && lines[index - 1].isTranslated) index - 2 else index - 1
        }
    }
    return if (lines[lines.lastIndex].isTranslated) lines.lastIndex - 1 else lines.lastIndex
}

/**
 * Get current position in lyric line. Used for word by word lyrics
 */
fun calculateLineProgress(line: LyricLine, currentPositionMs: Long): Float {
    val words = line.words
    val startMs = line.start.toLong()
    val endMs = line.end.toLong()

    // by line if no words are available
    if (words.isNullOrEmpty()) {
        return when {
            currentPositionMs < startMs -> 0f
            currentPositionMs > endMs -> 1f // add buffer so lyric line animation completes
            else -> (currentPositionMs - startMs).toFloat() / (endMs - startMs).toFloat()
        }
    }

    // progress based on words
    val currentMs = currentPositionMs.toULong()
    var completedWords = 0
    var partialProgress = 0f

    return when {
        currentPositionMs < startMs -> 0f
        currentPositionMs > endMs -> 1f // add buffer so lyric line animation completes
        else -> {
            for (i in words.indices) {
                val word = words[i]
                val start = word.timeRange.first
                val end = word.timeRange.last

                if (currentMs < start) {
                    break // we're before this word
                } else if (currentMs in word.timeRange) {
                    val wordDuration = (end - start).coerceAtLeast(1u).toFloat()
                    partialProgress = (currentMs - start).toFloat() / wordDuration
                    completedWords = i
                    break
                } else {
                    completedWords++
                }
            }

            val totalWords = words.size.toFloat()
            var progress = (completedWords + partialProgress) / totalWords
            progress.coerceIn(0f, 1f)
        }
    }
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 7.seconds

