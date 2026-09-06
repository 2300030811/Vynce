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
    val selectedTimeRange: StatsTimeRange = StatsTimeRange.ALL,
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
        loadStats(StatsTimeRange.ALL)
        viewModelScope.launch {
            playbackStatsRepository.refreshFlow.collect {
                loadStats(_state.value.selectedTimeRange)
            }
        }
    }

    fun setTimeRange(range: StatsTimeRange) {
        if (_state.value.selectedTimeRange == range && !_state.value.isLoading) return
        loadStats(range)
    }

    fun reload() {
        loadStats(_state.value.selectedTimeRange)
    }

    private fun loadStats(range: StatsTimeRange) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, selectedTimeRange = range) }
            val songs = database.songs(SongSortType.CREATE_DATE, true).first()
            val summary = playbackStatsRepository.loadSummary(
                range = range,
                songs = songs
            )
            val sortedSongs = summary.songs.sortedWith(compareByDescending<PlaybackStatsRepository.SongPlaybackSummary> { it.playCount }.thenByDescending { it.totalDurationMs })
            val sortedArtists = summary.topArtists.sortedWith(compareByDescending<PlaybackStatsRepository.ArtistPlaybackSummary> { it.playCount }.thenByDescending { it.totalDurationMs })

            // Calculate Personality dynamically
            val (personaChip, personaDesc) = playbackStatsRepository.calculatePersonality(summary)

            _state.update {
                it.copy(
                    isLoading = false,
                    selectedTimeRange = range,
                    featuredSong = sortedSongs.firstOrNull(),
                    topArtists = sortedArtists.take(10), // Show up to 10 top artists
                    topSongs = sortedSongs.drop(1).take(10), // Top 10 songs, excluding the #1 hero
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
