package com.vynce.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.vynce.app.data.stats.PlaybackStatsRepository
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.viewmodels.StatsViewModel
import com.vynce.app.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.asPaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = bottomPadding + 16.dp
            )
        ) {
            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading insights...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@LazyColumn
            }

            if (state.featuredSong != null) {
                item {
                    TopSongCard(state.featuredSong!!)
                    Spacer(Modifier.height(20.dp))
                }
            }

            // 2. Your Listening Summary
            item {
                ListeningSummarySection(state.totalListeningMs, state.totalPlayCount, state.totalUniqueSongs)
                Spacer(Modifier.height(24.dp))
            }

            // 3. Top Songs
            if (state.topSongs.isNotEmpty()) {
                item {
                    TopSongsList(state.topSongs)
                    Spacer(Modifier.height(24.dp))
                }
            }

            // 4. Top Artists
            if (state.topArtists.isNotEmpty()) {
                item {
                    TopArtistsList(state.topArtists) // Show all top artists
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 5. Listening Profile (Merged Chip + Habits)
            if (state.listeningHabits != null) {
                item {
                    ListeningProfileSection(
                        chip = state.personaChip,
                        description = state.personaDescription,
                        distribution = state.listeningHabits!!,
                        totalPlays = state.totalPlayCount
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

        }
    }
}

@Composable
fun TopSongCard(song: PlaybackStatsRepository.SongPlaybackSummary) {
    Column {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Most Played Song",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Played ${song.playCount} times",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ListeningSummarySection(totalMs: Long, totalPlays: Int, uniqueSongs: Int) {
    val hours = totalMs / (1000 * 60 * 60)
    val minutes = (totalMs / (1000 * 60)) % 60
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatTile(modifier = Modifier.weight(1f), value = timeStr, label = "Time")
        StatTile(modifier = Modifier.weight(1f), value = totalPlays.toString(), label = "Plays")
        StatTile(modifier = Modifier.weight(1f), value = uniqueSongs.toString(), label = "Songs")
    }
}

@Composable
fun TopSongsList(songs: List<PlaybackStatsRepository.SongPlaybackSummary>) {
    Column {
        Text("Top Songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        songs.forEachIndexed { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 2}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.playCount} plays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < songs.size - 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
fun TopArtistsList(artists: List<PlaybackStatsRepository.ArtistPlaybackSummary>) {
    Column {
        Text("Top Artists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        artists.forEachIndexed { index, artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    text = artist.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.playCount} plays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < artists.size - 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
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
        Triple("Morning", morning, "5 AM and 12 PM"),
        Triple("Afternoon", afternoon, "12 PM and 5 PM"),
        Triple("Evening", evening, "5 PM and 10 PM"),
        Triple("Night", night, "10 PM and 5 AM")
    ).sortedByDescending { it.second }

    val topHabit = habits.first()
    val topPercentage = ((topHabit.second.toFloat() / total.toFloat()) * 100).toInt()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Listening Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            
            Text(chip.substringAfter(" "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))
            
            Text("${topHabit.first} Listener", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$topPercentage% of your listening happens between ${topHabit.third}.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (totalPlays < 100) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Insights will improve as you listen more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun StatTile(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(88.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
