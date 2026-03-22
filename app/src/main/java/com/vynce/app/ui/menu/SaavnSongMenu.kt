package com.vynce.app.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.LocalDatabase
import com.vynce.app.LocalDownloadUtil
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.LocalSyncUtils
import com.vynce.app.R
import com.vynce.app.constants.ListThumbnailSize
import com.vynce.app.constants.ThumbnailCornerRadius
import com.vynce.app.db.entities.SongEntity
import com.vynce.app.extensions.toMediaItem
import com.vynce.app.models.MediaMetadata
import com.vynce.app.playback.ExoDownloadService
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.component.items.ListItem
import com.vynce.app.ui.dialog.AddToPlaylistDialog
import com.vynce.app.ui.dialog.AddToQueueDialog
import com.vynce.app.ui.dialog.ArtistDialog
import com.vynce.app.utils.joinByBullet
import com.vynce.app.utils.makeTimeString
import com.vynce.app.utils.toSaavnMediaMetadata
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong
import kotlinx.coroutines.launch

@Composable
fun SaavnSongMenu(
    song: SaavnSong,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadUtil = LocalDownloadUtil.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueBoard by playerConnection.queueBoard.collectAsState()
    val syncUtils = LocalSyncUtils.current

    val mediaMetadata = remember(song) { song.toSaavnMediaMetadata() }
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)
    
    val artists = remember(song) {
        mediaMetadata.artists
    }

    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    with(JioSaavn) {
        ListItem(
            title = song.name,
            subtitle = joinByBullet(
                song.artistNames(),
                song.duration.let { makeTimeString(it.toLong() * 1000L) }
            ),
            thumbnailContent = {
                AsyncImage(
                    model = song.thumbnailUrl()?.replace("http://", "https://"),
                    contentDescription = null,
                    modifier = Modifier
                        .size(ListThumbnailSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        database.transaction {
                            librarySong.let { librarySong ->
                                val s: SongEntity
                                if (librarySong == null) {
                                    insert(mediaMetadata, SongEntity::toggleLike)
                                    s = mediaMetadata.toSongEntity().let(SongEntity::toggleLike)
                                } else {
                                    s = librarySong.song.toggleLike()
                                    update(s)
                                }

                                syncUtils.likeSong(s)
                            }
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (librarySong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (librarySong?.song?.liked == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null
                    )
                }
            }
        )
    }

    HorizontalDivider()

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        // Radio is not supported for Saavn yet, might add later if possible
        /*
        GridMenuItem(
            icon = Icons.Rounded.Radio,
            title = R.string.start_radio
        ) {
            // Radio logic for Saavn?
            onDismiss()
        }
        */
        GridMenuItem(
            icon = Icons.Rounded.PlayArrow,
            title = R.string.play
        ) {
            playerConnection.playQueue(
                queue = ListQueue(
                    title = song.name,
                    items = listOf(mediaMetadata)
                )
            )
            onDismiss()
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = R.string.play_next
        ) {
            playerConnection.enqueueNext(mediaMetadata.toMediaItem())
            onDismiss()
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        DownloadGridMenu(
            localDateTime = download,
            onDownload = {
                database.transaction {
                    insert(mediaMetadata)
                }
                downloadUtil.download(mediaMetadata)
            },
            onRemoveDownload = {
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    mediaMetadata.id,
                    false
                )
            }
        )
        if (artists.isNotEmpty()) {
            GridMenuItem(
                icon = Icons.Rounded.Person,
                title = R.string.view_artist
            ) {
                if (artists.size == 1) {
                    // Artist view might not be fully supported for Saavn yet depending on how it's implemented
                    // But if it's there, we navigate
                    navController.navigate("artist/${artists[0].id}")
                    onDismiss()
                } else {
                    showSelectArtistDialog = true
                }
            }
        }
        mediaMetadata.album?.let { album ->
            GridMenuItem(
                icon = Icons.Rounded.Album,
                title = R.string.view_album
            ) {
                navController.navigate("album/${album.id}")
                onDismiss()
            }
        }
        GridMenuItem(
            icon = Icons.Rounded.Share,
            title = R.string.share
        ) {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://www.jiosaavn.com/song/${song.id}")
            }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
        }
    }

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showChooseQueueDialog) {
        AddToQueueDialog(
            onAdd = { queueName ->
                val q = queueBoard.addQueue(
                    queueName, listOf(mediaMetadata),
                    forceInsert = true, delta = false
                )
                q?.let {
                    queueBoard.setCurrQueue(it)
                }
            },
            onDismiss = {
                showChooseQueueDialog = false
            }
        )
    }

    if (showChoosePlaylistDialog) {
        AddToPlaylistDialog(
            navController = navController,
            songIds = null,
            onPreAdd = { playlist ->
                database.transaction {
                    insert(mediaMetadata)
                }
                
                // YouTube sync doesn't apply to Saavn songs
                
                listOf(mediaMetadata.id)
            },
            onDismiss = { showChoosePlaylistDialog = false }
        )
    }

    if (showSelectArtistDialog) {
        ArtistDialog(
            navController = navController,
            artists = artists,
            onDismiss = { showSelectArtistDialog = false }
        )
    }
}
