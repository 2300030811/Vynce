package com.vynce.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vynce.app.data.stats.PlaybackStatsRepository
import com.vynce.app.data.stats.StatsTimeRange
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.Song
import com.vynce.app.constants.SongSortType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsV2State(
    val isLoading: Boolean = true,
    val featuredSong: PlaybackStatsRepository.SongPlaybackSummary? = null,
    val topArtists: List<PlaybackStatsRepository.ArtistPlaybackSummary> = emptyList(),
    val topSongs: List<PlaybackStatsRepository.SongPlaybackSummary> = emptyList(),
    val totalListeningMs: Long = 0L,
    val totalPlayCount: Int = 0,
    val personaChip: String = "🎵 Listening Style",
    val personaDescription: String = "Loading your music DNA...",
    val listeningHabits: PlaybackStatsRepository.DayListeningDistribution? = null,
    val totalUniqueSongs: Int = 0
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val playbackStatsRepository: PlaybackStatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsV2State())
    val state: StateFlow<StatsV2State> = _state.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val songs = database.songs(SongSortType.CREATE_DATE, true).first()
            val summary = playbackStatsRepository.loadSummary(
                range = StatsTimeRange.ALL,
                songs = songs
            )
            val sortedSongs = summary.songs.sortedWith(compareByDescending<PlaybackStatsRepository.SongPlaybackSummary> { it.playCount }.thenByDescending { it.totalDurationMs })
            val sortedArtists = summary.topArtists.sortedWith(compareByDescending<PlaybackStatsRepository.ArtistPlaybackSummary> { it.playCount }.thenByDescending { it.totalDurationMs })

            val topArtist = sortedArtists.firstOrNull()

            // Calculate Personality dynamically
            val (personaChip, personaDesc) = playbackStatsRepository.calculatePersonality(summary)

            _state.update {
                it.copy(
                    isLoading = false,
                    featuredSong = sortedSongs.firstOrNull(),
                    topArtists = sortedArtists.take(5), // Keep top 5 artists
                    topSongs = sortedSongs.drop(1).take(5), // Top 5 songs, excluding the #1 hero
                    totalListeningMs = summary.totalDurationMs,
                    totalPlayCount = summary.totalPlayCount,
                    totalUniqueSongs = summary.uniqueSongs,
                    personaChip = personaChip,
                    personaDescription = personaDesc,
                    listeningHabits = summary.dayListeningDistribution
                )
            }
        }
    }
}
