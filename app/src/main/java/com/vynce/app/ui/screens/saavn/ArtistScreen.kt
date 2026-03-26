package com.vynce.app.ui.screens.saavn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.playback.PlayerConnection
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnArtistInfo
import com.zionhuang.jiosaavn.SaavnAlbumInfo
import com.zionhuang.jiosaavn.SaavnSong
import com.vynce.app.ui.screens.home.SectionHeader
import com.vynce.app.ui.component.items.SaavnSongListItem
import com.vynce.app.utils.playJioSaavnSong

@Composable
fun ArtistScreen(
    artistId: String,
    navController: NavController,
    playerConnection: PlayerConnection?
) {
    var artistInfo by remember { mutableStateOf(SaavnArtistInfo()) }
    var songs by remember { mutableStateOf<List<SaavnSong>>(emptyList()) }
    var albums by remember { mutableStateOf<List<SaavnAlbumInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistId) {
        val (info, songList, albumList) = JioSaavn.getArtistDetail(artistId)
        artistInfo = info; songs = songList; albums = albumList
        isLoading = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        // Hero image
        item {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                AsyncImage(
                    model = artistInfo.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient scrim at bottom
                Box(modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    ))
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) {
                    Text(artistInfo.name, style = MaterialTheme.typography.headlineLarge,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    if (artistInfo.followerCount != "0") {
                        Text("${formatFollowerCount(artistInfo.followerCount)} followers",
                            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // Play button
        item {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { playAllSongs(songs, playerConnection) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play")
                }
                OutlinedButton(onClick = { playAllSongs(songs.shuffled(), playerConnection) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(4.dp)); Text("Shuffle")
                }
            }
        }

        // Top songs
        if (songs.isNotEmpty()) {
            item { SectionHeader("Popular songs") }
            items(songs.take(10)) { song ->
                with(JioSaavn) {
                    SaavnSongListItem(
                        song = song,
                        navController = navController,
                        onPlay = { playJioSaavnSong(song, playerConnection) }
                    )
                }
            }
        }

        // Albums
        if (albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums) { album ->
                        Column(
                            modifier = Modifier.width(130.dp)
                                .clickable { navController.navigate("album/${album.id}") }
                        ) {
                            AsyncImage(
                                model = album.image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(130.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(album.name, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.year, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

