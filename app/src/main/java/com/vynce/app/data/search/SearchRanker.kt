package com.vynce.app.data.search

import com.zionhuang.jiosaavn.SaavnSong
import com.zionhuang.jiosaavn.SaavnArtist
import com.zionhuang.jiosaavn.SaavnAlbum
import com.zionhuang.jiosaavn.SaavnPlaylist

object SearchRanker {

    private val PENALTY_TERMS = listOf(
        "remix", "live", "slowed", "reverb", "cover", "lofi", "lo-fi",
        "instrumental", "karaoke", "tribute"
    )

    private val ARTIST_AGGREGATE_TERMS = listOf(
        "feat.", "feat ", "featuring", " ft.", " ft "
    )

    fun computeBaseMatchScore(name: String, query: String): Int {
        val nName = name.trim().lowercase()
        val nQuery = query.trim().lowercase()
        if (nName.isEmpty() || nQuery.isEmpty()) return 0

        var score = 0

        // 1. Exact match
        if (nName == nQuery) {
            score += 1000
        }
        // 2. Starts-with match (Name)
        else if (nName.startsWith(nQuery)) {
            score += 500
        }

        // Word-based match
        val words = nName.split(Regex("[\\s\\-_/(),.]+")).filter { it.isNotEmpty() }
        val queryWords = nQuery.split(Regex("[\\s\\-_/(),.]+")).filter { it.isNotEmpty() }

        if (queryWords.size == 1) {
            val qWord = queryWords.first()
            if (words.contains(qWord)) {
                score += 400 // Whole-word match
            } else if (words.any { it.startsWith(qWord) }) {
                score += 200 // Word starts with query
            } else if (nName.contains(qWord)) {
                score += 100 // Generic contains
            }
        } else {
            // Multi-word query
            val containsAllQueryWords = queryWords.all { qw -> words.contains(qw) }
            if (containsAllQueryWords) {
                score += 400 // Whole-word match for all query words
            } else {
                val matchAllStarts = queryWords.all { qw -> words.any { w -> w.startsWith(qw) } }
                if (matchAllStarts) {
                    score += 200
                } else if (nName.contains(nQuery)) {
                    score += 100
                }
            }
        }
        return score
    }

    fun computeSongScore(song: SaavnSong, query: String, originalIndex: Int): Int {
        var score = computeBaseMatchScore(song.name, query)
        val nName = song.name.trim().lowercase()
        val nQuery = query.trim().lowercase()

        // Penalize remix/live versions if the query does not ask for it
        for (term in PENALTY_TERMS) {
            if (nName.contains(term) && !nQuery.contains(term)) {
                score -= 300
            }
        }

        // Tie-breaker based on original index (popularity proxy)
        score += (100 - originalIndex) * 10

        return score
    }

    internal fun normalizeText(value: String): String {
        return value
            .lowercase()
            .replace(".", "")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun computeArtistScore(artist: SaavnArtist, query: String, originalIndex: Int): Int {
        val normalizedName = normalizeText(artist.name)
        val normalizedQuery = normalizeText(query)
        var score = computeBaseMatchScore(normalizedName, normalizedQuery)

        // Popularity boost based on followers (usually 0 from search API)
        if (artist.followerCount != "0") {
            val followers = parseFollowerCount(artist.followerCount)
            val popularityBoost = (if (followers > 0) Math.log10(followers.toDouble() + 1.0) * 100 else 0.0).toInt()
            score += popularityBoost
        }

        // Heuristic 1: Canonical expansion boost (e.g. "anirudh" -> "Anirudh Ravichander")
        val queryWords = query.trim().split(Regex("[\\s\\-_/(),.]+")).filter { it.isNotEmpty() }
        if (queryWords.size == 1) {
            val nName = artist.name.trim().lowercase()
            val nQuery = query.trim().lowercase()
            if (nName.startsWith(nQuery) && nName.length > nQuery.length + 3) {
                score += 800
            }
        }

        // Heuristic 2: Real image boost (JioSaavn default gray placeholders indicate low-quality/incomplete metadata)
        val hasPlaceholderImage = artist.image.isBlank() || 
                                  artist.image.lowercase().contains("default") || 
                                  artist.image.lowercase().contains("placeholder") ||
                                  artist.image.lowercase().contains("unknown")
        if (!hasPlaceholderImage) {
            score += 500
        }

        // Heuristic 3: Canonical punctuation boost (favors "A.R. Rahman" over "AR Rahman")
        if (artist.name.contains(".")) {
            score += 300
        }

        // Heuristic 4: Canonical Artist vs Aggregate / Collaboration
        val lowerArtistName = artist.name.lowercase()
        val isAggregate = ARTIST_AGGREGATE_TERMS.any { lowerArtistName.contains(it) } ||
                          lowerArtistName.count { it == '&' } >= 2 ||
                          lowerArtistName.count { it == ',' } >= 2
        
        if (isAggregate) {
            score -= 1000
        } else {
            score += 500
        }

        // Tie-breaker
        score += (100 - originalIndex) * 10

        return score
    }

    fun computeAlbumScore(album: SaavnAlbum, query: String, originalIndex: Int): Int {
        var score = computeBaseMatchScore(album.name, query)
        val nName = album.name.trim().lowercase()
        val nQuery = query.trim().lowercase()

        // Penalize remix/live versions if the query does not ask for it
        for (term in PENALTY_TERMS) {
            if (nName.contains(term) && !nQuery.contains(term)) {
                score -= 300
            }
        }

        // Tie-breaker
        score += (100 - originalIndex) * 10

        return score
    }

    fun computePlaylistScore(playlist: SaavnPlaylist, query: String, originalIndex: Int): Int {
        var score = computeBaseMatchScore(playlist.name, query)

        // Popularity boost based on followers
        val followers = parseFollowerCount(playlist.followerCount)
        val popularityBoost = (if (followers > 0) Math.log10(followers.toDouble() + 1.0) * 100 else 0.0).toInt()
        score += popularityBoost

        // Tie-breaker
        score += (100 - originalIndex) * 10

        return score
    }

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
}
