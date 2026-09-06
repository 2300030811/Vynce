package com.vynce.app.ui.screens.search

import com.vynce.app.extensions.decodeHtml

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.LocalDatabase
import com.vynce.app.LocalMenuState
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.LocalSnackbarHostState
import com.vynce.app.R
import com.vynce.app.constants.SuggestionItemHeight
import com.vynce.app.constants.SwipeToQueueKey
import com.vynce.app.data.spotify.SpotifyCurated
import com.vynce.app.data.spotify.SpotifyCuratedCategory
import com.vynce.app.data.spotify.SpotifyCuratedPlaylist
import com.vynce.app.data.spotify.SpotifyPlaylistFetcher
import com.vynce.app.extensions.toMediaItem
import com.vynce.app.extensions.togglePlayPause
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.LazyColumnScrollbar
import com.vynce.app.ui.component.SearchBarIconOffsetX
import com.vynce.app.ui.component.SwipeToQueueBox
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.component.items.SaavnSongListItem
import com.vynce.app.ui.screens.library.SpotifyImportDialog
import com.vynce.app.ui.theme.VyncePurple
import com.vynce.app.utils.rememberPreference
import com.vynce.app.utils.toSaavnMediaMetadata
import com.vynce.app.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

@OptIn(FlowPreview::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val queueSearchedSongsText = stringResource(R.string.queue_searched_songs)
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()

    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val viewState by viewModel.viewState.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = LocalSnackbarHostState.current

    var selectedCategory by rememberSaveable { mutableStateOf("all") }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    val dynamicCovers = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        SpotifyPlaylistFetcher.hydrateCuratedPlaylists(SpotifyCurated.PLAYLISTS, context) { playlistId, coverUrl ->
            dynamicCovers[playlistId] = coverUrl
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce { 300L }.collectLatest {
            viewModel.query.value = query
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start).asPaddingValues(),
    ) {
        if (query.isBlank()) {
            // ── 1. RECENT SEARCHES (IF ANY) ──────────────────────────────────
            if (viewState.history.isNotEmpty()) {
                item(key = "search_history_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                database.query {
                                    clearSearchHistory()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                item(key = "search_history_chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(viewState.history, key = { it.query }) { history ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                modifier = Modifier.clickable {
                                    onSearch(history.query)
                                    onDismiss()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = history.query,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            database.query {
                                                delete(history)
                                            }
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. HERO BANNER (EXPLORE UNIVERSE) ────────────────────────────
            item(key = "explore_universe_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF221C38),
                                    Color(0xFF14121F),
                                    Color(0xFF100F17)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = null,
                                tint = VyncePurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CURATED MUSIC UNIVERSE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = VyncePurple,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Explore Universe",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Browse top charts, viral soundscapes, moods, and curated mixes. Tap any mix to stream in 320k lossless audio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA09EB2),
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = { showImportDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = null,
                                tint = VyncePurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Import Playlist Link",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── 3. CATEGORY FILTER CHIPS ─────────────────────────────────────
            item(key = "category_filter_chips") {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BROWSE CATEGORIES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                        val count = if (selectedCategory == "all") SpotifyCurated.PLAYLISTS.size else SpotifyCurated.PLAYLISTS.count { it.category == selectedCategory }
                        Text(
                            text = "$count Collections",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SpotifyCurated.CATEGORIES, key = { it.id }) { category ->
                            val isSelected = selectedCategory == category.id
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) VyncePurple.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier.clickable { selectedCategory = category.id }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = category.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    category.badge?.let { badge ->
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else VyncePurple.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = badge.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else VyncePurple,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. CURATED MIXES PRESENTATION ────────────────────────────────
            if (selectedCategory != "all") {
                // Single Category Grid View
                val playlists = SpotifyCurated.PLAYLISTS.filter { it.category == selectedCategory }
                val currentCategory = SpotifyCurated.CATEGORIES.find { it.id == selectedCategory }

                item(key = "category_header_$selectedCategory") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            text = currentCategory?.label ?: "Category",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentCategory?.description ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(playlists.chunked(2), key = { chunk -> chunk.joinToString { it.id } }) { rowItems ->
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
                                    coverUrl = dynamicCovers[playlist.id] ?: playlist.coverArtUrl,
                                    onClick = {
                                        navController.navigate("spotify/playlist/${playlist.id}")
                                        onDismiss()
                                    }
                                )
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // "All Mixes" Grouped Category Carousels
                val activeCategories = SpotifyCurated.CATEGORIES.filter { it.id != "all" }
                for (cat in activeCategories) {
                    val playlists = SpotifyCurated.PLAYLISTS.filter { it.category == cat.id }
                    if (playlists.isNotEmpty()) {
                        item(key = "category_section_${cat.id}") {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = cat.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            cat.badge?.let { badge ->
                                                Surface(
                                                    color = Color(0xFF1DB954).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = badge,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1DB954)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = cat.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        CuratedMixCard(
                                            playlist = playlist,
                                            coverUrl = dynamicCovers[playlist.id] ?: playlist.coverArtUrl,
                                            modifier = Modifier.width(155.dp),
                                            onClick = {
                                                navController.navigate("spotify/playlist/${playlist.id}")
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "curated_bottom_spacer") {
                Spacer(Modifier.height(40.dp))
            }

        } else {
            // ── QUERY IS ACTIVE: LIVE SEARCH SUGGESTIONS & RESULTS ───────────
            items(
                items = viewState.history,
                key = { it.query }
            ) { history ->
                SuggestionItem(
                    query = history.query,
                    online = false,
                    onClick = {
                        onSearch(history.query)
                        onDismiss()
                    },
                    onDelete = {
                        database.query {
                            delete(history)
                        }
                    },
                    onFillTextField = {
                        onQueryChange(
                            TextFieldValue(
                                text = history.query,
                                selection = TextRange(history.query.length)
                            )
                        )
                    },
                    modifier = Modifier.animateItem()
                )
            }

            items(
                items = viewState.suggestions,
                key = { it }
            ) { querySuggestion ->
                val decodedQuery = querySuggestion.decodeHtml()
                SuggestionItem(
                    query = decodedQuery,
                    online = true,
                    onClick = {
                        onSearch(decodedQuery)
                        onDismiss()
                    },
                    onFillTextField = {
                        onQueryChange(
                            TextFieldValue(
                                text = decodedQuery,
                                selection = TextRange(decodedQuery.length)
                            )
                        )
                    },
                    modifier = Modifier.animateItem()
                )
            }

            if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
                item {
                    HorizontalDivider()
                }
            }

            items(
                items = viewState.items,
                key = { it.id }
            ) { item ->
                val content: @Composable () -> Unit = {
                    SaavnSongListItem(
                        song = item,
                        navController = navController,
                        onPlay = {
                            if (item.id == mediaMetadata?.id?.removePrefix("saavn:")) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                val saavnSongs = viewState.items
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = queueSearchedSongsText,
                                        items = saavnSongs.map { it.toSaavnMediaMetadata() },
                                        startIndex = saavnSongs.indexOf(item)
                                    ),
                                    replace = true,
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                SwipeToQueueBox(
                    item = item.toMediaItem(),
                    swipeEnabled = swipeEnabled,
                    snackbarHostState = snackbarHostState,
                    content = { content() },
                )
            }
        }
    }

    LazyColumnScrollbar(
        state = lazyListState,
    )

    if (showImportDialog) {
        SpotifyImportDialog(
            database = database,
            onDismiss = { showImportDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun CuratedMixCard(
    playlist: SpotifyCuratedPlaylist,
    coverUrl: String = playlist.coverArtUrl,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        Brush.linearGradient(
                            playlist.gradientColors.map { Color(it) }
                        )
                    )
            ) {
                AsyncImage(
                    model = coverUrl.ifBlank { playlist.coverArtUrl.ifBlank { SpotifyPlaylistFetcher.getCachedCover(playlist.id) } },
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = playlist.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1DB954),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "320k",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = playlist.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(SuggestionItemHeight)
            .clickable(onClick = onClick)
            .padding(end = SearchBarIconOffsetX)
    ) {
        Icon(
            if (online) Icons.Rounded.Search else Icons.Rounded.History,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .alpha(0.5f)
        )

        Text(
            text = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (!online) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(0.5f)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null
                )
            }
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(0.5f)
        ) {
            Icon(
                Icons.Rounded.ArrowOutward,
                contentDescription = null
            )
        }
    }
}
