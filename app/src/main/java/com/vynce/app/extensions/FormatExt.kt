/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.extensions

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Format a duration in seconds to MM:SS or HH:MM:SS string.
 */
fun Int.formatDuration(): String {
    val totalSeconds = this
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * Format milliseconds to MM:SS string.
 */
fun Long.formatTime(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

/**
 * Format a number of bytes to human-readable size string.
 */
fun Long.formatFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(this.toDouble()) / ln(1024.0)).toInt()
    return String.format(Locale.ROOT, "%.1f %s", this / 1024.0.pow(digitGroups), units[digitGroups])
}

/**
 * Format a number to abbreviated string (e.g., 1.2K, 3.4M).
 */
fun Int.formatAbbreviated(): String {
    return when {
        this < 1000 -> toString()
        this < 1_000_000 -> String.format(Locale.ROOT, "%.1fK", this / 1000.0)
        this < 1_000_000_000 -> String.format(Locale.ROOT, "%.1fM", this / 1_000_000.0)
        else -> String.format(Locale.ROOT, "%.1fB", this / 1_000_000_000.0)
    }
}

/**
 * Format a number to abbreviated string (e.g., 1.2K, 3.4M).
 */
fun Long.formatAbbreviated(): String = toInt().formatAbbreviated()

/**
 * Format a relative time string (e.g., "2 hours ago", "3 days ago").
 */
fun Long.formatTimeAgo(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        diff < 2_592_000_000 -> "${diff / 604_800_000}w ago"
        else -> "${diff / 2_592_000_000}mo ago"
    }
}

/**
 * Clamp a value between min and max.
 */
fun <T : Comparable<T>> T.clamp(min: T, max: T): T {
    return when {
        this < min -> min
        this > max -> max
        else -> this
    }
}

/**
 * Linear interpolation between two values.
 */
fun Float.lerp(to: Float, fraction: Float): Float = this + (to - this) * fraction

/**
 * Double linear interpolation between two values.
 */
fun Double.lerp(to: Double, fraction: Double): Double = this + (to - this) * fraction

/**
 * Get a random element from the list, or null if empty.
 */
fun <T> List<T>.randomOrNull(): T? = if (isEmpty()) null else get(Random.nextInt(size))

/**
 * Get a random sample of n elements from the list.
 */
fun <T> List<T>.randomSample(n: Int): List<T> {
    if (n >= size) return this
    return shuffled().take(n)
}

/**
 * Remove duplicate words from a string (case-insensitive).
 */
fun String.removeDuplicateWords(): String {
    val words = split(Regex("\\s+"))
    val seen = mutableSetOf<String>()
    return words.filter { word ->
        val lower = word.lowercase()
        seen.add(lower)
    }.joinToString(" ")
}

/**
 * Check if a string is a valid URL.
 */
fun String.isValidUrl(): Boolean {
    val urlPattern = Regex("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$", RegexOption.IGNORE_CASE)
    return urlPattern.matches(this)
}

/**
 * Truncate a string to a maximum length, adding ellipsis if truncated.
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    if (length <= maxLength) return this
    return substring(0, maxLength - ellipsis.length) + ellipsis
}

/**
 * Capitalize the first letter of a string.
 */
fun String.capitalizeFirst(): String {
    if (isEmpty()) return this
    return substring(0, 1).uppercase(Locale.ROOT) + substring(1)
}

/**
 * Convert a string to title case (first letter of each word capitalized).
 */
fun String.toTitleCase(): String {
    return split(" ").joinToString(" ") { word ->
        word.capitalizeFirst()
    }
}

/**
 * Parse a timestamp string (MM:SS or HH:MM:SS) to milliseconds.
 */
fun String.parseTimestampToMilliseconds(): Long {
    val parts = split(":").mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]) * 1000L
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
        else -> 0L
    }
}
