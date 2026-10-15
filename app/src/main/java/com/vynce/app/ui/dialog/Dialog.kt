/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.ui.dialog

import android.content.ClipData
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vynce.app.LocalPlayerConnection
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vynce.app.LocalSnackbarHostState
import com.vynce.app.R
import com.vynce.app.constants.DialogCornerRadius
import com.vynce.app.constants.SNACKBAR_VERY_SHORT
import com.vynce.app.db.entities.FormatEntity
import com.vynce.app.models.MediaMetadata
import com.vynce.app.ui.component.button.IconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            icon()
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(
                                // Align the title to the center when an icon is present.
                                Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                            ) {
                                title()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                content()

                if (buttons != null) {
                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.labelLarge
                            ) {
                                buttons()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier.padding(vertical = 24.dp)
            ) {
                val lazyListState = rememberLazyListState()
                LazyColumn(
                    state = lazyListState,
                    content = content
                )
            }
        }
    }
}

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(),
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val (textFieldValue, onTextFieldValueChange) = remember {
        mutableStateOf(initialTextFieldValue)
    }

    val focusRequester = remember {
        FocusRequester()
    }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }

            TextButton(
                enabled = isInputValid(textFieldValue.text),
                onClick = {
                    onDismiss()
                    onDone(textFieldValue.text)
                }
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    ) {
        TextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            maxLines = maxLines,
            colors = OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(
                imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                keyboardType = keyboardType
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onDone(textFieldValue.text)
                    onDismiss()
                }
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
        )

        extraContent?.invoke()
    }
}

