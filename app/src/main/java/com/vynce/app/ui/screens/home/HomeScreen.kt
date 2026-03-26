package com.vynce.app.ui.screens.home

import android.graphics.drawable.ColorDrawable

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.vynce.app.R
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.ui.screens.Screens
import com.vynce.app.utils.playJioSaavnSong
import com.vynce.app.ui.screens.saavn.playAllSongs
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnArtistInfo
import com.zionhuang.jiosaavn.SaavnAlbumInfo
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    playerConnection: PlayerConnection?,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val languages = listOf("All", "हिंदी", "English", "Punjabi", "Telugu", "Tamil", "Marathi", "Malayalam")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.vynce_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Vynce",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ── GREETING HEADER ─────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = greeting(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ready to vibe today?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── QUICK ACTIONS ───────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Rounded.History,
                        label = "History",
                        contentColor = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    ) { 
                        if (navController.graph.findNode(Screens.History.route) != null) {
                            navController.navigate(Screens.History.route) 
                        }
                    }

                    QuickActionCard(
                        icon = Icons.Rounded.Favorite,
                        label = "Liked",
                        contentColor = Color(0xFFE91E63),
                        modifier = Modifier.weight(1f)
                    ) { 
                        if (navController.graph.findNode(Screens.Liked.route) != null) {
                            navController.navigate(Screens.Liked.route) 
                        }
                    }

                    QuickActionCard(
                        icon = Icons.Rounded.LibraryMusic,
                        label = "Local",
                        contentColor = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f)
                    ) { 
                        if (navController.graph.findNode(Screens.Songs.route) != null) {
                            navController.navigate(Screens.Songs.route) 
                        }
                    }

                    QuickActionCard(
                        icon = Icons.Rounded.BarChart,
                        label = "Stats",
                        contentColor = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    ) { 
                        if (navController.graph.findNode(Screens.Stats.route) != null) {
                            navController.navigate(Screens.Stats.route) 
                        }
                    }
                }
            }

            // ── LANGUAGE CHIPS ───────────────────────────────
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(languages) { lang ->
                        FilterChip(
                            selected = state.selectedLanguage == lang,
                            onClick = { viewModel.setLanguage(lang) },
                            label = { Text(lang) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = null
                        )
                    }
                }
            }

            // ── FEATURED ARTISTS ─────────────────────────────
            if (state.featuredArtists.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Top Artists",
                        onSeeAll = null
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.featuredArtists, key = { it.id }) { artist ->
                            ArtistCircleCard(
                                artist = artist,
                                onClick = { navController.navigate("artist/${artist.id}") }
                            )
                        }
                    }
                }
            }

            // ── MUSIC SECTIONS ──
            state.sections.forEachIndexed { index, section ->
                if (section.isLoading) {
                    item(key = "shimmer_$index") {
                        SectionShimmer(title = "${section.emoji} ${section.title}")
                    }
                } else if (section.songs.isNotEmpty()) {
                    item(key = "title_$index") {
                        SectionHeader(
                            title = "${section.emoji} ${section.title}",
                            showSeeAll = section.playlistInfo?.id != null,
                            onSeeAll = section.playlistInfo?.id?.let { id ->
                                { navController.navigate("playlist/$id") }
                            }
                        )
                    }
                    item(key = "row_$index") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(section.songs.take(15), key = { it.id }) { song ->
                                SongCard(
                                    song = song,
                                    onClick = { playJioSaavnSong(song, playerConnection) }
                                )
                            }
                        }
                    }
                }
            }

            // ── NEW ALBUMS ───────────────────────────────────
            if (state.newAlbums.isNotEmpty()) {
                item { SectionHeader("💿 New Albums", onSeeAll = null) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.newAlbums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { navController.navigate("album/${album.id}") }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── COMPONENTS ───────────────────────────────────────────

@Composable
fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val appName = "Vynce"
    return when {
        hour < 5  -> "Good Night, Late Owl"
        hour < 12 -> "Good Morning! ☀️"
        hour < 17 -> "Good Afternoon"
        hour < 21 -> "Good Evening"
        else      -> "Good Night"
    }
}

@Composable
fun SectionHeader(title: String, showSeeAll: Boolean = false, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (showSeeAll && onSeeAll != null) {
            TextButton(
                onClick = onSeeAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("See all", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SongCard(song: com.zionhuang.jiosaavn.SaavnSong, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.size(150.dp)
        ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.image.replace("http://", "https://").replace("150x150", "500x500"))
                .placeholder(ColorDrawable(0xFF1A1A2E.toInt()))
                .fallback(ColorDrawable(0xFF1A1A2E.toInt()))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = song.name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = song.primaryArtists,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    }
}

@Composable
fun ArtistCircleCard(artist: com.zionhuang.jiosaavn.SaavnArtist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(100.dp),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            val colorList = listOf(
                Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), 
                Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF42A5F5),
                Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65),
                Color(0xFFFFA726), Color(0xFFFF7043)
            )
            val bgColor = colorList[artist.name.length % colorList.size]

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.image.replace("http://", "https://").replace("150x150", "500x500"))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ArtistLetterAvatar(artist.name, bgColor)
                },
                error = {
                    ArtistLetterAvatar(artist.name, bgColor)
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AlbumCard(album: com.zionhuang.jiosaavn.SaavnAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.size(150.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.image.replace("http://", "https://").replace("150x150", "500x500"))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${album.artists} • ${album.year}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(contentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

@Composable
fun SectionShimmer(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(5) {
                val alpha by rememberInfiniteTransition(label = "shimmer")
                    .animateFloat(
                        initialValue = 0.2f, targetValue = 0.5f,
                        animationSpec = infiniteRepeatable(
                            tween(1000, easing = LinearEasing), RepeatMode.Reverse
                        ), label = "shimmer"
                    )
                Column(modifier = Modifier.width(150.dp)) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistLetterAvatar(name: String, bgColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}