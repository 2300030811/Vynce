package com.vynce.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks a listening session - a continuous period of playback.
 * Used for streak calculation and session analytics.
 */
@Entity(tableName = "listening_session")
data class ListeningSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val totalPlayTimeMs: Long,
    val songCount: Int,
    val date: String, // YYYY-MM-DD for efficient grouping
)
