package com.vynce.app.ui.screens.saavn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.utils.appBarScrollBehavior
import com.vynce.app.ui.utils.backToMain
import com.vynce.app.utils.toSaavnMediaMetadata
import com.vynce.jiosaavn.JioSaavn
import com.vynce.jiosaavn.SaavnAlbumInfo
import com.vynce.jiosaavn.SaavnSong
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    albumTitle: String? = null,
    navController: NavController,
    playerConnection: PlayerConnection?
) {
    var albumInfo by remember { mutableStateOf(SaavnAlbumInfo()) }
    var songs by remember { mutableStateOf<List<SaavnSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    val scrollBehavior = appBarScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(albumId, albumTitle, reloadKey) {
        isLoading = true
        error = null
        albumInfo = SaavnAlbumInfo()
        songs = emptyList()

        try {
            val cleanId = albumId
                .removePrefix("saavn_album:")
                .removePrefix("saavn:")
                .trim()

            val isSynthetic = albumId.startsWith("saavn_album:")
            val titleQuery = albumTitle?.trim()?.takeIf { it.isNotEmpty() }
                ?: if (isSynthetic && !cleanId.all { it.isDigit() || it == '-' }) cleanId else null

            var resolvedInfo = SaavnAlbumInfo()
            var resolvedSongs = emptyList<SaavnSong>()

            // 1. If not synthetic and we have a valid ID, try fetching directly
            if (!isSynthetic && cleanId.isNotEmpty()) {
                val (info, songList) = JioSaavn.getAlbum(cleanId)
                if (info.name.isNotBlank() || songList.isNotEmpty()) {
                    resolvedInfo = info
                    resolvedSongs = songList
                }
            }

            // 2. If direct fetch didn't return songs or it is synthetic, resolve via album search
            if (resolvedSongs.isEmpty() && !titleQuery.isNullOrBlank()) {
                val searchResults = JioSaavn.searchAlbums(titleQuery)
                if (searchResults.isNotEmpty()) {
                    val bestAlbum = searchResults.firstOrNull { it.name.equals(titleQuery, ignoreCase = true) }
                        ?: searchResults.first()
                    val (info, songList) = JioSaavn.getAlbum(bestAlbum.id)
                    if (info.name.isNotBlank() || songList.isNotEmpty()) {
                        resolvedInfo = if (info.name.isNotBlank()) info else SaavnAlbumInfo(
                            id = bestAlbum.id,
                            name = bestAlbum.name,
                            artists = bestAlbum.artists,
                            image = bestAlbum.image,
                            songCount = bestAlbum.songCount,
                            year = bestAlbum.year
                        )
                        resolvedSongs = songList
                    }
                }

                // 3. Secondary fallback: search songs directly by title
                if (resolvedSongs.isEmpty()) {
                    val searchedSongs = JioSaavn.searchSongs(titleQuery)
                    if (searchedSongs.isNotEmpty()) {
                        val firstSong = searchedSongs.first()
                        if (resolvedInfo.name.isBlank()) {
                            resolvedInfo = SaavnAlbumInfo(
                                id = "saavn_album:$titleQuery",
                                name = firstSong.album.ifBlank { titleQuery },
                                artists = firstSong.primaryArtists,
                                image = firstSong.image,
                                songCount = searchedSongs.size.toString(),
                                year = firstSong.year
                            )
                        }
                        resolvedSongs = searchedSongs
                    }
                }
            }

            if (resolvedSongs.isEmpty() && resolvedInfo.name.isBlank()) {
                error = "Couldn't load album${if (!titleQuery.isNullOrBlank()) " \"$titleQuery\"" else ""}"
            } else {
                albumInfo = resolvedInfo
                songs = resolvedSongs
            }
        } catch (e: CancellationException) {
            // Prevent stale updates from completing when user navigates away or switches albums
            throw e
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Failed to load album"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    val displayTitle = albumInfo.name.ifBlank { albumTitle ?: "" }
                    if (displayTitle.isNotBlank()) {
                        Column {
                            Text(displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (albumInfo.artists.isNotBlank()) {
                                Text(albumInfo.artists, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
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
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null && songs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
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
                            text = "Couldn't load album",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error ?: "Check your connection and try again",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        FilledTonalButton(onClick = { reloadKey += 1 }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 100.dp
                    )
                ) {
                    // Header
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            AsyncImage(
                                model = albumInfo.image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .aspectRatio(1f)
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(12.dp))
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
                                    onClick = { playAllSongs(albumInfo.name, songs, playerConnection, playlistId = "saavn_album:${albumInfo.id}") },
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
                                            shuffle = true,
                                            playlistId = "saavn_album:${albumInfo.id}"
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Shuffle, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Shuffle")
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Download All + Save to Playlist buttons
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val downloadUtil = com.vynce.app.LocalDownloadUtil.current
                            var showAddToPlaylistDialog by remember { mutableStateOf(false) }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (songs.isNotEmpty()) {
                                            val mediaItems = songs.map { it.toSaavnMediaMetadata() }
                                            downloadUtil.download(mediaItems)
                                            android.widget.Toast.makeText(context, "Downloading ${songs.size} songs...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download")
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (songs.isNotEmpty()) {
                                            showAddToPlaylistDialog = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Playlist")
                                }
                            }

                            if (showAddToPlaylistDialog) {
                                val songIds = songs.map { "saavn:${it.id}" }
                                com.vynce.app.ui.dialog.AddToPlaylistDialog(
                                    navController = navController,
                                    songIds = songIds,
                                    onDismiss = { showAddToPlaylistDialog = false }
                                )
                            }
                        }
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
                                            startIndex = index,
                                            playlistId = "saavn_album:${albumInfo.id}"
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
            }
        }
    }
}
