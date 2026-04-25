package com.vynce.app.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnAlbumInfo
import com.zionhuang.jiosaavn.SaavnArtist
import com.zionhuang.jiosaavn.SaavnHomeAlbumModule
import com.zionhuang.jiosaavn.SaavnHomePlaylistModule
import com.zionhuang.jiosaavn.SaavnHomeSongModule
import com.zionhuang.jiosaavn.SaavnPlaylistInfo
import com.zionhuang.jiosaavn.SaavnSong
import com.vynce.app.db.DatabaseDao
import com.vynce.app.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val availableLanguages: List<String> = listOf("Hindi", "English", "Telugu", "Tamil", "Punjabi", "Marathi", "Gujarati", "Bengali", "Kannada", "Bhojpuri", "Malayalam", "Urdu", "Haryanvi", "Rajasthani", "Odia", "Assamese"),
    val isLoading: Boolean = true,
    val error: String? = null
)

// ── ViewModel ───────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: com.vynce.app.db.MusicDatabase
) : ViewModel() {

    private val TAG = "HomeViewModel"
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private var saavnSections: List<HomeSection> = emptyList()

    init {
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
        
        loadAll()
        
        viewModelScope.launch { 
            loadArtists() 
        }
    }

    fun loadAll() {
        val lang = _state.value.selectedLanguage
        
        _state.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
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
            val queries = listOf(
                "Arijit Singh", "Anirudh Ravichander", "Diljit Dosanjh",
                "AP Dhillon", "Shreya Ghoshal", "Sid Sriram", "AR Rahman"
            )
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

    fun setLanguage(lang: String) {
        if (lang == _state.value.selectedLanguage) return
        _state.update { it.copy(selectedLanguage = lang) }
        loadAll()
    }
}