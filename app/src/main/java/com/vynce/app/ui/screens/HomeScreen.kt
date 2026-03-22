package com.vynce.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.utils.playJioSaavnSong
import com.vynce.app.viewmodels.HomeState
import com.vynce.app.viewmodels.HomeViewModel
import com.zionhuang.jiosaavn.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
        // ── HEADER ──────────────────────────────────────
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${state.greeting},", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Vynce", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── QUICK ACTIONS ────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(Icons.Rounded.History, "History", "history"),
                    Triple(Icons.Rounded.BarChart, "Stats", Screens.Stats.route),
                    Triple(Icons.Rounded.FolderOpen, "Folders", Screens.Folders.route),
                    Triple(Icons.Rounded.AudioFile, "Local", Screens.Songs.route)
                ).forEach { (icon, label, route) ->
                    QuickActionChip(icon, label, modifier = Modifier.weight(1f)) { 
                        navController.navigate(route) 
                    }
                }
            }
        }

        // ── MOOD CHIPS ───────────────────────────────────
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val moods = listOf("Trending","Romantic","Party","Lofi","Bollywood","Tollywood","English","Punjabi")
                items(moods) { mood ->
                    FilterChip(
                        selected = false, 
                        onClick = { navController.navigate("search/$mood") }, 
                        label = { Text(mood) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // ── HERO SECTION ─────────────────────────────────
        state.heroSong?.let { heroSong ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { playJioSaavnSong(heroSong, playerConnection) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(heroSong.image.replace("http://","https://"))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    startY = 100f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = heroSong.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = heroSong.primaryArtists,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── SECTIONS ─────────────────────────────────────
        val sectionData = listOf(
            Triple("Tollywood Hits", state.tollywood, "tollywood hits 2025 telugu"),
            // Artists & English Charts injected below
            Triple("New Releases", state.newReleases, "new hindi songs 2025"),
            Triple("Trending Now", state.trending, "trending hindi songs"),
            // Albums injected below
            Triple("Bollywood Hits", state.bollywood, "bollywood hits 2025"),
            Triple("Romantic Hits", state.romantic, "romantic hindi love songs"),
            Triple("Lofi & Chill", state.lofi, "lofi chill hindi"),
            Triple("Party Anthems", state.party, "party dance hits hindi 2025"),
            Triple("Punjabi Hits", state.punjabi, "punjabi hits 2025"),
            Triple("Top Picks", state.topPicks, "top picks india 2025")
        )

        sectionData.forEachIndexed { index, (title, list, query) ->
            if (list.isNotEmpty()) {
                item { 
                    SectionHeader(
                        title = title, 
                        showSeeAll = index % 2 == 0,
                        onSeeAll = { navController.navigate("search/$query") }
                    ) 
                }
                item { 
                    val cardWidth = if (title in listOf("New Releases", "Trending Now", "Top Picks")) 160.dp else 150.dp
                    HorizontalPlaylistRow(list, cardWidth = cardWidth) { 
                        navController.navigate("search/${it.name}") 
                    } 
                }
            }
            
            // Reordered Injections:
            if (index == 0 && state.topArtists.isNotEmpty()) {
                item { SectionHeader("Top Artists", showSeeAll = true, onSeeAll = { navController.navigate("search/top hindi artists") }) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.topArtists) { artist -> ArtistCard(artist) }
                    }
                }
                
                // English right after artists
                if (state.english.isNotEmpty()) {
                    item { SectionHeader("English Charts", showSeeAll = true, onSeeAll = { navController.navigate("search/english pop hits 2025") }) }
                    item { HorizontalPlaylistRow(state.english) { navController.navigate("search/${it.name}") } }
                }
            }
            if (index == 2 && state.featuredAlbums.isNotEmpty()) {
                item { SectionHeader("Featured Albums", showSeeAll = true, onSeeAll = { navController.navigate("search/best hindi albums 2025") }) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.featuredAlbums) { album -> AlbumCard(album) { navController.navigate("search/${album.name}") } }
                    }
                }
            }
        }

        // ── LOADING ──────────────────────────────────────
        if (state.isLoading && state.heroSong == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
fun SectionHeader(title: String, showSeeAll: Boolean = false, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (showSeeAll) {
            Text(
                text = "See all →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSeeAll?.invoke() }
            )
        }
    }
}

@Composable
fun HorizontalPlaylistRow(playlists: List<SaavnPlaylist>, cardWidth: Dp = 150.dp, onClick: (SaavnPlaylist) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists) { playlist ->
            SaavnPlaylistCard(playlist = playlist, width = cardWidth, onClick = { onClick(playlist) })
        }
    }
}

@Composable
fun SaavnPlaylistCard(playlist: SaavnPlaylist, width: Dp = 150.dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(playlist.image.replace("http://","https://"))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.songCount} songs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ArtistAvatar(name: String, imageUrl: String?, modifier: Modifier = Modifier) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val colors = listOf(0xFF9B72F8, 0xFF7B52D8, 0xFF5B32B8, 0xFFB892FF, 0xFF6A4FC8)
    val bgColor = Color(colors[name.length % colors.size])

    val validUrl = imageUrl?.replace("http://", "https://")
        ?.takeIf { it.isNotBlank() && it.startsWith("https://") }

    SubcomposeAsyncImage(
        model = validUrl,
        contentDescription = name,
        modifier = modifier.clip(CircleShape),
        contentScale = ContentScale.Crop,
        error = {
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, style = MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ArtistCard(artist: SaavnArtist) {
    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtistAvatar(name = artist.name, imageUrl = artist.image, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(6.dp))
        Text(artist.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AlbumCard(album: SaavnAlbum, onClick: () -> Unit) {
    Column(modifier = Modifier.width(170.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(album.image.replace("http://","https://")).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(170.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(album.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artists, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun QuickActionChip(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
