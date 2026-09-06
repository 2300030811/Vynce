package com.vynce.app.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.LocalDatabase
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.R
import com.vynce.app.constants.ThumbnailCornerRadius
import com.vynce.app.data.spotify.SpotifyCurated
import com.vynce.app.data.spotify.SpotifyPlaylistFetcher
import com.vynce.app.extensions.decodeHtml
import com.vynce.app.models.TopResult
import com.vynce.app.models.UnifiedSearchResult
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.ArtistAvatar
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.screens.library.SpotifyImportDialog
import com.vynce.app.ui.component.items.*
import com.vynce.app.ui.component.shimmer.ListItemPlaceHolder
import com.vynce.app.ui.component.shimmer.ShimmerHost
import com.vynce.app.utils.makeTimeString
import com.vynce.app.utils.toSaavnMediaMetadata
import com.vynce.app.viewmodels.UnifiedSearchUiState
import com.vynce.app.viewmodels.UnifiedSearchViewModel
import com.vynce.jiosaavn.SaavnAlbum
import com.vynce.jiosaavn.SaavnArtist
import com.vynce.jiosaavn.SaavnPlaylist
import com.vynce.jiosaavn.SaavnSong
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSearchScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    viewModel: UnifiedSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.back)
                )
            }
            Text(
                text = "Search results for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            )
        }

        when (val state = uiState) {
            is UnifiedSearchUiState.Loading -> {
                UnifiedSearchShimmer()
            }
            is UnifiedSearchUiState.Error -> {
                SearchErrorView(message = state.message, onRetry = { viewModel.retry() })
            }
            is UnifiedSearchUiState.Success -> {
                SearchSuccessContent(
                    navController = navController,
                    results = state.result
                )
            }
        }
    }
}

@Composable
fun SearchErrorView(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun UnifiedSearchShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxSize()) {
        repeat(8) {
            ListItemPlaceHolder()
        }
    }
}

