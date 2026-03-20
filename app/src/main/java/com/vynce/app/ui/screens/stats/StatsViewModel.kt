package com.vynce.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.SongWithPlayCount
import com.vynce.app.db.ArtistWithPlayCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val database: MusicDatabase
) : ViewModel() {

    // Query the existing Event/History tables OuterTune already has
    val topSongs: StateFlow<List<SongWithPlayCount>> = database.delegate.dao.mostPlayedSongs(limit = 10)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topArtists: StateFlow<List<ArtistWithPlayCount>> = database.delegate.dao.mostPlayedArtists(limit = 5)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalMinutes: StateFlow<Int> = database.delegate.dao.totalListeningMinutes()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val totalSongs: StateFlow<Int> = database.delegate.dao.totalPlayCount()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
}
