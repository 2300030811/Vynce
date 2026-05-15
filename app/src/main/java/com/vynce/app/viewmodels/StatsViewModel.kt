package com.vynce.app.viewmodels

import com.vynce.app.constants.StatPeriod
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// redoing this whole feature later, plz ignore the slop code
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    database: MusicDatabase,
) : DatabaseViewModel(database) {
    val statPeriod = MutableStateFlow(StatPeriod.`1_WEEK`)

    val mostPlayedSongs = statPeriod.flatMapLatest { period ->
        database.mostPlayedSongs(period.toTimeMillis())
    }.asStateFlow(emptyList())

    val mostPlayedArtists = statPeriod.flatMapLatest { period ->
        val time = period.toLocalDateTime()
        database.mostPlayedArtists(time.year, time.month.value).map { artists ->
            artists
        }
    }.asStateFlow(emptyList())


    val mostPlayedAlbums = statPeriod.flatMapLatest { period ->
        database.mostPlayedAlbums(period.toTimeMillis())
    }.asStateFlow(emptyList())

    val lostMemories = flowOf(LocalDateTime.now()).map { now ->
        val dateMonth = String.format(Locale.ROOT, "%02d-%02d", now.monthValue, now.dayOfMonth)
        val currentYear = now.year.toString()
        database.lostMemories(dateMonth, currentYear)
    }.flatMapLatest { it }
        .asStateFlow(emptyList())

    // Enhanced history: most frequent songs (by session count, not just play time)
    val mostFrequentSongs = statPeriod.flatMapLatest { period ->
        database.mostFrequentSongs(period.toTimeMillis(), 10)
    }.asStateFlow(emptyList())

    // Enhanced history: monthly top songs
    private val _monthlyTopSongs = MutableStateFlow<List<Song>>(emptyList())
    val monthlyTopSongs: StateFlow<List<Song>> = _monthlyTopSongs

    init {
        launchIO {
            val monthStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MM"))
            database.monthlyTopSongs(monthStr, 20).collect { songs ->
                _monthlyTopSongs.value = songs
            }
        }
    }

    // Listening streak: consecutive days with at least one play
    private val _listeningStreak = MutableStateFlow(0)
    val listeningStreak: StateFlow<Int> = _listeningStreak

    init {
        launchIO {
            database.events().collect { events ->
                val streak = calculateStreak(events.map { it.event.timestamp.toLocalDate() })
                _listeningStreak.value = streak
            }
        }
    }

    // Total unique songs played
    private val _uniqueSongsCount = MutableStateFlow(0)
    val uniqueSongsCount: StateFlow<Int> = _uniqueSongsCount

    init {
        launchIO {
            database.events().collect { events ->
                val uniqueCount = events.map { it.event.songId }.distinct().size
                _uniqueSongsCount.value = uniqueCount
            }
        }
    }

    // Total listening time in hours
    private val _totalListeningHours = MutableStateFlow(0f)
    val totalListeningHours: StateFlow<Float> = _totalListeningHours

    init {
        launchIO {
            database.events().collect { events ->
                val totalMs = events.sumOf { it.event.playTime }
                val hours = totalMs / 3600000f
                _totalListeningHours.value = hours
            }
        }
    }

    private fun calculateStreak(dates: List<LocalDate>): Int {
        if (dates.isEmpty()) return 0

        val uniqueDates = dates.toSortedSet().toList()
        val today = LocalDate.now()
        var streak = 0
        var checkDate = today

        // Check if today has any plays, otherwise start from yesterday
        if (!uniqueDates.contains(today)) {
            checkDate = today.minusDays(1)
            if (!uniqueDates.contains(checkDate)) return 0
        }

        while (uniqueDates.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        return streak
    }
}

