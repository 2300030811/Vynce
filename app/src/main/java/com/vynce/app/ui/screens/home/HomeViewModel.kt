package com.vynce.app.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vynce.jiosaavn.JioSaavn
import com.vynce.jiosaavn.SaavnAlbumInfo
import com.vynce.jiosaavn.SaavnArtist
import com.vynce.jiosaavn.SaavnHomeAlbumModule
import com.vynce.jiosaavn.SaavnHomePlaylistModule
import com.vynce.jiosaavn.SaavnHomeSongModule
import com.vynce.jiosaavn.SaavnPlaylistInfo
import com.vynce.jiosaavn.SaavnSong
import com.vynce.app.constants.ContentLanguageKey
import com.vynce.app.constants.SYSTEM_DEFAULT
import com.vynce.app.constants.SongSortType
import com.vynce.app.data.stats.PlaybackStatsRepository
import com.vynce.app.data.stats.StatsTimeRange
import com.vynce.app.db.DatabaseDao
import com.vynce.app.db.entities.Song
import com.vynce.app.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

// ── UI state models ─────────────────────────────────────────────────

sealed interface HomeSection {
    val title: String
    val subtitle: String?
    
    data class SongSection(override val title: String, override val subtitle: String? = null, val songs: List<SaavnSong>) : HomeSection
    data class LocalSongSection(override val title: String, override val subtitle: String? = null, val songs: List<Song>) : HomeSection
    data class AlbumSection(override val title: String, override val subtitle: String? = null, val albums: List<SaavnAlbumInfo>) : HomeSection
    data class PlaylistSection(override val title: String, override val subtitle: String? = null, val playlists: List<SaavnPlaylistInfo>) : HomeSection
}

data class HomeState(
    val sections: List<HomeSection> = emptyList(),
    val featuredArtists: List<SaavnArtist> = emptyList(),
    val selectedLanguage: String = "Hindi",
    val isLoading: Boolean = true,
    val error: String? = null,
    val continueListening: List<PlaybackStatsRepository.PlaybackHistoryEntry> = emptyList(),
    val rediscover: List<PlaybackStatsRepository.SongPlaybackSummary> = emptyList(),
    val topStatsArtists: List<PlaybackStatsRepository.ArtistPlaybackSummary> = emptyList(),
    val listeningStreakDays: Int = 0,
    val activeDays: Int = 0,
    val totalPlayCount: Int = 0,
    val uniqueSongs: Int = 0,
    val totalListeningMs: Long = 0L,
    val topSongs: List<PlaybackStatsRepository.SongPlaybackSummary> = emptyList(),
    val topGenres: List<PlaybackStatsRepository.GenrePlaybackSummary> = emptyList(),
    val personaChip: String = "🎵 Melody Hunter",
    val personaDescription: String = "",
    val additionalInsight: String = ""
)