@Composable
fun SearchSuccessContent(
    navController: NavController,
    results: UnifiedSearchResult
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dynamicCovers = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        SpotifyPlaylistFetcher.hydrateCuratedPlaylists(SpotifyCurated.PLAYLISTS, context) { id, url ->
            dynamicCovers[id] = url
        }
    }

    val tabs = remember { listOf("All", "Songs", "Artists", "Albums", "Playlists") }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    var showImportDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> UnifiedSearchAllTabContent(
                    navController = navController,
                    results = results,
                    dynamicCovers = dynamicCovers,
                    onSongClick = { song ->
                        playerConnection?.playQueue(
                            ListQueue(
                                title = "Search Results: ${results.query}",
                                items = results.songs.map { it.toSaavnMediaMetadata() },
                                startIndex = results.songs.indexOf(song)
                            ),
                            replace = true
                        )
                    },
                    onSeeAllSongs = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    onSeeAllArtists = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    onSeeAllAlbums = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                    onSeeAllPlaylists = { coroutineScope.launch { pagerState.animateScrollToPage(4) } }
                )
                1 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues()
                ) {
                    if (results.songs.isEmpty()) {
                        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No songs found") } }
                    } else {
                        items(results.songs) { song ->
                            SaavnSongRow(
                                song = song,
                                navController = navController,
                                onClick = {
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = "Search Results: ${results.query}",
                                            items = results.songs.map { it.toSaavnMediaMetadata() },
                                            startIndex = results.songs.indexOf(song)
                                        ),
                                        replace = true
                                    )
                                }
                            )
                        }
                    }
                }
                2 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues()
                ) {
                    val validArtists = results.artists.filter { it.name.trim().length > 1 }
                    if (validArtists.isEmpty()) {
                        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No artists found") } }
                    } else {
                        items(validArtists) { artist ->
                            SaavnArtistListItem(
                                artist = artist,
                                onClick = { navController.navigate("artist/${artist.id}") }
                            )
                        }
                    }
                }
                3 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues()
                ) {
                    if (results.albums.isEmpty()) {
                        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No albums found") } }
                    } else {
                        items(results.albums) { album ->
                            SaavnAlbumListItem(
                                album = album,
                                onClick = { navController.navigate("album/${album.id}") }
                            )
                        }
                    }
                }
                4 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues()
                ) {
                    // Import Spotify Link Banner
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clickable { showImportDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1DB954).copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddLink,
                                        contentDescription = null,
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Import Any Playlist",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Paste a Spotify or YouTube link to stream in 320k",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Curated Music Universe Playlists
                    val spotifyList = if (results.spotifyPlaylists.isNotEmpty()) results.spotifyPlaylists else SpotifyCurated.PLAYLISTS.take(8)
                    if (spotifyList.isNotEmpty()) {
                        item {
                            Text(
                                text = "Curated Music Universe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }
                        items(spotifyList.chunked(2)) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (playlist in rowItems) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        CuratedMixCard(
                                            playlist = playlist,
                                            coverUrl = dynamicCovers[playlist.id] ?: playlist.coverArtUrl.ifBlank { SpotifyPlaylistFetcher.getCachedCover(playlist.id, context).orEmpty() },
                                            onClick = {
                                                navController.navigate("spotify/playlist/${playlist.id}")
                                            }
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Saavn Playlists
                    if (results.playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Community Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(results.playlists) { playlist ->
                            SaavnPlaylistListItem(
                                playlist = playlist,
                                onClick = { navController.navigate("playlist/${playlist.id}") }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        SpotifyImportDialog(
            database = database,
            onDismiss = { showImportDialog = false }
        )
    }
}

@Composable
fun UnifiedSearchAllTabContent(
    navController: NavController,
    results: UnifiedSearchResult,
    dynamicCovers: Map<String, String> = emptyMap(),
    onSongClick: (SaavnSong) -> Unit,
    onSeeAllSongs: () -> Unit,
    onSeeAllArtists: () -> Unit,
    onSeeAllAlbums: () -> Unit,
    onSeeAllPlaylists: () -> Unit
) {
    val isEmpty = results.songs.isEmpty() &&
            results.artists.isEmpty() &&
            results.albums.isEmpty() &&
            results.playlists.isEmpty() &&
            results.spotifyPlaylists.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues()
    ) {
        if (isEmpty) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try a different search term",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 1. Top Result
        results.topResult?.let { top ->
            item {
                Text(
                    text = "Top Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                TopResultCard(
                    topResult = top,
                    navController = navController,
                    dynamicCovers = dynamicCovers,
                    onSongClick = onSongClick
                )
            }
        }

        // 2. Songs Section
        if (results.songs.isNotEmpty()) {
            item {
                SectionHeader(title = "Songs", onSeeAllClick = onSeeAllSongs)
                Column(modifier = Modifier.fillMaxWidth()) {
                    results.songs.take(4).forEach { song ->
                        SaavnSongRow(
                            song = song,
                            navController = navController,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }

        // 3. Curated Playlists (Spotify Universe)
        val displaySpotifyPlaylists = if (results.spotifyPlaylists.isNotEmpty()) {
            results.spotifyPlaylists
        } else {
            SpotifyCurated.PLAYLISTS.take(6)
        }
        if (displaySpotifyPlaylists.isNotEmpty()) {
            item {
                SectionHeader(title = "Curated Playlists", onSeeAllClick = onSeeAllPlaylists)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(displaySpotifyPlaylists, key = { it.id }) { playlist ->
                        CuratedMixCard(
                            playlist = playlist,
                            coverUrl = dynamicCovers[playlist.id] ?: playlist.coverArtUrl.ifBlank { SpotifyPlaylistFetcher.getCachedCover(playlist.id).orEmpty() },
                            modifier = Modifier.width(155.dp),
                            onClick = { navController.navigate("spotify/playlist/${playlist.id}") }
                        )
                    }
                }
            }
        }

        // 4. Artists Section
        val validArtists = results.artists.filter { it.name.trim().length > 1 }
        if (validArtists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artists", onSeeAllClick = onSeeAllArtists)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(validArtists.take(8)) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { navController.navigate("artist/${artist.id}") }
                        )
                    }
                }
            }
        }

        // 5. Albums Section
        if (results.albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Albums", onSeeAllClick = onSeeAllAlbums)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(results.albums.take(8)) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { navController.navigate("album/${album.id}") }
                        )
                    }
                }
            }
        }

        // 6. Community Playlists
        if (results.playlists.isNotEmpty()) {
            item {
                SectionHeader(title = "Community Playlists", onSeeAllClick = onSeeAllPlaylists)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(results.playlists.take(8)) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { navController.navigate("playlist/${playlist.id}") }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun TopResultCard(
    topResult: TopResult,
    navController: NavController,
    dynamicCovers: Map<String, String> = emptyMap(),
    onSongClick: (SaavnSong) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        when (topResult) {
            is TopResult.Song -> {
                val song = topResult.song
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(14.dp)
                ) {
                    Box(modifier = Modifier.size(76.dp)) {
                        AsyncImage(
                            model = song.image.takeIf { it.isNotEmpty() },
                            contentDescription = song.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name.decodeHtml(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = song.primaryArtists.decodeHtml().ifBlank { "Song" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "320k Lossless",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (song.duration.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = makeTimeString(song.duration.toLongOrNull()?.times(1000L) ?: 0L),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            is TopResult.Artist -> {
                val artist = topResult.artist
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("artist/${artist.id}") }
                        .padding(14.dp)
                ) {
                    ArtistAvatar(
                        name = artist.name,
                        imageUrl = artist.image,
                        modifier = Modifier.size(76.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name.decodeHtml(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Artist • Tap to view discography",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "View",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            is TopResult.SpotifyPlaylistResult -> {
                val playlist = topResult.playlist
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("spotify/playlist/${playlist.id}") }
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            .background(Brush.linearGradient(playlist.gradientColors.map { Color(it) }))
                    ) {
                        AsyncImage(
                            model = dynamicCovers[playlist.id] ?: playlist.coverArtUrl.ifBlank { SpotifyPlaylistFetcher.getCachedCover(playlist.id) },
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = playlist.subtitle.ifBlank { "Curated Mix" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFF1DB954).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Curated Mix",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1DB954)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1DB954),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            is TopResult.Album -> {
                val album = topResult.album
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("album/${album.id}") }
                        .padding(14.dp)
                ) {
                    AsyncImage(
                        model = album.image.takeIf { it.isNotEmpty() },
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.name.decodeHtml(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Album • ${album.artists.decodeHtml().ifBlank { "Various" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            is TopResult.Playlist -> {
                val playlist = topResult.playlist
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("playlist/${playlist.id}") }
                        .padding(14.dp)
                ) {
                    AsyncImage(
                        model = playlist.image.takeIf { it.isNotEmpty() },
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name.decodeHtml(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Community Playlist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSeeAllClick) {
            Text("See all", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ArtistCard(artist: SaavnArtist, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        ArtistAvatar(
            name = artist.name,
            imageUrl = artist.image,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name.decodeHtml(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumCard(album: SaavnAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = album.image.takeIf { it.isNotEmpty() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name.decodeHtml(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artists.decodeHtml().ifBlank { "Various" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlaylistCard(playlist: SaavnPlaylist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = playlist.image.takeIf { it.isNotEmpty() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.name.decodeHtml(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
