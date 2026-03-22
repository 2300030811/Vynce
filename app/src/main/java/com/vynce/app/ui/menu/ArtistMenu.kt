package com.vynce.app.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vynce.app.LocalDatabase
import com.vynce.app.LocalNetworkConnected
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.R
import com.vynce.app.constants.ArtistSongSortType
import com.vynce.app.db.entities.Artist
import com.vynce.app.models.toMediaMetadata
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.component.items.ArtistListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isNetworkConnected = LocalNetworkConnected.current
    val artistState = database.artist(originalArtist.id).collectAsState(initial = originalArtist)
    val artist = artistState.value ?: originalArtist

    ArtistListItem(
        artist = artist,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    database.transaction {
                        update(artist.artist.toggleLike())
                    }
                }
            ) {
                Icon(
                    painter = painterResource(if (artist.artist.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (artist.artist.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
            }
        }
    )

    HorizontalDivider()

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = Icons.Rounded.Shuffle,
            title = R.string.shuffle
        ) {
            coroutineScope.launch {
                val songs = withContext(Dispatchers.IO) {
                    database.artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true).first()
                        .map { it.toMediaMetadata() }
                        .shuffled()
                }

                playerConnection.playQueue(
                    ListQueue(
                        title = artist.artist.name,
                        items = songs,
                        playlistId = null
                    )
                )
            }
            onDismiss()
        }
        if (!artist.artist.isLocal) {
            GridMenuItem(
                icon = Icons.Rounded.Share,
                title = R.string.share
            ) {
                onDismiss()
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://www.jiosaavn.com/artist/${artist.id}")
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
        }
    }
}

