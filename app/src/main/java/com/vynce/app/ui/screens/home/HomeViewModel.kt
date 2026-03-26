package com.vynce.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnAlbum
import com.zionhuang.jiosaavn.SaavnArtist
import com.zionhuang.jiosaavn.SaavnPlaylistInfo
import com.zionhuang.jiosaavn.SaavnSong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

object SaavnCuratedPlaylists {
    val sections = listOf(
        SectionDef(0, "Trending Now",      "🔥", "Weekly Top Songs Hindi"),
        SectionDef(1, "Chartbusters",       "🏆", "Hindi Chartbusters 2025"),
        SectionDef(2, "Bollywood Hits",    "🎬", "Bollywood Top 40"),
        SectionDef(3, "Telugu Hits",       "🌟", "Telugu Top 50"),
        SectionDef(4, "Punjabi Grooves",    "🎤", "Punjabi India Superhits"),
        SectionDef(5, "Zen & Chill",       "😌", "Best Indian Lofi"),
        SectionDef(6, "Romantic Vibes",    "💕", "Romantic Hits Hindi"),
        SectionDef(7, "Global Hits",       "🎸", "Top English Songs International")
    )
}

data class SectionDef(val index: Int, val title: String, val emoji: String, val searchQuery: String)

data class HomeSection(
    val title: String,
    val emoji: String,
    val songs: List<SaavnSong> = emptyList(),
    val playlistInfo: SaavnPlaylistInfo? = null,
    val isLoading: Boolean = true
)

data class HomeState(
    val sections: List<HomeSection> = SaavnCuratedPlaylists.sections.map { HomeSection(it.title, it.emoji) },
    val featuredArtists: List<SaavnArtist> = emptyList(),
    val newAlbums: List<SaavnAlbum> = emptyList(),
    val selectedLanguage: String = "All",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("jiosaavn_cache", Context.MODE_PRIVATE)

    init { loadAll() }

    fun loadAll() {
        val currentLang = _state.value.selectedLanguage
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                SaavnCuratedPlaylists.sections.forEach { sectionDef ->
                    loadSection(sectionDef, currentLang)
                }
                loadArtists()
                loadNewAlbums()
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Error loading home", e)
                _state.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private fun loadSection(sectionDef: SectionDef, language: String) {
        viewModelScope.launch {
            try {
                val query = if (language == "All") sectionDef.searchQuery else "${sectionDef.searchQuery} $language"
                val cachedId = prefs.getString("pl_$query", null)
                    ?.takeIf { it.isNotBlank() && it != "null" }
                
                val playlistId = cachedId ?: run {
                    val results = JioSaavn.searchPlaylists(query)
                    val found = results.firstOrNull { 
                        it.name.contains(query.split(" ").first(), ignoreCase = true)
                    } ?: results.firstOrNull()
                    
                    found?.id?.also { id ->
                        prefs.edit().putString("pl_$query", id).apply()
                    }
                }
                
                if (playlistId != null) {
                    val (info, songs) = JioSaavn.getPlaylist(playlistId)
                    _state.update { s ->
                        val updated = s.sections.toMutableList()
                        if (sectionDef.index < updated.size) {
                            updated[sectionDef.index] = updated[sectionDef.index].copy(
                                songs = songs.take(20), playlistInfo = info, isLoading = false
                            )
                        }
                        s.copy(sections = updated)
                    }
                } else {
                    // Fallback to song search if no playlist found
                    val query = if (language == "All") sectionDef.searchQuery else "${sectionDef.searchQuery} $language"
                    val songs = JioSaavn.searchSongs(query)
                    _state.update { s ->
                        val updated = s.sections.toMutableList()
                        if (sectionDef.index < updated.size) {
                            updated[sectionDef.index] = updated[sectionDef.index].copy(
                                songs = songs.take(20), isLoading = false
                            )
                        }
                        s.copy(sections = updated)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Section load error: ${sectionDef.title}", e)
                _state.update { s ->
                    val updated = s.sections.toMutableList()
                    if (sectionDef.index < updated.size) {
                        updated[sectionDef.index] = updated[sectionDef.index].copy(isLoading = false)
                    }
                    s.copy(sections = updated)
                }
            }
        }
    }

    private fun loadArtists() {
        viewModelScope.launch {
            try {
                val queries = listOf("Arijit Singh", "Anirudh Ravichander", "Diljit Dosanjh", 
                                     "AP Dhillon", "Shreya Ghoshal", "Sid Sriram", "AR Rahman")
                val artists = queries.map { query ->
                    async {
                        try {
                            JioSaavn.searchArtists(query).firstOrNull()
                        } catch (e: Exception) { null }
                    }
                }.awaitAll().filterNotNull().filter { it.image.isNotBlank() }
                
                _state.update { it.copy(featuredArtists = artists) }
            } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun loadNewAlbums() {
        viewModelScope.launch {
            try {
                val bollywood = JioSaavn.searchAlbums("New Bollywood Albums 2025")
                val english = JioSaavn.searchAlbums("New International Albums 2025")
                val allAlbums = (bollywood + english).shuffled().take(15)
                _state.update { it.copy(newAlbums = allAlbums) }
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun setLanguage(lang: String) {
        _state.update { it.copy(selectedLanguage = lang) }
        loadAll() // Trigger reload with language context (though query split isn't fully implemented yet)
    }
}