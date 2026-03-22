package com.vynce.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.jiosaavn.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val greeting: String = "",
    val heroSong: SaavnSong? = null,
    val newReleases: List<SaavnPlaylist> = emptyList(),
    val trending: List<SaavnPlaylist> = emptyList(),
    val topArtists: List<SaavnArtist> = emptyList(),
    val bollywood: List<SaavnPlaylist> = emptyList(),
    val tollywood: List<SaavnPlaylist> = emptyList(),
    val lofi: List<SaavnPlaylist> = emptyList(),
    val romantic: List<SaavnPlaylist> = emptyList(),
    val party: List<SaavnPlaylist> = emptyList(),
    val punjabi: List<SaavnPlaylist> = emptyList(),
    val english: List<SaavnPlaylist> = emptyList(),
    val topPicks: List<SaavnPlaylist> = emptyList(),
    val featuredAlbums: List<SaavnAlbum> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        loadAll()
    }

    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, greeting = getGreeting()) }
            val saavn = JioSaavn
            
            // Priority Batch: Telugu, English, Hero, Artists
            val heroSongDefer = async {
                try {
                    val telugu = saavn.searchSongs("trending telugu songs 2025")
                    if (telugu.isNotEmpty()) {
                        telugu.random()
                    } else {
                        saavn.searchSongs("trending english songs 2025").firstOrNull()
                    }
                } catch(e: Exception) { null }
            }
            val tollywoodDefer = async { try { saavn.searchPlaylists("tollywood hits 2025") } catch(e: Exception) { emptyList() } }
            val englishDefer = async { try { saavn.searchPlaylists("english pop hits 2025") } catch(e: Exception) { emptyList() } }
            
            val artistNames = listOf(
                "Arijit Singh", "Shreya Ghoshal", "Jubin Nautiyal",
                "Neha Kakkar", "Diljit Dosanjh", "AP Dhillon",
                "Badshah", "Atif Aslam"
            )
            val topArtistsDefer = async {
                artistNames.mapNotNull { name ->
                    try { saavn.searchArtists(name).firstOrNull() } catch (e: Exception) { null }
                }
            }

            // Start fetching secondary batch now but await later
            val newReleasesDefer = async { try { saavn.searchPlaylists("new releases hindi 2025") } catch(e: Exception) { emptyList() } }
            val trendingDefer = async { try { saavn.searchPlaylists("trending india 2025") } catch(e: Exception) { emptyList() } }
            val bollywoodDefer = async { try { saavn.searchPlaylists("bollywood hits 2025") } catch(e: Exception) { emptyList() } }
            val lofiDefer = async { try { saavn.searchPlaylists("lofi chill hindi relaxing") } catch(e: Exception) { emptyList() } }
            val romanticDefer = async { try { saavn.searchPlaylists("romantic hindi love songs") } catch(e: Exception) { emptyList() } }
            val partyDefer = async { try { saavn.searchPlaylists("party dance hits hindi 2025") } catch(e: Exception) { emptyList() } }
            val punjabiDefer = async { try { saavn.searchPlaylists("punjabi hits 2025") } catch(e: Exception) { emptyList() } }
            val topPicksDefer = async { try { saavn.searchPlaylists("top picks india best of") } catch(e: Exception) { emptyList() } }
            val albumsDefer = async { try { saavn.searchAlbums("best hindi albums 2025") } catch(e: Exception) { emptyList() } }

            try {
                // Await priority first
                val hero = heroSongDefer.await()
                val tollywood = tollywoodDefer.await()
                val english = englishDefer.await()
                val artists = topArtistsDefer.await()

                _state.update { s ->
                    s.copy(
                        heroSong = hero,
                        tollywood = tollywood,
                        english = english,
                        topArtists = artists,
                    )
                }

                // Await rest
                val newReleases = newReleasesDefer.await()
                val trending = trendingDefer.await()
                val bollywood = bollywoodDefer.await()
                val lofi = lofiDefer.await()
                val romantic = romanticDefer.await()
                val party = partyDefer.await()
                val punjabi = punjabiDefer.await()
                val topPicks = topPicksDefer.await()
                val albums = albumsDefer.await()

                _state.update { s ->
                    s.copy(
                        newReleases = newReleases,
                        trending = trending,
                        bollywood = bollywood,
                        featuredAlbums = albums,
                        lofi = lofi,
                        romantic = romantic,
                        party = party,
                        punjabi = punjabi,
                        topPicks = topPicks,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Error loading sections: ${e.message}")
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
