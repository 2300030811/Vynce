/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * AI playlist generator adapted from PixelMusic's AiPlaylistGenerator.
 * Uses Groq for inference against the user's local library.
 */

package com.vynce.app.data.ai

import android.util.Log
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.Song
import kotlinx.serialization.json.Json

/**
 * Generates playlists using AI by matching the user's prompt against their library.
 *
 * Flow:
 * 1. Takes all songs from the database
 * 2. Sends compact metadata to Groq with the user's natural language prompt
 * 3. Groq returns a curated list of song IDs
 * 4. Maps IDs back to playable songs
 */
class AiPlaylistGenerator(
    private val orchestrator: GroqOrchestrator,
) {
    companion object {
        private const val TAG = "AiPlaylistGen"
        private const val MAX_CANDIDATES = 80 // Token budget cap
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * System prompt for playlist generation.
     * Adapted from PixelMusic's AiSystemPromptEngine.
     */
    private val systemPrompt = """
You are Vynce AI, a music curation assistant built into the Vynce music player.

Your task: Given a user's music library and their request, select songs that best match the mood, genre, activity, or theme they describe.

Rules:
1. ONLY return song IDs from the candidate pool provided — never invent IDs
2. Return a JSON array of song ID strings, nothing else
3. Select 10-25 songs unless the user specifies a different count
4. Order songs to create a natural, flowing playlist progression
5. Consider genre, artist variety, and mood coherence
6. If the request is ambiguous, interpret it creatively but stay within the library

Response format: ["id1", "id2", "id3", ...]
Do NOT include any text, explanation, or markdown — ONLY the JSON array.
    """.trimIndent()

    /**
     * Generate a playlist from the user's prompt.
     *
     * @param prompt Natural language description (e.g., "chill vibes for studying")
     * @param allSongs All songs in the user's library
     * @param targetLength Desired playlist length
     * @return List of songs matching the prompt, or failure with user-friendly error
     */
    suspend fun generate(
        prompt: String,
        allSongs: List<Song>,
        targetLength: Int = 20,
    ): Result<List<Song>> {
        if (allSongs.isEmpty()) {
            return Result.failure(Exception("Your library is empty. Add some songs first!"))
        }

        // Take a representative sample to fit token budget
        val candidates = allSongs.shuffled().take(MAX_CANDIDATES)

        // Build compact JSON — only essential fields to minimize tokens
        val songsJson = buildString {
            append("[")
            candidates.forEachIndexed { index, song ->
                val title = song.song.title.replace("\"", "'").take(50)
                val artist = song.artists.firstOrNull()?.name?.replace("\"", "'")?.take(30) ?: "Unknown"
                val album = song.album?.title?.replace("\"", "'")?.take(30) ?: ""
                val genre = song.genre?.firstOrNull()?.title?.replace("\"", "'")?.take(20) ?: ""
                if (index > 0) append(",")
                append("""{"id":"${song.song.id}","t":"$title","a":"$artist"""")
                if (album.isNotBlank()) append(""","al":"$album"""")
                if (genre.isNotBlank()) append(""","g":"$genre"""")
                append("}")
            }
            append("]")
        }

        val userPrompt = """
<request>
<query>$prompt</query>
<target_length>$targetLength tracks</target_length>
<randomness_seed>${System.currentTimeMillis()}</randomness_seed>
</request>
<candidate_pool>
$songsJson
</candidate_pool>
        """.trimIndent()

        Log.d(TAG, "Generating playlist: prompt='$prompt', candidates=${candidates.size}")

        val result = orchestrator.generateContent(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = 0.9f,
        )

        return result.fold(
            onSuccess = { response ->
                val songIds = extractSongIds(response)
                val songMap = allSongs.associateBy { it.song.id }
                val playlist = songIds.mapNotNull { songMap[it] }

                if (playlist.isEmpty()) {
                    Result.failure(Exception(
                        "AI returned song IDs that don't match your library. " +
                        "This can happen with smaller models. Try again or use a different prompt."
                    ))
                } else {
                    Log.d(TAG, "Generated playlist: ${playlist.size} songs")
                    Result.success(playlist)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * Robust JSON array extractor.
     * Handles markdown fences, extra text, and nested brackets.
     * Adapted from PixelMusic's extractPlaylistSongIds.
     */
    private fun extractSongIds(rawResponse: String): List<String> {
        val sanitized = rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()

        // Find the first valid JSON array of strings
        for (startIndex in sanitized.indices) {
            if (sanitized[startIndex] != '[') continue

            var depth = 0
            var inString = false
            var isEscaped = false

            for (index in startIndex until sanitized.length) {
                val char = sanitized[index]

                if (inString) {
                    if (isEscaped) { isEscaped = false; continue }
                    when (char) {
                        '\\' -> isEscaped = true
                        '"' -> inString = false
                    }
                    continue
                }

                when (char) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = sanitized.substring(startIndex, index + 1)
                            val decoded = runCatching {
                                json.decodeFromString<List<String>>(candidate)
                            }
                            if (decoded.isSuccess) return decoded.getOrThrow()
                            break
                        }
                    }
                }
            }
        }

        throw IllegalArgumentException(
            "AI returned an invalid format. Expected a JSON array of song IDs. " +
            "Try a simpler prompt or a more capable model."
        )
    }
}
