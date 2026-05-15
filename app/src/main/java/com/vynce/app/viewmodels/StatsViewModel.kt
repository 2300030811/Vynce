package com.vynce.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vynce.app.constants.StatPeriod
import com.vynce.app.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject

// redoing this whole feature later, plz ignore the slop code
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    val statPeriod = MutableStateFlow(StatPeriod.`1_WEEK`)

    val mostPlayedSongs = statPeriod.flatMapLatest { period ->
        database.mostPlayedSongs(period.toTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedArtists = statPeriod.flatMapLatest { period ->
        val time = period.toLocalDateTime()
        database.mostPlayedArtists(time.year, time.month.value).map { artists ->
            artists
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    val mostPlayedAlbums = statPeriod.flatMapLatest { period ->
        database.mostPlayedAlbums(period.toTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val lostMemories = flowOf(LocalDateTime.now()).map { now ->
        val dateMonth = String.format(Locale.ROOT, "%02d-%02d", now.monthValue, now.dayOfMonth)
        val currentYear = now.year.toString()
        database.lostMemories(dateMonth, currentYear)
    }.flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

