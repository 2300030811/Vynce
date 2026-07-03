package com.vynce.app.utils

import androidx.documentfile.provider.DocumentFile

/**
 * Normalizes media IDs extracted from filenames.
 *
 * Filesystem providers sanitize special characters (e.g., colons to underscores)
 * in filenames. This utility restores the original media ID format so it matches
 * what's stored in the database.
 *
 * Example: "saavn_12345" -> "saavn:12345"
 */
object DownloadIdNormalizer {
    fun normalize(id: String): String =
        if (id.startsWith("saavn_"))
            "saavn:" + id.removePrefix("saavn_")
        else
            id
}

/**
 * Safely extracts the media ID from any [DocumentFile]'s display name.
 *
 * Downloaded files are named like `"Song Title [mediaId].mka"`. This property
 * parses out the `[mediaId]` portion and normalizes it (e.g., `saavn_ID` → `saavn:ID`).
 *
 * Works with both the custom [TreeDocumentFileOt] and the standard library
 * [DocumentFile] implementations, avoiding the ClassCastException that occurs
 * when casting to [TreeDocumentFileOt] for files created via [DocumentFile.fromTreeUri].
 */
val DocumentFile.mediaId: String?
    get() {
        val name = name ?: return null
        val start = name.lastIndexOf('[')
        val end = name.lastIndexOf(']')
        if (start < 0 || end < 0 || start >= end) return null
        return DownloadIdNormalizer.normalize(name.substring(start + 1, end))
    }
