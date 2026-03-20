package com.vynce.app.ui.screens.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vynce.app.ui.theme.*


@Composable
fun StatsScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val topSongs by viewModel.topSongs.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val totalMinutes by viewModel.totalMinutes.collectAsState()
    val totalSongs by viewModel.totalSongs.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Your stats", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = VynceOnSurface)
        }
        // Summary cards row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.MusicNote,
                    value = totalSongs.toString(),
                    label = "Songs played"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Schedule,
                    value = "${totalMinutes}m",
                    label = "Listening time"
                )
            }
        }
        // Top songs
        item { SectionHeader("Top songs this month") }
        items(topSongs.size) { idx ->
            TopSongRow(song = topSongs[idx])
        }
        // Top artists
        item { SectionHeader("Top artists") }
        items(topArtists.size) { idx ->
            TopArtistRow(artist = topArtists[idx])
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
             value: String, label: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(
        containerColor = VynceSurfaceCard)) {
        Column(modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = VyncePurple,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = VynceOnSurface)
            Text(label, fontSize = 12.sp, color = VynceMuted)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        color = VynceAccent, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun TopSongRow(song: com.vynce.app.db.SongWithPlayCount) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(song.song.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${song.playCount} plays", color = VynceMuted, fontSize = 12.sp)
    }
}

@Composable
fun TopArtistRow(artist: com.vynce.app.db.ArtistWithPlayCount) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(artist.artist.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${artist.playCount} plays", color = VynceMuted, fontSize = 12.sp)
    }
}
