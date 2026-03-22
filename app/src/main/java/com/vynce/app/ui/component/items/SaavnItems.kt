package com.vynce.app.ui.component.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vynce.app.LocalMenuState
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.R
import com.vynce.app.constants.ListThumbnailSize
import com.vynce.app.constants.ThumbnailCornerRadius
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.menu.SaavnSongMenu
import com.vynce.app.utils.joinByBullet
import com.vynce.app.utils.makeTimeString
import com.vynce.app.utils.toSaavnMediaMetadata
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong

import com.vynce.app.extensions.decodeHtml

@Composable
fun SaavnSongRow(
    song: SaavnSong,
    navController: NavController,
    onClick: () -> Unit
) {
    SaavnSongListItem(
        song = song,
        navController = navController,
        onPlay = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaavnSongListItem(
    song: SaavnSong,
    navController: NavController,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val mediaMetadata = remember(song) { song.toSaavnMediaMetadata() }
    val isActive by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val active = isActive?.id == mediaMetadata.id

    with(JioSaavn) {
        ListItem(
            title = song.name.decodeHtml(),
            subtitle = joinByBullet(
                song.artistNames().decodeHtml(),
                song.duration.let { makeTimeString(it.toLong() * 1000L) }
            ),
            badges = {
                // Quality badge
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "320",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.thumbnailUrl()?.replace("http://", "https://"),
                    isActive = active,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    modifier = Modifier.size(ListThumbnailSize)
                )
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        menuState.show {
                            SaavnSongMenu(
                                song = song,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = null
                    )
                }
            },
            isActive = active,
            modifier = modifier.combinedClickable(
                onClick = onPlay,
                onLongClick = {
                    menuState.show {
                        SaavnSongMenu(
                            song = song,
                            navController = navController,
                            onDismiss = menuState::dismiss
                        )
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        )
    }
}

@Composable
fun SaavnSongCard(
    song: SaavnSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    with(JioSaavn) {
        Column(
            modifier = modifier
                .width(160.dp)
                .clickable(onClick = onClick)
                .padding(8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.thumbnailUrl()?.replace("http://", "https://"))
                    .build(),
                placeholder = painterResource(R.drawable.launcher_foreground),
                error = painterResource(R.drawable.launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(144.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = song.name.decodeHtml(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artistNames().decodeHtml(),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
