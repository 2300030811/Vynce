package com.vynce.app.ui.screens.saavn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.ui.utils.appBarScrollBehavior
import com.vynce.app.ui.utils.backToMain
import com.vynce.app.ui.component.button.IconButton
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnAlbumInfo
import com.zionhuang.jiosaavn.SaavnSong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    navController: NavController,
    playerConnection: PlayerConnection?
) {
    var albumInfo by remember { mutableStateOf(SaavnAlbumInfo()) }
    var songs by remember { mutableStateOf<List<SaavnSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val scrollBehavior = appBarScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(albumId) {
        val (info, songList) = JioSaavn.getAlbum(albumId)
        albumInfo = info
        songs = songList
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    AsyncImage(
                        model = albumInfo.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(albumInfo.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(albumInfo.artists, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${albumInfo.year} • ${albumInfo.songCount} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    
                    // Play All + Shuffle buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { playAllSongs(albumInfo.name, songs, playerConnection) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Play all")
                        }
                        OutlinedButton(
                            onClick = {
                                playAllSongs(
                                    title = albumInfo.name,
                                    songs = songs,
                                    playerConnection = playerConnection,
                                    shuffle = true
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shuffle, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }

            if (isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator() } }
            }

            // Song list — numbered
        itemsIndexed(songs) { index, song ->
            with(JioSaavn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            playAllSongs(
                                title = albumInfo.name,
                                songs = songs,
                                playerConnection = playerConnection,
                                startIndex = index
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.name, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artistNames(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Duration
                    Text(
                        text = formatDuration(song.duration.toIntOrNull() ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

        LargeTopAppBar(
            title = {
                if (!isLoading) {
                    Column {
                        Text(albumInfo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(albumInfo.artists, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}
