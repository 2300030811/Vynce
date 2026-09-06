package com.vynce.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.R
import com.vynce.app.data.stats.PlaybackStatsRepository
import com.vynce.app.data.stats.StatsTimeRange
import com.vynce.app.models.MediaMetadata
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.utils.urlEncode
import com.vynce.app.viewmodels.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    fun playTrack(songId: String, title: String?, artist: String?, thumbnail: String?) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Detailed Insights",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
        
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Calculating your insights...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (state.totalPlayCount == 0 && state.featuredSong == null) {
            // ── EMPTY STATE FOR NEW USERS ────────────────────────────
            StatsEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onStartListening = {
                    navController.navigateUp()
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .animateContentSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = bottomPadding + 24.dp
                )
            ) {
                // 1. Time Range Filter Selector
                item(key = "time_range_selector") {
                    TimeRangeSelector(
                        selected = state.selectedTimeRange,
                        onSelect = { viewModel.setTimeRange(it) }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 2. Hero Most Played Song
                if (state.featuredSong != null) {
                    item(key = "featured_song") {
                        TopSongCard(
                            song = state.featuredSong!!,
                            onClick = {
                                val s = state.featuredSong!!
                                playTrack(s.songId, s.title, s.artist, s.albumArtUri)
                            }
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }

                // 3. Listening Summary Stat Tiles
                item(key = "summary_section") {
                    ListeningSummarySection(
                        totalMs = state.totalListeningMs,
                        totalPlays = state.totalPlayCount,
                        uniqueSongs = state.totalUniqueSongs
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // 4. Top Songs List with Thumbnails & Clickable Playback
                if (state.topSongs.isNotEmpty()) {
                    item(key = "top_songs_list") {
                        TopSongsList(
                            songs = state.topSongs,
                            onSongClick = { song ->
                                playTrack(song.songId, song.title, song.artist, song.albumArtUri)
                            }
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // 5. Top Artists List with Clickable Navigation
                if (state.topArtists.isNotEmpty()) {
                    item(key = "top_artists_list") {
                        TopArtistsList(
                            artists = state.topArtists,
                            onArtistClick = { artist ->
                                navController.navigate("search/${artist.artist.urlEncode()}")
                            }
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // 6. Listening Profile (Personality DNA + Habit Distribution)
                if (state.listeningHabits != null) {
                    item(key = "listening_profile") {
                        ListeningProfileSection(
                            chip = state.personaChip,
                            description = state.personaDescription,
                            distribution = state.listeningHabits!!,
                            totalPlays = state.totalPlayCount
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeRangeSelector(
    selected: StatsTimeRange,
    onSelect: (StatsTimeRange) -> Unit
) {
    val ranges = listOf(
        StatsTimeRange.ALL to "All Time",
        StatsTimeRange.MONTH to "This Month",
        StatsTimeRange.WEEK to "This Week",
        StatsTimeRange.DAY to "Today"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(ranges) { (range, label) ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = { Text(label, fontWeight = if (selected == range) FontWeight.Bold else FontWeight.Normal) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun StatsEmptyState(
    modifier: Modifier = Modifier,
    onStartListening: () -> Unit
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoGraph,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "No Listening Stats Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Play your favorite tracks and albums to unlock your personalized listening profile, top songs, favorite artists, and listening DNA.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStartListening,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Listening", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TopSongCard(
    song: PlaybackStatsRepository.SongPlaybackSummary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (!song.albumArtUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Floating Play Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist.ifEmpty { "Most Played Song" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "${song.playCount} plays",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ListeningSummarySection(totalMs: Long, totalPlays: Int, uniqueSongs: Int) {
    val hours = totalMs / (1000 * 60 * 60)
    val minutes = (totalMs / (1000 * 60)) % 60
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(modifier = Modifier.weight(1f), value = timeStr, label = "Listening Time", icon = Icons.Rounded.GraphicEq)
        StatTile(modifier = Modifier.weight(1f), value = totalPlays.toString(), label = "Total Plays", icon = Icons.Rounded.PlayArrow)
        StatTile(modifier = Modifier.weight(1f), value = uniqueSongs.toString(), label = "Unique Songs", icon = Icons.Rounded.MusicNote)
    }
}

@Composable
fun TopSongsList(
    songs: List<PlaybackStatsRepository.SongPlaybackSummary>,
    onSongClick: (PlaybackStatsRepository.SongPlaybackSummary) -> Unit
) {
    Column {
        Text("Top Songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        
        songs.forEachIndexed { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSongClick(song) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank number
                Text(
                    text = "${index + 2}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.width(8.dp))

                // Album art thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!song.albumArtUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title + Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist.ifEmpty { "Unknown Artist" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Plays count
                Text(
                    text = "${song.playCount} plays",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (index < songs.size - 1) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    modifier = Modifier.padding(start = 80.dp)
                )
            }
        }
    }
}

@Composable
fun TopArtistsList(
    artists: List<PlaybackStatsRepository.ArtistPlaybackSummary>,
    onArtistClick: (PlaybackStatsRepository.ArtistPlaybackSummary) -> Unit
) {
    Column {
        Text("Top Artists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        artists.forEachIndexed { index, artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onArtistClick(artist) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank number
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.width(8.dp))

                // Artist Name & Track Count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${artist.uniqueSongs} ${if (artist.uniqueSongs == 1) "track" else "tracks"} played",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Plays count
                Text(
                    text = "${artist.playCount} plays",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (index < artists.size - 1) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        }
    }
}

@Composable
fun ListeningProfileSection(
    chip: String,
    description: String,
    distribution: PlaybackStatsRepository.DayListeningDistribution,
    totalPlays: Int
) {
    val morning = distribution.buckets.filter { it.startMinute in 300..719 }.sumOf { it.totalDurationMs }
    val afternoon = distribution.buckets.filter { it.startMinute in 720..1019 }.sumOf { it.totalDurationMs }
    val evening = distribution.buckets.filter { it.startMinute in 1020..1319 }.sumOf { it.totalDurationMs }
    val night = distribution.buckets.filter { it.startMinute < 300 || it.startMinute >= 1320 }.sumOf { it.totalDurationMs }
    
    val total = (morning + afternoon + evening + night).coerceAtLeast(1L)
    
    val habits = listOf(
        Triple("Morning", morning, "5 AM – 12 PM"),
        Triple("Afternoon", afternoon, "12 PM – 5 PM"),
        Triple("Evening", evening, "5 PM – 10 PM"),
        Triple("Night", night, "10 PM – 5 AM")
    ).sortedByDescending { it.second }

    val topHabit = habits.first()
    val topPercentage = ((topHabit.second.toFloat() / total.toFloat()) * 100).toInt()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Listening Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            
            val personaTitle = if (chip.contains(" ")) chip.substringAfter(" ") else chip
            Text(personaTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(Modifier.height(16.dp))
            
            Text("${topHabit.first} Listener", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$topPercentage% of your listening happens during ${topHabit.first.lowercase()} (${topHabit.third}).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            // Visual distribution bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                val morningWeight = (morning.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                val afternoonWeight = (afternoon.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                val eveningWeight = (evening.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                val nightWeight = (night.toFloat() / total.toFloat()).coerceAtLeast(0.02f)

                Box(Modifier.weight(morningWeight).fillMaxSize().background(Color(0xFFF6AD55)))
                Box(Modifier.weight(afternoonWeight).fillMaxSize().background(Color(0xFF63B3ED)))
                Box(Modifier.weight(eveningWeight).fillMaxSize().background(Color(0xFFB794F4)))
                Box(Modifier.weight(nightWeight).fillMaxSize().background(Color(0xFF4A5568)))
            }

            Spacer(Modifier.height(10.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendItem(color = Color(0xFFF6AD55), label = "Morning")
                LegendItem(color = Color(0xFF63B3ED), label = "Afternoon")
                LegendItem(color = Color(0xFFB794F4), label = "Evening")
                LegendItem(color = Color(0xFF4A5568), label = "Night")
            }

            if (totalPlays < 50) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "✨ Your insights will grow richer as you continue streaming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.height(96.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
