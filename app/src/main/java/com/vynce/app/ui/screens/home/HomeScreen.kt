package com.vynce.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.R
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.ui.screens.Screens
import com.vynce.app.utils.playJioSaavnSong
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.models.MediaMetadata
import com.vynce.app.models.toMediaMetadata
import com.vynce.app.utils.saavnHighResHttps
import java.util.Calendar
import kotlinx.coroutines.launch
import android.widget.Toast
import com.vynce.app.ui.component.shimmer.*
import com.vynce.app.ui.utils.shimmer
import com.vynce.app.ui.component.AiPlaylistDialog
import com.vynce.app.viewmodels.AiPlaylistViewModel
import com.vynce.app.utils.rememberPreference
import com.vynce.app.constants.AiEnabledKey

import com.vynce.app.ui.component.ScrollToTopManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    playerConnection: PlayerConnection?,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val (aiEnabled, _) = rememberPreference(key = AiEnabledKey, defaultValue = false)
    val lazyListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    var showAiDialog by remember { mutableStateOf(false) }
    var showPersonalitySheet by remember { mutableStateOf(false) }

    ScrollToTopManager(navController, lazyListState)

    // ponytail: Expand top app bar / search bar whenever user is at top of home screen to avoid blank space gap
    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(isAtTop) {
        if (isAtTop) {
            scrollBehavior?.state?.heightOffset = 0f
            scrollBehavior?.state?.contentOffset = 0f
        }
    }

    if (!aiEnabled) {
        showAiDialog = false
    }

    fun playStatsSong(songId: String, title: String?, artist: String?, thumbnail: String?) {
        val metadata = MediaMetadata(
            id = songId,
            title = title ?: "Unknown Title",
            artists = listOf(MediaMetadata.Artist(null, artist ?: "Unknown Artist")),
            thumbnailUrl = thumbnail,
            duration = -1,
            album = null,
            genre = null
        )
        playerConnection?.playQueue(ListQueue(title = title ?: "", items = listOf(metadata)))
    }

    if (aiEnabled && showAiDialog) {
        AiDialogWrapper(
            onDismiss = { showAiDialog = false },
            playerConnection = playerConnection
        )
    }

    if (showPersonalitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPersonalitySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val hasSpace = state.personaChip.contains(" ")
            val emoji = if (hasSpace) state.personaChip.substringBefore(" ") else "🎧"
            val title = if (hasSpace) state.personaChip.substringAfter(" ") else state.personaChip

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = state.personaDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        showPersonalitySheet = false
                        navController.navigate(Screens.Stats.route)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Detailed Insights")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            val showFab by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 3 } }
            val fabScope = rememberCoroutineScope()
            AnimatedVisibility(visible = showFab, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                FloatingActionButton(
                    onClick = {
                        fabScope.launch {
                            lazyListState.animateScrollToItem(0)
                            scrollBehavior?.state?.heightOffset = 0f
                            scrollBehavior?.state?.contentOffset = 0f
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 16.dp
                    )
                ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Scroll to top") }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
                .pullToRefresh(isRefreshing = state.isLoading, state = pullToRefreshState, onRefresh = { viewModel.loadAll() })
        ) {
            LazyColumn(
                state = lazyListState, 
                modifier = Modifier.fillMaxSize(), 
                contentPadding = PaddingValues(
                    // ponytail: snug up home screen top spacing below the search bar
                    top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 56.dp,
                    bottom = 120.dp
                )
            ) {

                // ── GREETING ────────────────────────────────────
                item(key = "greeting") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
                    ) {
                        Column {
                            Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                onClick = { showPersonalitySheet = true }
                            ) {
                                Text(
                                    text = state.personaChip,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            
                            Spacer(Modifier.height(8.dp))
                            
                            val subtitle = if (state.continueListening.isNotEmpty()) {
                                "▶ Resume ${state.continueListening.first().title}"
                            } else if (state.totalPlayCount > 0) {
                                "Ready to vibe with ${state.totalPlayCount} songs today?"
                            } else {
                                stringResource(R.string.ready_to_vibe_today)
                            }
                            
                            val subtitleModifier = if (state.continueListening.isNotEmpty()) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val first = state.continueListening.first()
                                        playStatsSong(first.songId, first.title, first.artist, first.thumbnail)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                                    .offset(x = (-8).dp)
                            } else {
                                Modifier
                            }
                            
                            val subtitleColor = if (state.continueListening.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(modifier = subtitleModifier, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = subtitle, 
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = subtitleColor
                                )
                            }
                        }
                    }
                }                // ── QUICK ACTIONS ───────────────────────────────
                item(key = "quick_actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(Icons.Rounded.History, "History", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) {
                            if (navController.graph.findNode(Screens.History.route) != null) navController.navigate(Screens.History.route)
                            else Toast.makeText(context, "History not available", Toast.LENGTH_SHORT).show()
                        }
                        QuickActionCard(Icons.Rounded.Favorite, "Liked", MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                            if (navController.graph.findNode(Screens.Liked.route) != null) navController.navigate(Screens.Liked.route)
                            else Toast.makeText(context, "Liked not available", Toast.LENGTH_SHORT).show()
                        }
                        QuickActionCard(Icons.Rounded.LibraryMusic, "Local", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                            if (navController.graph.findNode(Screens.Songs.route) != null) navController.navigate(Screens.Songs.route)
                            else Toast.makeText(context, "Local not available", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // ── CONTINUE LISTENING ───────────────────────────
                if (state.continueListening.isNotEmpty()) {
                    item(key = "continue_listening_header") { SectionHeader("Continue Listening") }
                    item(key = "continue_listening_row") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(state.continueListening, key = { it.timestamp.toString() + it.songId }) { entry ->
                                HistoryEntryCard(entry) {
                                    playStatsSong(entry.songId, entry.title, entry.artist, entry.thumbnail)
                                }
                            }
                        }
                    }
                }

                // ── ON REPEAT ───────────────────────────────────
                if (state.topSongs.isNotEmpty()) {
                    item(key = "on_repeat_header") {
                        SectionHeader("On Repeat")
                        Text(
                            text = "Your most played songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp).offset(y = (-8).dp)
                        )
                    }
                    item(key = "on_repeat_row") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(state.topSongs, key = { it.songId }) { entry ->
                                OnRepeatSongCard(entry) {
                                    playStatsSong(entry.songId, entry.title, entry.artist, entry.albumArtUri)
                                }
                            }
                        }
                    }
                }

                // ── TOP ARTISTS ────────────────────────────
                val hasPersonalizedArtists = state.topStatsArtists.isNotEmpty()
                val artistsTitle = if (hasPersonalizedArtists) "Your Top Artists" else "Top Artists"

                if (state.featuredArtists.isNotEmpty()) {
                    item(key = "artists_header") { SectionHeader(artistsTitle) }
                    item(key = "artists_row") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.featuredArtists, key = { it.id }) { artist ->
                                ArtistCircleCard(artist) { navController.navigate("artist/${artist.id}") }
                            }
                        }
                    }
                } else if (state.isLoading) {
                    item(key = "artists_shimmer_h") { SectionHeader("Top Artists") }
                    item(key = "artists_shimmer") {
                        ShimmerHost {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(6, key = { it }) {
                                    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(96.dp).clip(CircleShape).background(placeholderColor))
                                        Spacer(Modifier.height(8.dp))
                                        Box(Modifier.width(56.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(placeholderColor))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── REDISCOVER ───────────────────────────────────
                if (state.rediscover.isNotEmpty()) {
                    item(key = "rediscover_header") {
                        SectionHeader("Rediscover Favorites")
                        Text(
                            text = "Songs you haven't played in a while",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp).offset(y = (-8).dp)
                        )
                    }
                    item(key = "rediscover_row") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(state.rediscover, key = { it.songId }) { entry ->
                                RediscoverSongCard(entry) { }
                            }
                        }
                    }
                }

                // ── AI PLAYLIST BANNER ────────────────────────
                if (aiEnabled) {
                    item(key = "ai_playlist_banner") {
                        Surface(
                            onClick = { showAiDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .height(84.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "✨ Generate AI Playlist",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Type a vibe, get a mix instantly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                // ── DYNAMIC SECTIONS ───────────────────────────
                if (state.isLoading && state.sections.isEmpty()) {
                    item { HomeShimmer() }
                } else {
                    state.sections.forEachIndexed { index, section ->
                        item(key = "sec_header_$index") {
                            SectionHeader(title = section.title, showSeeAll = false)
                        }
                        item(key = "sec_row_$index") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                when (section) {
                                    is HomeSection.LocalSongSection -> {
                                        items(section.songs, key = { it.id }) { song ->
                                            LocalSongCard(song) { 
                                                playerConnection?.playQueue(
                                                    ListQueue(
                                                        title = song.title,
                                                        items = listOf(song.toMediaMetadata())
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    is HomeSection.SongSection -> {
                                        items(section.songs, key = { it.id }) { song ->
                                            SongCard(song) { playJioSaavnSong(song, playerConnection) }
                                        }
                                    }
                                    is HomeSection.AlbumSection -> {
                                        items(section.albums, key = { it.id }) { album ->
                                            AlbumCard(album) { navController.navigate("album/${album.id}") }
                                        }
                                    }
                                    is HomeSection.PlaylistSection -> {
                                        items(section.playlists, key = { it.id }) { playlist ->
                                            PlaylistCard(playlist) { navController.navigate("playlist/${playlist.id}") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── GLOBAL ERROR ────────────────────────────────
                if (state.error != null && !state.isLoading && state.sections.isEmpty()) {
                    item(key = "global_error") { GlobalErrorCard(state.error) { viewModel.loadAll() } }
                }
            }

            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState, isRefreshing = state.isLoading,
                modifier = Modifier.align(Alignment.TopCenter), color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AiDialogWrapper(
    onDismiss: () -> Unit,
    playerConnection: PlayerConnection?
) {
    val aiViewModel: AiPlaylistViewModel = hiltViewModel()
    AiPlaylistDialog(
        viewModel = aiViewModel,
        onDismiss = {
            onDismiss()
            aiViewModel.clearPlaylist()
        },
        onPlaylistGenerated = {
            val generatedSongs = aiViewModel.generatedPlaylist.value.distinctBy { it.song.id }
            if (generatedSongs.isNotEmpty()) {
                playerConnection?.playQueue(
                    ListQueue(
                        title = "AI Playlist",
                        items = generatedSongs.map { it.toMediaMetadata() }
                    ),
                    replace = true
                )
            }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════
// COMPONENTS
// ══════════════════════════════════════════════════════════════════════

fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5  -> "Good Night, Late Owl 👋"
        hour < 12 -> "Good Morning 👋"
        hour < 17 -> "Good Afternoon 👋"
        hour < 21 -> "Good Evening 👋"
        else      -> "Good Night 👋"
    }
}

/** Big category divider — e.g. "🔥 Trending" */
@Composable
fun CategoryHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 6.dp),
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** Playlist row header with cover thumbnail + name + See All */
@Composable
fun PlaylistSectionHeader(name: String, imageUrl: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl.saavnHighResHttps())
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("See all", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, showSeeAll: Boolean = false, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        if (showSeeAll && onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("See all", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SongCard(song: com.vynce.jiosaavn.SaavnSong, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(150.dp)
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.image.saavnHighResHttps()).build(),
                    placeholder = ColorPainter(Color(0xFF1A1A2E)),
                    fallback = ColorPainter(Color(0xFF1A1A2E)),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient overlay at bottom
                Box(
                    Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(song.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(song.primaryArtists, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
fun ArtistCircleCard(artist: com.vynce.jiosaavn.SaavnArtist, onClick: () -> Unit) {
    Column(modifier = Modifier.width(96.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, modifier = Modifier.size(96.dp), tonalElevation = 4.dp, shadowElevation = 4.dp) {
            val colors = listOf(Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2),
                Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFA726))
            val bgColor = colors[artist.name.length % colors.size]
            Box(Modifier.fillMaxSize()) {
                ArtistLetterAvatar(artist.name, bgColor)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artist.image.saavnHighResHttps()).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(artist.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun QuickActionCard(icon: ImageVector, label: String, contentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = contentColor)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun ArtistLetterAvatar(name: String, bgColor: Color) {
    Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// ── Shimmer / Error states ──────────────────────────────────────────

// Shimmer / Error states moved to ShimmerComponents.kt

@Composable
fun SectionErrorCard(title: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun GlobalErrorCard(error: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Rounded.CloudOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Text("Couldn't load content", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(error ?: "Check your connection and try again", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Retry")
        }
    }
}

@Composable
fun LocalSongCard(song: com.vynce.app.db.entities.Song, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(150.dp)
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.thumbnailUrl).build(),
                    placeholder = ColorPainter(Color(0xFF1A1A2E)),
                    fallback = ColorPainter(Color(0xFF1A1A2E)),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(song.artists.joinToString { it.name }.takeIf { it.isNotEmpty() } ?: "Unknown Artist", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
fun AlbumCard(album: com.vynce.jiosaavn.SaavnAlbumInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(150.dp)
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.image.saavnHighResHttps()).build(),
                    placeholder = ColorPainter(Color(0xFF1A1A2E)),
                    fallback = ColorPainter(Color(0xFF1A1A2E)),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
fun PlaylistCard(playlist: com.vynce.jiosaavn.SaavnPlaylistInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(150.dp)
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(playlist.image.saavnHighResHttps()).build(),
                    placeholder = ColorPainter(Color(0xFF1A1A2E)),
                    fallback = ColorPainter(Color(0xFF1A1A2E)),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
fun StatsArtistCircleCard(artist: com.vynce.app.data.stats.PlaybackStatsRepository.ArtistPlaybackSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.width(96.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, modifier = Modifier.size(96.dp), tonalElevation = 4.dp, shadowElevation = 4.dp) {
            val colors = listOf(Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2),
                Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFA726))
            val bgColor = colors[artist.artist.length % colors.size]
            ArtistLetterAvatar(artist.artist, bgColor)
        }
        Spacer(Modifier.height(8.dp))
        Text(artist.artist, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun HistoryEntryCard(entry: com.vynce.app.data.stats.PlaybackStatsRepository.PlaybackHistoryEntry, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(150.dp)
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.thumbnail?.saavnHighResHttps()).build(),
                    placeholder = ColorPainter(Color(0xFF1A1A2E)),
                    fallback = ColorPainter(Color(0xFF1A1A2E)),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(entry.title ?: "Unknown", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(entry.artist ?: "Unknown Artist", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
fun OnRepeatSongCard(entry: com.vynce.app.data.stats.PlaybackStatsRepository.SongPlaybackSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.width(110.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.size(110.dp)
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.albumArtUri?.saavnHighResHttps()).build(),
                    placeholder = ColorPainter(Color(0xFF1A1A2E)),
                    fallback = ColorPainter(Color(0xFF1A1A2E)),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("🔥 ${entry.playCount} plays", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(entry.artist, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
fun RediscoverSongCard(entry: com.vynce.app.data.stats.PlaybackStatsRepository.SongPlaybackSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(150.dp)
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.albumArtUri?.saavnHighResHttps()).build(),
                    placeholder = ColorPainter(Color(0xFF1A1A2E)),
                    fallback = ColorPainter(Color(0xFF1A1A2E)),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(entry.artist, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}