// ── ViewModel ───────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: com.vynce.app.db.MusicDatabase,
    private val playbackStatsRepository: PlaybackStatsRepository
) : ViewModel() {

    private val TAG = "HomeViewModel"
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private var saavnSections: List<HomeSection> = emptyList()
    private var loadJob: Job? = null

    init {
        // Load and update Playback Stats automatically
        viewModelScope.launch {
            playbackStatsRepository.refreshFlow.collect {
                try {
                    val songs = database.songs(SongSortType.CREATE_DATE, true).first()
                    val summary = playbackStatsRepository.loadSummary(
                        range = StatsTimeRange.ALL,
                        songs = songs
                    )
                    val recentHistory = playbackStatsRepository.loadPlaybackHistory(20)
                    val recentSongIds = recentHistory.map { it.songId }.toSet()
                    val rediscoverSongs = playbackStatsRepository.getRediscoverSongs(
                        limit = 15,
                        excludeSongIds = recentSongIds
                    )
                    Log.d(TAG, "Rediscover candidates found: ${rediscoverSongs.size} (using thresholds: minPlays=2, minDays=14)")

                    val insight = summary.dayListeningDistribution?.let { dist ->
                        val peakHour = dist.buckets.maxByOrNull { it.totalDurationMs }?.startMinute?.div(60) ?: -1
                        when (peakHour) {
                            in 5..11 -> "Most active time: Morning"
                            in 12..16 -> "Most active time: Afternoon"
                            in 17..21 -> "Most active time: Evening"
                            in 22..24, in 0..4 -> "Most active time: Late Night"
                            else -> null
                        }
                    } ?: summary.peakDayLabel?.let { "Most active day: $it" } ?: "Music is a huge part of your routine."
                    val (personaChip, personaDesc) = playbackStatsRepository.calculatePersonality(summary)

                    _state.update { state ->
                        state.copy(
                            listeningStreakDays = summary.longestStreakDays,
                            activeDays = summary.activeDays,
                            totalPlayCount = summary.totalPlayCount,
                            uniqueSongs = summary.uniqueSongs,
                            totalListeningMs = summary.totalDurationMs,
                            topStatsArtists = summary.topArtists,
                            continueListening = recentHistory,
                            rediscover = rediscoverSongs,
                            topSongs = summary.topSongs,
                            topGenres = summary.topGenres,
                            personaChip = personaChip,
                            personaDescription = personaDesc,
                            additionalInsight = insight
                        )
                    }
                    loadArtists()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load stats for HomeState", e)
                }
            }
        }

        viewModelScope.launch {
            database.quickPicks().collect { localSongs ->
                val quickPicksSection = if (localSongs.isNotEmpty()) {
                    listOf(HomeSection.LocalSongSection("Quick Picks", "Based on your history", localSongs.take(20)))
                } else emptyList()
                
                _state.update { state ->
                    state.copy(sections = quickPicksSection + saavnSections)
                }
            }
        }
        
        // Observe content language preference and reload when it changes
        viewModelScope.launch {
            context.dataStore.data
                .map { prefs ->
                    val stored = prefs[ContentLanguageKey] ?: SYSTEM_DEFAULT
                    resolveLanguage(stored)
                }
                .distinctUntilChanged()
                .collect { lang ->
                    _state.update { it.copy(selectedLanguage = lang) }
                    loadAll()
                }
        }
        
        // loadArtists() is now called after stats are loaded
    }


    /**
     * Resolves the stored language preference to the actual language string
     * that JioSaavn expects. Falls back to system locale → "Hindi".
     */
    private fun resolveLanguage(stored: String): String {
        if (stored == SYSTEM_DEFAULT || stored == "system") {
            // Map system locale to JioSaavn language name
            return when (Locale.getDefault().language) {
                "hi" -> "Hindi"
                "pa" -> "Punjabi"
                "ta" -> "Tamil"
                "te" -> "Telugu"
                "mr" -> "Marathi"
                "gu" -> "Gujarati"
                "bn" -> "Bengali"
                "kn" -> "Kannada"
                "ml" -> "Malayalam"
                "ur" -> "Urdu"
                "or" -> "Odia"
                "raj" -> "Rajasthani"
                "as" -> "Assamese"
                "bho" -> "Bhojpuri"
                "en" -> "English"
                else -> "Hindi"
            }
        }
        // The stored value is the language code from LanguageCodeToName
        // Map it to a display name or use it directly
        return stored.replaceFirstChar { it.titlecase() }
    }

    fun loadAll() {
        val lang = _state.value.selectedLanguage
        
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val saavnModules = JioSaavn.getHome(lang.lowercase())
                saavnSections = saavnModules.mapNotNull { module ->
                    when (module) {
                        is SaavnHomeSongModule -> HomeSection.SongSection(module.title, module.subtitle, module.songs)
                        is SaavnHomePlaylistModule -> HomeSection.PlaylistSection(module.title, module.subtitle, module.playlists)
                        is SaavnHomeAlbumModule -> HomeSection.AlbumSection(module.title, module.subtitle, module.albums)
                        else -> null
                    }
                }
                
                _state.update { state ->
                    val quickPicks = state.sections.filterIsInstance<HomeSection.LocalSongSection>()
                    state.copy(
                        isLoading = false,
                        sections = quickPicks + saavnSections
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading home", e)
                _state.update { it.copy(error = e.localizedMessage, isLoading = false) }
            }
        }
    }

    private suspend fun loadArtists() {
        try {
            val statsArtists = _state.value.topStatsArtists
            val queries = if (statsArtists.isNotEmpty()) {
                statsArtists.map { it.artist }.take(7)
            } else {
                listOf(
                    "Arijit Singh", "Anirudh Ravichander", "Diljit Dosanjh",
                    "AP Dhillon", "Shreya Ghoshal", "Sid Sriram", "AR Rahman"
                )
            }
            val artists = queries.map { query ->
                viewModelScope.async {
                    try {
                        JioSaavn.searchArtists(query).firstOrNull()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load artist: $query", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull().filter { it.image.isNotBlank() }

            _state.update { it.copy(featuredArtists = artists) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load artists", e)
        }
    }


}