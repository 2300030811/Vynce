package com.vynce.app.ui.screens.saavn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.playback.PlayerConnection
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnPlaylistInfo
import com.zionhuang.jiosaavn.SaavnSong
import com.vynce.app.utils.playJioSaavnSong

@Composable
fun PlaylistScreen(
    playlistId: String,
    navController: NavController,
    playerConnection: PlayerConnection?
) {
    var playlistInfo by remember { mutableStateOf(SaavnPlaylistInfo()) }
    var songs by remember { mutableStateOf<List<SaavnSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        val (info, songList) = JioSaavn.getPlaylist(playlistId)
        playlistInfo = info; songs = songList; isLoading = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                AsyncImage(model = playlistInfo.image, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(16.dp))
                Text(playlistInfo.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${playlistInfo.songCount} songs • ${formatFollowerCount(playlistInfo.followerCount)} followers",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { playAllSongs(songs, playerConnection) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play all")
                    }
                    OutlinedButton(onClick = { playAllSongs(songs.shuffled(), playerConnection) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(4.dp)); Text("Shuffle")
                    }
                }
            }
        }
        if (isLoading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
        }
        itemsIndexed(songs) { index, song ->
            with(JioSaavn) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { playJioSaavnSong(song, playerConnection) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artistNames(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatDuration(song.duration.toIntOrNull() ?: 0),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
