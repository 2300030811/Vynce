package com.vynce.app.viewmodels

import androidx.lifecycle.SavedStateHandle
import com.vynce.app.data.search.SearchCacheManager
import com.vynce.app.models.UnifiedSearchResult
import com.zionhuang.jiosaavn.JioSaavn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

sealed interface UnifiedSearchUiState {
    object Loading : UnifiedSearchUiState
    data class Success(val result: UnifiedSearchResult) : UnifiedSearchUiState
    data class Error(val message: String) : UnifiedSearchUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class UnifiedSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchCacheManager: SearchCacheManager
) : BaseViewModel() {

    val initialQuery = savedStateHandle.get<String>("query") ?: ""
    private val _query = MutableStateFlow(initialQuery)
    val query = _query.asStateFlow()

    private val _uiState = MutableStateFlow<UnifiedSearchUiState>(UnifiedSearchUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        launchIO {
            _query
                .debounce(500)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    flow {
                        if (q.isBlank()) {
                            emit(UnifiedSearchUiState.Success(UnifiedSearchResult(q, emptyList(), emptyList(), emptyList(), emptyList(), null)))
                            return@flow
                        }
                        emit(UnifiedSearchUiState.Loading)
                        try {
                            val cached = searchCacheManager.get(q)
                            if (cached != null) {
                                emit(UnifiedSearchUiState.Success(cached))
                            } else {
                                val result = performUnifiedSearch(q)
                                searchCacheManager.put(q, result)
                                emit(UnifiedSearchUiState.Success(result))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("UnifiedSearchVM", "Search failed: ${e.message}", e)
                            emit(UnifiedSearchUiState.Error(e.message ?: "Search failed. Please try again."))
                        }
                    }
                }
                .collect {
                    _uiState.value = it
                }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun retry() {
        val q = _query.value
        if (q.isNotBlank()) {
            launchIO {
                // Invalidate only the current query from cache to force network refresh
                searchCacheManager.remove(q)
                // Toggle query to trigger flatMapLatest again
                _query.value = ""
                _query.value = q
            }
        }
    }

    private suspend fun performUnifiedSearch(q: String): UnifiedSearchResult = supervisorScope {
        val songsDeferred = async { JioSaavn.searchSongs(q) }
        val artistsDeferred = async { JioSaavn.searchArtists(q) }
        val albumsDeferred = async { JioSaavn.searchAlbums(q) }
        val playlistsDeferred = async { JioSaavn.searchPlaylists(q) }

        val songs = try { songsDeferred.await() } catch (e: Exception) { 
            android.util.Log.e("UnifiedSearchVM", "Songs query failed", e)
            emptyList() 
        }
        val artists = try { artistsDeferred.await() } catch (e: Exception) { 
            android.util.Log.e("UnifiedSearchVM", "Artists query failed", e)
            emptyList() 
        }
        val albums = try { albumsDeferred.await() } catch (e: Exception) { 
            android.util.Log.e("UnifiedSearchVM", "Albums query failed", e)
            emptyList() 
        }
        val playlists = try { playlistsDeferred.await() } catch (e: Exception) { 
            android.util.Log.e("UnifiedSearchVM", "Playlists query failed", e)
            emptyList() 
        }

        // Implement safe deduplication
        val dedupedSongs = songs.distinctBy { it.id }
        val dedupedArtists = artists.distinctBy { it.id }
        val dedupedAlbums = albums.distinctBy { it.id }
        val dedupedPlaylists = playlists.distinctBy { it.id }

        // Apply Tiered Top Result selection logic
        val trimmed = q.trim()
        val songCandidates = dedupedSongs.take(5)
        val artistCandidates = dedupedArtists.take(5)
        val albumCandidates = dedupedAlbums.take(5)
        val playlistCandidates = dedupedPlaylists.take(5)
        
        val normalizedQuery = trimmed.lowercase()
        val isSingleWordQuery = !trimmed.contains(" ") && trimmed.length >= 3
        
        val candidatesList = mutableListOf<ScoredCandidate>()
        
        // 1. Process Songs
        songCandidates.forEachIndexed { index, song ->
            val normalizedName = song.name.trim().lowercase()
            val firstWord = normalizedName.split(Regex("\\s+")).firstOrNull() ?: ""
            val initialMatchLevel = when {
                normalizedName == normalizedQuery -> MatchLevel.EXACT_MATCH_SONG
                firstWord == normalizedQuery -> MatchLevel.FIRST_WORD_MATCH
                normalizedName.startsWith(normalizedQuery) -> MatchLevel.STARTS_WITH_MATCH
                normalizedName.contains(normalizedQuery) -> MatchLevel.CONTAINS_MATCH
                else -> MatchLevel.NONE
            }
            val popularityScore = 0
            val apiRelevanceScore = maxOf(0, 10 - index) * 15
            val categoryWeight = 150
            val hasPlaceholderImage = song.image.isBlank() || song.image.lowercase().contains("default") || song.image.lowercase().contains("placeholder")
            val imagePenalty = if (hasPlaceholderImage) -200 else 0
            
            val finalScore = popularityScore + apiRelevanceScore + categoryWeight + imagePenalty
            candidatesList.add(
                ScoredCandidate(
                    name = song.name,
                    matchLevel = initialMatchLevel,
                    score = finalScore,
                    topResult = com.vynce.app.models.TopResult.Song(song)
                )
            )
        }
        
        // 2. Process Artists
        artistCandidates.forEachIndexed { index, artist ->
            val normalizedName = artist.name.trim().lowercase()
            val firstWord = normalizedName.split(Regex("\\s+")).firstOrNull() ?: ""
            var initialMatchLevel = when {
                normalizedName == normalizedQuery -> MatchLevel.EXACT_MATCH
                firstWord == normalizedQuery -> MatchLevel.FIRST_WORD_MATCH
                normalizedName.startsWith(normalizedQuery) -> MatchLevel.STARTS_WITH_MATCH
                normalizedName.contains(normalizedQuery) -> MatchLevel.CONTAINS_MATCH
                else -> MatchLevel.NONE
            }
            
            // Promote any artist matching the first word of a single-word query to EXACT_MATCH,
            // but only if they are ranked in the top 3 by the API (index <= 2).
            if (initialMatchLevel == MatchLevel.FIRST_WORD_MATCH && isSingleWordQuery && index <= 2) {
                initialMatchLevel = MatchLevel.EXACT_MATCH
            }
            
            val followers = parseFollowerCount(artist.followerCount)
            val popularityScore = minOf(500, (if (followers > 0) Math.log10(followers.toDouble() + 1.0) * 100 else 0.0).toInt())
            val apiRelevanceScore = maxOf(0, 10 - index) * 15
            val categoryWeight = 200
            val hasPlaceholderImage = artist.image.isBlank() || artist.image.lowercase().contains("default") || artist.image.lowercase().contains("placeholder")
            val imagePenalty = if (hasPlaceholderImage) -200 else 0
            
            val finalScore = popularityScore + apiRelevanceScore + categoryWeight + imagePenalty
            candidatesList.add(
                ScoredCandidate(
                    name = artist.name,
                    matchLevel = initialMatchLevel,
                    score = finalScore,
                    topResult = com.vynce.app.models.TopResult.Artist(artist)
                )
            )
        }
        
        // 3. Process Albums
        albumCandidates.forEachIndexed { index, album ->
            val normalizedName = album.name.trim().lowercase()
            val firstWord = normalizedName.split(Regex("\\s+")).firstOrNull() ?: ""
            val initialMatchLevel = when {
                normalizedName == normalizedQuery -> MatchLevel.EXACT_MATCH
                firstWord == normalizedQuery -> MatchLevel.FIRST_WORD_MATCH
                normalizedName.startsWith(normalizedQuery) -> MatchLevel.STARTS_WITH_MATCH
                normalizedName.contains(normalizedQuery) -> MatchLevel.CONTAINS_MATCH
                else -> MatchLevel.NONE
            }
            val popularityScore = 0
            val apiRelevanceScore = maxOf(0, 10 - index) * 15
            val categoryWeight = 100
            val hasPlaceholderImage = album.image.isBlank() || album.image.lowercase().contains("default") || album.image.lowercase().contains("placeholder")
            val imagePenalty = if (hasPlaceholderImage) -200 else 0
            
            val finalScore = popularityScore + apiRelevanceScore + categoryWeight + imagePenalty
            candidatesList.add(
                ScoredCandidate(
                    name = album.name,
                    matchLevel = initialMatchLevel,
                    score = finalScore,
                    topResult = com.vynce.app.models.TopResult.Album(album)
                )
            )
        }
        
        // 4. Process Playlists
        playlistCandidates.forEachIndexed { index, playlist ->
            val normalizedName = playlist.name.trim().lowercase()
            val firstWord = normalizedName.split(Regex("\\s+")).firstOrNull() ?: ""
            val initialMatchLevel = when {
                normalizedName == normalizedQuery -> MatchLevel.EXACT_MATCH
                firstWord == normalizedQuery -> MatchLevel.FIRST_WORD_MATCH
                normalizedName.startsWith(normalizedQuery) -> MatchLevel.STARTS_WITH_MATCH
                normalizedName.contains(normalizedQuery) -> MatchLevel.CONTAINS_MATCH
                else -> MatchLevel.NONE
            }
            
            val followers = parseFollowerCount(playlist.followerCount)
            val popularityScore = minOf(500, (if (followers > 0) Math.log10(followers.toDouble() + 1.0) * 100 else 0.0).toInt())
            val apiRelevanceScore = maxOf(0, 10 - index) * 15
            val categoryWeight = 50
            val hasPlaceholderImage = playlist.image.isBlank() || playlist.image.lowercase().contains("default") || playlist.image.lowercase().contains("placeholder")
            val imagePenalty = if (hasPlaceholderImage) -200 else 0
            
            val finalScore = popularityScore + apiRelevanceScore + categoryWeight + imagePenalty
            candidatesList.add(
                ScoredCandidate(
                    name = playlist.name,
                    matchLevel = initialMatchLevel,
                    score = finalScore,
                    topResult = com.vynce.app.models.TopResult.Playlist(playlist)
                )
            )
        }
        
        val sortedCandidates = candidatesList
            .filter { it.matchLevel != MatchLevel.NONE }
            .sortedWith(
                compareBy<ScoredCandidate> { MATCH_PRIORITY[it.matchLevel] ?: 5 }
                    .thenByDescending { it.score }
            )
            
        val winner = sortedCandidates.firstOrNull()
        
        val topResult = when {
            winner != null -> winner.topResult
            dedupedSongs.isNotEmpty() -> com.vynce.app.models.TopResult.Song(dedupedSongs.first())
            else -> null
        }
        
        UnifiedSearchResult(
            query = q,
            songs = dedupedSongs,
            artists = dedupedArtists,
            albums = dedupedAlbums,
            playlists = dedupedPlaylists,
            topResult = topResult
        )
    }
}

private enum class MatchLevel {
    EXACT_MATCH_SONG,
    EXACT_MATCH,
    FIRST_WORD_MATCH,
    STARTS_WITH_MATCH,
    CONTAINS_MATCH,
    NONE
}

private val MATCH_PRIORITY = mapOf(
    MatchLevel.EXACT_MATCH_SONG to 0,
    MatchLevel.EXACT_MATCH to 1,
    MatchLevel.FIRST_WORD_MATCH to 2,
    MatchLevel.STARTS_WITH_MATCH to 3,
    MatchLevel.CONTAINS_MATCH to 4,
    MatchLevel.NONE to 5
)

private data class ScoredCandidate(
    val name: String,
    val matchLevel: MatchLevel,
    val score: Int,
    val topResult: com.vynce.app.models.TopResult
)

private fun parseFollowerCount(followerCountStr: String): Long {
    val trimmed = followerCountStr.trim().uppercase()
    if (trimmed.isEmpty()) return 0L
    return try {
        if (trimmed.endsWith("M")) {
            val numStr = trimmed.removeSuffix("M")
            val value = numStr.toDoubleOrNull() ?: 0.0
            (value * 1_000_000).toLong()
        } else if (trimmed.endsWith("K")) {
            val numStr = trimmed.removeSuffix("K")
            val value = numStr.toDoubleOrNull() ?: 0.0
            (value * 1_000).toLong()
        } else {
            val cleanStr = trimmed.replace(",", "")
            cleanStr.toLongOrNull() ?: 0L
        }
    } catch (e: Exception) {
        0L
    }
}


