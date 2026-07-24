package com.vynce.app.ui.screens.saavn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import com.vynce.jiosaavn.JioSaavn
import com.vynce.jiosaavn.SaavnArtistInfo
import com.vynce.jiosaavn.SaavnAlbumInfo
import com.vynce.jiosaavn.SaavnSong
import com.vynce.app.ui.screens.home.SectionHeader
import com.vynce.app.ui.component.items.SaavnSongListItem
import kotlinx.coroutines.CancellationException

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
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var artistBio by remember { mutableStateOf<String?>(null) }
    var artistWikiImage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId, reloadKey) {
        isLoading = true
        error = null
        artistInfo = SaavnArtistInfo()
        songs = emptyList()
        albums = emptyList()
        artistBio = null
        artistWikiImage = null

        try {
            val (info, songList, albumList) = JioSaavn.getArtistDetail(artistId)
            artistInfo = info
            songs = songList
            albums = albumList
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = exception.message ?: "Failed to load artist"
        } finally {
            isLoading = false
        }

        if (error == null && artistInfo.name.isNotEmpty()) {
            try {
                val bioResult = com.vynce.app.data.artist.ArtistBioRepository.getArtistBio(artistInfo.name)
                if (bioResult != null) {
                    artistBio = bioResult.bio
                    if (artistInfo.image.isEmpty() || artistInfo.image.contains("default", ignoreCase = true)) {
                        artistWikiImage = bioResult.imageUrl
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                Unit
            }
        }
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Couldn't load artist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = error ?: "Check your connection and try again",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = { reloadKey += 1 }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        // Hero image
        item {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                AsyncImage(
                    model = artistWikiImage ?: artistInfo.image,
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
                Button(onClick = { playAllSongs(artistInfo.name, songs, playerConnection) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play")
                }
                OutlinedButton(
                    onClick = {
                        playAllSongs(
                            title = artistInfo.name,
                            songs = songs,
                            playerConnection = playerConnection,
                            shuffle = true
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(4.dp)); Text("Shuffle")
                }
            }
        }

        // Biography
        artistBio?.takeIf { it.isNotEmpty() }?.let { bio ->
            item {
                var isExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { isExpanded = !isExpanded },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Biography",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isExpanded) "Show Less" else "Read More",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
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
                        onPlay = {
                            playAllSongs(
                                title = artistInfo.name,
                                songs = songs,
                                playerConnection = playerConnection,
                                startIndex = songs.indexOf(song).coerceAtLeast(0)
                            )
                        }
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
}
