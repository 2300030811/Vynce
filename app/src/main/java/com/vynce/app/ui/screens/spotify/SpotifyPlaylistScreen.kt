package com.vynce.app.ui.screens.spotify

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.LocalDownloadUtil
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.data.spotify.SpotifyEmbedPlaylist
import com.vynce.app.data.spotify.SpotifyEmbedTrack
import com.vynce.app.data.spotify.SpotifyPlaylistFetcher
import com.vynce.app.data.spotify.SpotifyResolver
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.SourceBadge
import com.vynce.app.ui.dialog.AddToPlaylistDialog
import com.vynce.app.ui.theme.VyncePurple
import com.vynce.app.utils.makeTimeString
import com.vynce.app.utils.toSaavnMediaMetadata
import com.vynce.jiosaavn.SaavnSong
import kotlinx.coroutines.*

@Composable
fun SpotifyPlaylistScreen(
    playlistId: String,
    navController: NavController,
    playerConnection: PlayerConnection?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bottomInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    var playlist by remember { mutableStateOf<SpotifyEmbedPlaylist?>(null) }
    val trackCovers = remember { mutableStateMapOf<Int, String>() }
    var isLoading by remember { mutableStateOf(true) }
    var isResolvingPlayback by remember { mutableStateOf(false) }
    var resolvingIndex by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(playlistId, reloadKey) {
        isLoading = true
        error = null
        trackCovers.clear()
        try {
            val result = SpotifyPlaylistFetcher.fetchPlaylist(playlistId, context)
            if (result != null) {
                playlist = result
                // Background hydrate individual track artworks matching Vynce Web
                coroutineScope.launch(Dispatchers.IO) {
                    val dispatcher = Dispatchers.IO.limitedParallelism(4)
                    val jobs = result.tracks.mapIndexed { index, track ->
                        async(dispatcher) {
                            if (track.coverUrl.isNotBlank()) {
                                trackCovers[index] = track.coverUrl
                            } else {
                                val resolved = SpotifyResolver.resolveToSaavn(
                                    trackName = track.name,
                                    artistName = track.artist,
                                    durationSeconds = track.durationMs / 1000
                                )
                                val img = resolved?.image
                                if (!img.isNullOrBlank()) {
                                    trackCovers[index] = img
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            } else {
                error = "Failed to load playlist. Please check your connection."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to load playlist"
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VyncePurple)
        }
        error != null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
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
            Text("Couldn't load playlist", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(error ?: "Check network and try again", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = { reloadKey += 1 }) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
        playlist != null -> {
            val p = playlist!!
            val tracks = p.tracks

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInsets + 64.dp)
            ) {
                // Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1DB954).copy(alpha = 0.35f), Color(0xFF0F172A), Color.Black)
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AsyncImage(
                                    model = p.coverUrl,
                                    contentDescription = p.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = Color(0xFF1DB954).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "CURATED MIX",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1DB954)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = p.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (p.description.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = p.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "${tracks.size} tracks • 320kbps Lossless Stream",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Action buttons: Row 1 - Play All & Shuffle
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty() && playerConnection != null) {
                                    coroutineScope.launch {
                                        isResolvingPlayback = true
                                        Toast.makeText(context, "Loading lossless tracks...", Toast.LENGTH_SHORT).show()
                                        playSpotifyPlaylist(p, playerConnection, startIndex = 0, shuffle = false)
                                        isResolvingPlayback = false
                                    }
                                }
                            },
                            enabled = !isResolvingPlayback && tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = VyncePurple)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isResolvingPlayback && resolvingIndex == -1) "Loading..." else "Play All")
                        }

                        OutlinedButton(
                            onClick = {
                                if (tracks.isNotEmpty() && playerConnection != null) {
                                    coroutineScope.launch {
                                        isResolvingPlayback = true
                                        Toast.makeText(context, "Shuffling playlist...", Toast.LENGTH_SHORT).show()
                                        playSpotifyPlaylist(p, playerConnection, startIndex = 0, shuffle = true)
                                        isResolvingPlayback = false
                                    }
                                }
                            },
                            enabled = !isResolvingPlayback && tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shuffle, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                    }
                }

                // Action buttons: Row 2 - Download All & Save to Playlist
                item {
                    val downloadUtil = LocalDownloadUtil.current
                    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
                    var isDownloading by remember { mutableStateOf(false) }
                    var isPreparingPlaylist by remember { mutableStateOf(false) }
                    var playlistSongIds by remember { mutableStateOf<List<String>>(emptyList()) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (tracks.isNotEmpty() && !isDownloading) {
                                    coroutineScope.launch {
                                        isDownloading = true
                                        Toast.makeText(context, "Resolving tracks for lossless download...", Toast.LENGTH_SHORT).show()
                                        val resolved = resolveAllTracksToSaavn(tracks)
                                        if (resolved.isNotEmpty()) {
                                            val mediaItems = resolved.map { it.toSaavnMediaMetadata() }
                                            downloadUtil.download(mediaItems)
                                            Toast.makeText(context, "Downloading ${mediaItems.size} songs...", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Could not resolve tracks for download", Toast.LENGTH_SHORT).show()
                                        }
                                        isDownloading = false
                                    }
                                }
                            },
                            enabled = !isDownloading && tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isDownloading) "Preparing..." else "Download")
                        }

                        OutlinedButton(
                            onClick = {
                                if (tracks.isNotEmpty() && !isPreparingPlaylist) {
                                    coroutineScope.launch {
                                        isPreparingPlaylist = true
                                        Toast.makeText(context, "Preparing playlist...", Toast.LENGTH_SHORT).show()
                                        val resolved = resolveAllTracksToSaavn(tracks)
                                        if (resolved.isNotEmpty()) {
                                            playlistSongIds = resolved.map { "saavn:${it.id}" }
                                            showAddToPlaylistDialog = true
                                        } else {
                                            Toast.makeText(context, "Could not load songs for playlist", Toast.LENGTH_SHORT).show()
                                        }
                                        isPreparingPlaylist = false
                                    }
                                }
                            },
                            enabled = !isPreparingPlaylist && tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPreparingPlaylist) "Preparing..." else "Playlist")
                        }
                    }

                    if (showAddToPlaylistDialog && playlistSongIds.isNotEmpty()) {
                        AddToPlaylistDialog(
                            navController = navController,
                            songIds = playlistSongIds,
                            onDismiss = { showAddToPlaylistDialog = false }
                        )
                    }
                }

                // Track items
                itemsIndexed(tracks) { index, track ->
                    val isCurrentResolving = resolvingIndex == index
                    val trackDuration = track.durationMs / 1000
                    val songArtwork = trackCovers[index] ?: track.coverUrl.ifBlank { null }

                    ListItem(
                        leadingContent = {
                            if (isCurrentResolving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else if (songArtwork != null) {
                                AsyncImage(
                                    model = songArtwork,
                                    contentDescription = track.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                text = track.name,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "${track.artist} • ${makeTimeString(trackDuration.toLong() * 1000L)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            SourceBadge(text = "320k")
                        },
                        modifier = Modifier.clickable {
                            if (playerConnection != null && !isResolvingPlayback) {
                                coroutineScope.launch {
                                    resolvingIndex = index
                                    isResolvingPlayback = true
                                    playSpotifyPlaylist(
                                        playlist = p,
                                        playerConnection = playerConnection,
                                        startIndex = index,
                                        shuffle = false
                                    )
                                    isResolvingPlayback = false
                                    resolvingIndex = -1
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun resolveSpotifyTrack(
    track: SpotifyEmbedTrack
): SaavnSong? {
    return SpotifyResolver.resolveToSaavn(
        trackName = track.name,
        artistName = track.artist,
        durationSeconds = track.durationMs / 1000
    )
}

private suspend fun resolveAllTracksToSaavn(
    tracks: List<SpotifyEmbedTrack>
): List<SaavnSong> = withContext(Dispatchers.IO) {
    val dispatcher = Dispatchers.IO.limitedParallelism(6)
    val jobs = tracks.map { track ->
        async(dispatcher) {
            resolveSpotifyTrack(track)
        }
    }
    jobs.awaitAll().filterNotNull()
}

private suspend fun playSpotifyPlaylist(
    playlist: SpotifyEmbedPlaylist,
    playerConnection: PlayerConnection,
    startIndex: Int = 0,
    shuffle: Boolean = false
) = withContext(Dispatchers.IO) {
    val tracks = playlist.tracks
    if (tracks.isEmpty()) return@withContext

    // 1. First resolve the target clicked track for immediate instant playback
    val targetTrack = if (shuffle) null else tracks.getOrNull(startIndex)
    val targetResolved = targetTrack?.let { resolveSpotifyTrack(it) }

    // 2. Concurrently resolve all tracks in parallel
    val dispatcher = Dispatchers.IO.limitedParallelism(6)
    val resolvedArray = arrayOfNulls<SaavnSong>(tracks.size)
    if (targetTrack != null && targetResolved != null) {
        resolvedArray[startIndex] = targetResolved
    }

    val jobs = tracks.mapIndexed { idx, track ->
        if (idx == startIndex && targetResolved != null) null
        else async(dispatcher) {
            resolvedArray[idx] = resolveSpotifyTrack(track)
        }
    }.filterNotNull()
    jobs.awaitAll()

    val validSongs = if (shuffle) {
        resolvedArray.filterNotNull().shuffled()
    } else {
        resolvedArray.filterNotNull()
    }

    if (validSongs.isEmpty()) return@withContext

    val mediaItems = validSongs.map { it.toSaavnMediaMetadata() }
    val targetQueueIndex = if (shuffle) {
        0
    } else {
        if (targetResolved != null) {
            val pos = validSongs.indexOfFirst { it.id == targetResolved.id }
            if (pos >= 0) pos else startIndex.coerceIn(mediaItems.indices)
        } else {
            startIndex.coerceIn(mediaItems.indices)
        }
    }

    withContext(Dispatchers.Main) {
        playerConnection.playQueue(
            ListQueue(
                title = playlist.name,
                items = mediaItems,
                startIndex = targetQueueIndex,
                playlistId = "spotify_playlist_${playlist.id}"
            )
        )
    }
}
