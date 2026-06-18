/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.viewmodels

import android.content.Context
import com.vynce.app.constants.AiApiKeyKey
import com.vynce.app.constants.AiModelKey
import com.vynce.app.data.ai.AiPlaylistGenerator
import com.vynce.app.data.ai.GroqOrchestrator
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.Song
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.get
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
) : DatabaseViewModel(database) {

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState

    private val _generatedPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val generatedPlaylist: StateFlow<List<Song>> = _generatedPlaylist

    val promptSuggestions = listOf(
        "🎧 Chill vibes for studying",
        "🏃 High energy workout mix",
        "🌅 Sunset drive playlist",
        "😴 Calm songs for sleeping",
        "🎉 Party bangers",
        "💔 Sad and emotional songs",
        "🎸 Rock classics",
        "☕ Morning coffee background",
        "🧘 Meditation & focus",
        "🌧️ Rainy day mood",
    )

    fun generatePlaylist(prompt: String) {
        if (prompt.isBlank()) return

        _uiState.value = AiUiState.Loading

        viewModelScope.launch {
            var apiKey = context.dataStore.get(AiApiKeyKey, "")
            if (apiKey.isBlank()) {
                apiKey = com.vynce.app.BuildConfig.GROQ_API_KEY
            }
            val model = context.dataStore.get(AiModelKey, GroqOrchestrator.DEFAULT_MODEL)

            if (apiKey.isBlank()) {
                _uiState.value = AiUiState.Error(
                    "No API key set. Get a free Groq API key at console.groq.com and add it in Settings → AI Integration."
                )
                return@launch
            }

            val orchestrator = GroqOrchestrator(apiKey, model)
            val generator = AiPlaylistGenerator(orchestrator)

            // Get all songs from database
            val allSongs = database.songsByRowIdAsc().first()

            if (allSongs.isEmpty()) {
                _uiState.value = AiUiState.Error("Your library is empty. Add some songs first!")
                return@launch
            }

            val result = generator.generate(
                prompt = prompt,
                allSongs = allSongs,
            )

            result.fold(
                onSuccess = { songs ->
                    _generatedPlaylist.value = songs
                    _uiState.value = AiUiState.Success(songs.size)
                },
                onFailure = { error ->
                    _uiState.value = AiUiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    fun clearPlaylist() {
        _generatedPlaylist.value = emptyList()
        _uiState.value = AiUiState.Idle
    }

    sealed class AiUiState {
        data object Idle : AiUiState()
        data object Loading : AiUiState()
        data class Success(val songCount: Int) : AiUiState()
        data class Error(val message: String) : AiUiState()
    }
}
