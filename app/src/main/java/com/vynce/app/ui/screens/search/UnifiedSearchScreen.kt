package com.vynce.app.ui.screens.search

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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.R
import com.vynce.app.constants.ListThumbnailSize
import com.vynce.app.constants.ThumbnailCornerRadius
import com.vynce.app.models.TopResult
import com.vynce.app.models.UnifiedSearchResult
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.component.items.*
import com.vynce.app.ui.component.shimmer.ListItemPlaceHolder
import com.vynce.app.ui.component.shimmer.ShimmerHost
import com.vynce.app.utils.playJioSaavnSong
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
    val coroutineScope = rememberCoroutineScope()
    val tabs = remember { listOf("All", "Songs", "Artists", "Albums", "Playlists") }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val playerConnection = LocalPlayerConnection.current

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
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> UnifiedSearchAllTabContent(
                    navController = navController,
                    results = results,
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
                    if (results.artists.isEmpty()) {
                        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No artists found") } }
                    } else {
                        items(results.artists) { artist ->
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
                    if (results.playlists.isEmpty()) {
                        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No playlists found") } }
                    } else {
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
}

@Composable
fun UnifiedSearchAllTabContent(
    navController: NavController,
    results: UnifiedSearchResult,
    onSongClick: (SaavnSong) -> Unit,
    onSeeAllSongs: () -> Unit,
    onSeeAllArtists: () -> Unit,
    onSeeAllAlbums: () -> Unit,
    onSeeAllPlaylists: () -> Unit
) {
    val isEmpty = results.songs.isEmpty() &&
            results.artists.isEmpty() &&
            results.albums.isEmpty() &&
            results.playlists.isEmpty()

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
                    onSongClick = onSongClick
                )
            }
        }

        // 2. Songs Section
        if (results.songs.isNotEmpty()) {
            item {
                SectionHeader(title = "Songs", onSeeAllClick = onSeeAllSongs)
                Column(modifier = Modifier.fillMaxWidth()) {
                    results.songs.take(3).forEach { song ->
                        SaavnSongRow(
                            song = song,
                            navController = navController,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }

        // 3. Artists Section
        if (results.artists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artists", onSeeAllClick = onSeeAllArtists)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(results.artists.take(8)) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { navController.navigate("artist/${artist.id}") }
                        )
                    }
                }
            }
        }

        // 4. Albums Section
        if (results.albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Albums", onSeeAllClick = onSeeAllAlbums)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
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

        // 5. Playlists Section
        if (results.playlists.isNotEmpty()) {
            item {
                SectionHeader(title = "Playlists", onSeeAllClick = onSeeAllPlaylists)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
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
    onSongClick: (SaavnSong) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        when (topResult) {
            is TopResult.Artist -> {
                val artist = topResult.artist
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("artist/${artist.id}") }
                        .padding(16.dp)
                ) {
                    AsyncImage(
                        model = artist.image.takeIf { it.isNotEmpty() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Artist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            is TopResult.Song -> {
                val song = topResult.song
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(16.dp)
                ) {
                    AsyncImage(
                        model = song.image.takeIf { it.isNotEmpty() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Song • ${song.primaryArtists}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        .padding(16.dp)
                ) {
                    AsyncImage(
                        model = album.image.takeIf { it.isNotEmpty() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Album • ${album.artists}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
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
                        .padding(16.dp)
                ) {
                    AsyncImage(
                        model = playlist.image.takeIf { it.isNotEmpty() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Playlist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
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
            .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSeeAllClick) {
            Text("See all")
        }
    }
}

@Composable
fun ArtistCard(artist: SaavnArtist, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = artist.image.takeIf { it.isNotEmpty() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
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
            .width(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = album.image.takeIf { it.isNotEmpty() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artists.takeIf { it.isNotEmpty() } ?: "Various",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlaylistCard(playlist: SaavnPlaylist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = playlist.image.takeIf { it.isNotEmpty() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