/**
 * Dialog for user interaction
 *
 * @param title Title of prompt
 * @param description Description of prompt
 * @param confirmText Text for confirm button
 * @param titleBar Title of prompt. Specifying this will override title
 * @param onDismiss
 * @param onConfirm
 * @param onReset
 * @param onCancel
 * @param content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    confirmText: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    isInputValid: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}
) = BasicAlertDialog(
    onDismissRequest = { onDismiss() },
    content = {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.background,
                    RoundedCornerShape(DialogCornerRadius)
                )
                .fillMaxWidth(0.8f)
                .padding(16.dp)
        ) {
            Column(
                modifier = modifier
                    .padding(12.dp)
                    .weight(1f, false)
            ) {
                // title
                if (titleBar != null) {
                    Row {
                        titleBar()
                    }
                } else if (title != null) {
                    Text(
                        text = title,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                content() // body
            }

            // bottom options
            // always have an ok, but explicit cancel/reset is optional
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onReset != null) {
                    Row(modifier = Modifier.weight(1f)) {
                        TextButton(
                            onClick = { onReset() },
                        ) {
                            Text(stringResource(R.string.reset))
                        }
                    }
                }

                if (onCancel != null) {
                    TextButton(
                        onClick = { onCancel() }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }

                TextButton(
                    onClick = { onConfirm() },
                    enabled = isInputValid
                ) {
                    Text(confirmText ?: stringResource(android.R.string.ok))
                }
            }
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterDialog(
    title: String,
    description: String? = null,
    initialValue: Int,
    upperBound: Int = 100,
    lowerBound: Int = 0,
    unitDisplay: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) = BasicAlertDialog(
    onDismissRequest = { onDismiss() },
    content = {
        val tempValue = rememberSaveable {
            mutableIntStateOf(initialValue)
        }
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.background,
                    RoundedCornerShape(DialogCornerRadius)
                )
                .fillMaxWidth(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // title and description
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // plus minus buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (tempValue.intValue < upperBound) {
                                tempValue.intValue += 1
                            }
                        },
                    ) {
                        Text(
                            text = "+",
//                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }


                    IconButton(
                        onClick = {
                            if (tempValue.intValue > lowerBound) {
                                tempValue.intValue -= 1
                            }
                        },
                    ) {
                        Text(
                            text = "—",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                // slider and value display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${tempValue.intValue}$unitDisplay",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Slider(
                        value = tempValue.intValue.toFloat(),
                        onValueChange = { tempValue.intValue = it.toInt() },
                        valueRange = lowerBound.toFloat()..upperBound.toFloat()
                    )
                }

                // bottom options
                // always have an ok, but explicit cancel/reset is optional
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onReset != null)
                        Row(modifier = Modifier.weight(1f)) {
                            TextButton(
                                onClick = { onReset() },
                            ) {
                                Text(stringResource(R.string.reset))
                            }
                        }

                    TextButton(
                        onClick = { onConfirm(tempValue.intValue) }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }

                    if (onCancel != null)
                        TextButton(
                            onClick = { onCancel() }
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                }
            }
        }
    }
)

@Composable
fun DetailsDialog(
    mediaMetadata: MediaMetadata,
    currentFormat: FormatEntity?,
    currentPlayCount: Int?,
    clipboardManager: Clipboard,
    setVisibility: (newState: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val playerConnection = com.vynce.app.LocalPlayerConnection.current
    val liveAudioFormat = playerConnection?.player?.audioFormat

    fun copyToClipboard(label: String, value: String) {
        val clipData = ClipData.newPlainText(label, AnnotatedString(value))
        clipboardManager.nativeClipboard.setPrimaryClip(clipData)
        coroutineScope.launch {
            val job = launch {
                snackbarHostState.showSnackbar(
                    message = "Copied $label",
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite
                )
            }
            delay(SNACKBAR_VERY_SHORT)
            job.cancel()
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { setVisibility(false) },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        confirmButton = {
            TextButton(
                onClick = { setVisibility(false) }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Header with Artwork Thumbnail & Basic Info
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (mediaMetadata.thumbnailUrl != null) {
                            AsyncImage(
                                model = mediaMetadata.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mediaMetadata.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val artistText = mediaMetadata.artists.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
                            if (!artistText.isNullOrBlank()) {
                                Text(
                                    text = artistText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (mediaMetadata.album?.title?.isNotBlank() == true) {
                                Text(
                                    text = mediaMetadata.album.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 2. Track Metadata Card
                val trackDetails = mutableListOf<Pair<String, String>>()
                trackDetails.add(stringResource(R.string.media_id) to mediaMetadata.id)
                if (mediaMetadata.duration > 0) {
                    trackDetails.add("Duration" to com.vynce.app.utils.makeTimeString((mediaMetadata.duration * 1000).toLong()))
                }
                if (mediaMetadata.isLocal) {
                    mediaMetadata.trackNumber?.let { trackDetails.add(stringResource(R.string.track_number) to it.toString()) }
                    mediaMetadata.discNumber?.let { trackDetails.add(stringResource(R.string.disc_number) to it.toString()) }
                    mediaMetadata.getDateString()?.let { trackDetails.add(stringResource(R.string.sort_by_date_released) to it) }
                    mediaMetadata.getDateModifiedString()?.let { trackDetails.add(stringResource(R.string.sort_by_date_modified) to it) }
                    mediaMetadata.localPath?.let { trackDetails.add(stringResource(R.string.file_path) to it) }
                }

                DetailSectionCard(
                    title = "Track Information",
                    items = trackDetails,
                    onItemClick = { label, value -> copyToClipboard(label, value) }
                )

                // 3. Playback Stats Card
                if (currentPlayCount != null && currentPlayCount > 0) {
                    DetailSectionCard(
                        title = "Listening Stats",
                        items = listOf(stringResource(R.string.play_count) to "$currentPlayCount plays"),
                        onItemClick = { label, value -> copyToClipboard(label, value) }
                    )
                }

                // 4. Audio Quality & Specifications Card
                val audioSpecs = mutableListOf<Pair<String, String>>()

                // Bitrate
                val bitrateKbps = currentFormat?.bitrate?.let { it / 1000 }
                    ?: liveAudioFormat?.bitrate?.takeIf { it > 0 }?.let { it / 1000 }
                if (bitrateKbps != null && bitrateKbps > 0) {
                    audioSpecs.add(stringResource(R.string.bitrate) to "$bitrateKbps Kbps")
                }

                // Sample Rate
                val sampleRateHz = currentFormat?.sampleRate
                    ?: liveAudioFormat?.sampleRate?.takeIf { it > 0 }
                if (sampleRateHz != null && sampleRateHz > 0) {
                    val formattedSr = if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", sampleRateHz / 1000f)
                    audioSpecs.add(stringResource(R.string.sample_rate) to "$formattedSr ($sampleRateHz Hz)")
                }

                // Channels
                val channels = liveAudioFormat?.channelCount?.takeIf { it > 0 }
                if (channels != null) {
                    val channelLabel = when (channels) {
                        1 -> "1 Channel (Mono)"
                        2 -> "2 Channels (Stereo)"
                        6 -> "5.1 Surround"
                        8 -> "7.1 Surround"
                        else -> "$channels Channels"
                    }
                    audioSpecs.add("Channels" to channelLabel)
                }

                // MIME type / Codec
                val mime = currentFormat?.mimeType ?: liveAudioFormat?.sampleMimeType
                val codecs = currentFormat?.codecs ?: liveAudioFormat?.codecs
                if (!mime.isNullOrBlank()) {
                    val friendlyCodec = when {
                        mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC ($mime)"
                        mime.contains("flac", ignoreCase = true) -> "FLAC Lossless ($mime)"
                        mime.contains("opus", ignoreCase = true) -> "Opus ($mime)"
                        mime.contains("vorbis", ignoreCase = true) -> "Ogg Vorbis ($mime)"
                        mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) -> "MP3 ($mime)"
                        else -> mime
                    }
                    audioSpecs.add(stringResource(R.string.mime_type) to friendlyCodec)
                } else if (!codecs.isNullOrBlank()) {
                    audioSpecs.add(stringResource(R.string.codecs) to codecs)
                }

                // Bits per sample
                currentFormat?.bitsPerSample?.takeIf { it > 0 }?.let {
                    audioSpecs.add(stringResource(R.string.bits_per_sample) to "$it-bit")
                }

                // Loudness
                currentFormat?.loudnessDb?.let {
                    audioSpecs.add(stringResource(R.string.loudness) to "$it dB")
                }

                // File size
                val fileSize = currentFormat?.contentLength?.let {
                    if (mediaMetadata.isLocal && mediaMetadata.localPath != null && File(mediaMetadata.localPath).exists()) {
                        Formatter.formatShortFileSize(context, File(mediaMetadata.localPath).length())
                    } else {
                        Formatter.formatShortFileSize(context, it)
                    }
                }
                if (fileSize != null) {
                    audioSpecs.add(stringResource(R.string.file_size) to fileSize)
                }

                if (audioSpecs.isNotEmpty()) {
                    DetailSectionCard(
                        title = "Audio Specifications",
                        items = audioSpecs,
                        onItemClick = { label, value -> copyToClipboard(label, value) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(DialogCornerRadius)
    )
}

@Composable
private fun DetailSectionCard(
    title: String,
    items: List<Pair<String, String>>,
    onItemClick: (label: String, value: String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            items.forEach { (label, value) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onItemClick(label, value) }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun InfoLabel(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.padding(horizontal = 8.dp)
) {
    Icon(
        if (isError) Icons.Outlined.Error else Icons.Outlined.Info,
        contentDescription = null,
        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(4.dp)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

