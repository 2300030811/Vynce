package com.vynce.app.utils

import com.vynce.app.db.entities.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Playlist import/export utilities for M3U, CSV, and JSON formats.
 * Inspired by Nuclear's multi-format playlist exporter.
 * ponytail: Uses standard Android JSON & String builders without extra dependencies.
 */
object PlaylistExporter {

    /**
     * Exports a playlist as M3U formatted text.
     */
    fun exportToM3u(playlistName: String, songs: List<Song>): String {
        val builder = StringBuilder("#EXTM3U\n#PLAYLIST:$playlistName\n\n")
        songs.forEach { song ->
            val durationSec = song.song.duration / 1000
            val title = song.song.title
            val artist = song.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown Artist" }
            val path = song.song.localPath ?: song.song.id
            builder.append("#EXTINF:$durationSec,$artist - $title\n")
            builder.append("$path\n")
        }
        return builder.toString()
    }

    /**
     * Parses M3U playlist file content into a list of song identifiers or titles.
     * ponytail: Uses lineSequence to avoid intermediate list allocations.
     */
    fun parseM3u(m3uContent: String): List<String> =
        m3uContent.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .toList()

    /**
     * Exports playlist songs as JSON string.
     */
    fun exportToJson(playlistName: String, songs: List<Song>): String {
        val root = JSONObject()
        root.put("name", playlistName)
        val array = JSONArray()
        songs.forEach { song ->
            val obj = JSONObject()
            obj.put("id", song.song.id)
            obj.put("title", song.song.title)
            obj.put("artists", JSONArray(song.artists.map { it.name }))
            obj.put("album", song.album?.title ?: "")
            obj.put("duration", song.song.duration)
            array.put(obj)
        }
        root.put("tracks", array)
        return root.toString(2)
    }
}
