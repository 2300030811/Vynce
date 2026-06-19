package com.vynce.app.data.search

import com.vynce.app.models.UnifiedSearchResult
import com.vynce.app.models.TopResult
import com.zionhuang.jiosaavn.JioSaavn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.zionhuang.jiosaavn.SaavnSong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val searchCacheManager: SearchCacheManager
) {
    // Coalescing map to prevent duplicate concurrent network requests for the same query
    private val activeRequests = mutableMapOf<String, Deferred<UnifiedSearchResult>>()
    private val mutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun searchSongs(query: String): List<SaavnSong> {
        return performSearch(query).songs
    }

    suspend fun performSearch(query: String): UnifiedSearchResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return UnifiedSearchResult(trimmedQuery, emptyList(), emptyList(), emptyList(), emptyList(), null)
        }

        // 1. Check cache first
        val cached = searchCacheManager.get(trimmedQuery)
        if (cached != null) {
            return cached
        }

        // 2. Coalesce identical requests using an independent scope
        // This prevents JobCancellationException if one caller (e.g. Suggestion VM) cancels its flow.
        val normalizedKey = trimmedQuery.lowercase()
        val deferred = mutex.withLock {
            activeRequests[normalizedKey] ?: repositoryScope.async {
                try {
                    executeNetworkSearch(trimmedQuery)
                } finally {
                    mutex.withLock {
                        activeRequests.remove(normalizedKey)
                    }
                }
            }.also {
                activeRequests[normalizedKey] = it
            }
        }

        val result = deferred.await()
        searchCacheManager.put(trimmedQuery, result)
        return result
    }

    private suspend fun executeNetworkSearch(q: String): UnifiedSearchResult = supervisorScope {
        val songsDeferred = async { JioSaavn.searchSongs(q) }
        val artistsDeferred = async { JioSaavn.searchArtists(q) }
        val albumsDeferred = async { JioSaavn.searchAlbums(q) }
        val playlistsDeferred = async { JioSaavn.searchPlaylists(q) }

        val songs = try { songsDeferred.await() } catch (e: Exception) { emptyList() }
        val artists = try { artistsDeferred.await() } catch (e: Exception) { emptyList() }
        val albums = try { albumsDeferred.await() } catch (e: Exception) { emptyList() }
        val playlists = try { playlistsDeferred.await() } catch (e: Exception) { emptyList() }

        // Deduplication
        val dedupedSongs = songs.distinctBy { it.id }
        val dedupedArtists = artists.distinctBy { it.id }
        val dedupedAlbums = albums.distinctBy { it.id }
        val dedupedPlaylists = playlists.distinctBy { it.id }

        // Custom Ranking (preserving scores to avoid recomputing and losing original index context)
        val rankedSongsWithScores = dedupedSongs.mapIndexed { index, song ->
            song to SearchRanker.computeSongScore(song, q, index)
        }.sortedByDescending { it.second }
        val rankedSongs = rankedSongsWithScores.map { it.first }

        val rankedArtistsWithScores = dedupedArtists.mapIndexed { index, artist ->
            artist to SearchRanker.computeArtistScore(artist, q, index)
        }.sortedByDescending { it.second }
        val rankedArtists = rankedArtistsWithScores.map { it.first }

        val rankedAlbumsWithScores = dedupedAlbums.mapIndexed { index, album ->
            album to SearchRanker.computeAlbumScore(album, q, index)
        }.sortedByDescending { it.second }
        val rankedAlbums = rankedAlbumsWithScores.map { it.first }

        val rankedPlaylistsWithScores = dedupedPlaylists.mapIndexed { index, playlist ->
            playlist to SearchRanker.computePlaylistScore(playlist, q, index)
        }.sortedByDescending { it.second }
        val rankedPlaylists = rankedPlaylistsWithScores.map { it.first }

        rankedArtistsWithScores.take(10).forEachIndexed { index, (artist, score) ->
            android.util.Log.d(
                "SearchRank",
                "Artist[$index] name=${artist.name} score=$score image=${artist.image}"
            )
        }

        val songCandidates = rankedSongsWithScores.take(5)
        val artistCandidates = rankedArtistsWithScores.take(5)
        val albumCandidates = rankedAlbumsWithScores.take(5)
        val playlistCandidates = rankedPlaylistsWithScores.take(5)

        val normalizedQuery = q.trim().lowercase()
        
        val candidatesList = mutableListOf<ScoredCandidate>()

        // 1. Songs
        songCandidates.forEach { (song, score) ->
            val normalizedName = song.name.trim().lowercase()
            val matchLevel = computeMatchLevel(normalizedName, normalizedQuery)
            val finalScore = score + 150
            candidatesList.add(ScoredCandidate(song.name, matchLevel, finalScore, TopResult.Song(song)))
        }

        // 2. Artists
        artistCandidates.forEach { (artist, score) ->
            val normalizedName = artist.name.trim().lowercase()
            val matchLevel = computeMatchLevel(normalizedName, normalizedQuery)
            val finalScore = score + 200
            candidatesList.add(ScoredCandidate(artist.name, matchLevel, finalScore, TopResult.Artist(artist)))
        }

        // 3. Albums
        albumCandidates.forEach { (album, score) ->
            val normalizedName = album.name.trim().lowercase()
            val matchLevel = computeMatchLevel(normalizedName, normalizedQuery)
            val finalScore = score + 100
            candidatesList.add(ScoredCandidate(album.name, matchLevel, finalScore, TopResult.Album(album)))
        }

        // 4. Playlists
        playlistCandidates.forEach { (playlist, score) ->
            val normalizedName = playlist.name.trim().lowercase()
            val matchLevel = computeMatchLevel(normalizedName, normalizedQuery)
            val finalScore = score + 50
            candidatesList.add(ScoredCandidate(playlist.name, matchLevel, finalScore, TopResult.Playlist(playlist)))
        }

        val sortedCandidates = candidatesList
            .filter { it.matchLevel != MatchLevel.NONE }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.score }
                    .thenBy { MATCH_PRIORITY[it.matchLevel] ?: 5 }
            )

        val winner = sortedCandidates.firstOrNull()

        val topResult = when {
            winner != null -> winner.topResult
            rankedSongs.isNotEmpty() -> TopResult.Song(rankedSongs.first())
            else -> null
        }

        android.util.Log.d(
            "TOP_RESULT_DEBUG",
            "winner=${winner?.name} type=${winner?.topResult}"
        )
        android.util.Log.d(
            "TOP_RESULT_DEBUG",
            "topResult=$topResult"
        )

        UnifiedSearchResult(
            query = q,
            songs = rankedSongs,
            artists = rankedArtists,
            albums = rankedAlbums,
            playlists = rankedPlaylists,
            topResult = topResult
        )
    }

    private fun normalizeSearchText(value: String): String {
        return value
            .lowercase()
            .replace(".", "")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun computeMatchLevel(rawName: String, rawQuery: String): MatchLevel {
        val normalizedName = normalizeSearchText(rawName)
        val normalizedQuery = normalizeSearchText(rawQuery)
        val words = normalizedName.split(Regex("\\s+"))
        return when {
            normalizedName == normalizedQuery -> MatchLevel.EXACT_MATCH
            normalizedName.startsWith(normalizedQuery) -> MatchLevel.STARTS_WITH_MATCH
            words.any { it.startsWith(normalizedQuery) } -> MatchLevel.STARTS_WITH_MATCH
            words.contains(normalizedQuery) -> MatchLevel.WORD_MATCH
            normalizedName.contains(normalizedQuery) -> MatchLevel.CONTAINS_MATCH
            else -> MatchLevel.NONE
        }
    }
}

private enum class MatchLevel {
    EXACT_MATCH,
    STARTS_WITH_MATCH,
    WORD_MATCH,
    CONTAINS_MATCH,
    NONE
}

private val MATCH_PRIORITY = mapOf(
    MatchLevel.EXACT_MATCH to 0,
    MatchLevel.STARTS_WITH_MATCH to 1,
    MatchLevel.WORD_MATCH to 2,
    MatchLevel.CONTAINS_MATCH to 3,
    MatchLevel.NONE to 4
)

private data class ScoredCandidate(
    val name: String,
    val matchLevel: MatchLevel,
    val score: Int,
    val topResult: TopResult
)
