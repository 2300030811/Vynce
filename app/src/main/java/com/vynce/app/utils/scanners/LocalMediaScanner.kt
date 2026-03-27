/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.utils.scanners

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import com.vynce.app.constants.AutomaticScannerKey
import com.vynce.app.constants.ENABLE_FFMETADATAEX
import com.vynce.app.constants.SCANNER_DEBUG
import com.vynce.app.constants.SYNC_SCANNER
import com.vynce.app.constants.ScannerImpl
import com.vynce.app.constants.ScannerImplKey
import com.vynce.app.constants.ScannerM3uMatchCriteria
import com.vynce.app.constants.ScannerMatchCriteria
import com.vynce.app.constants.scannerWhitelistExts
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.AlbumEntity
import com.vynce.app.db.entities.Artist
import com.vynce.app.db.entities.ArtistEntity
import com.vynce.app.db.entities.FormatEntity
import com.vynce.app.db.entities.GenreEntity
import com.vynce.app.db.entities.Song
import com.vynce.app.db.entities.SongAlbumMap
import com.vynce.app.db.entities.SongArtistMap
import com.vynce.app.db.entities.SongEntity
import com.vynce.app.db.entities.SongGenreMap
import com.vynce.app.models.CulmSongs
import com.vynce.app.models.DirectoryTree
import com.vynce.app.models.MediaMetadata
import com.vynce.app.models.SongTempData
import com.vynce.app.models.toMediaMetadata
import com.vynce.app.ui.utils.ARTIST_SEPARATORS
import com.vynce.app.utils.closestAlbumMatch
import com.vynce.app.utils.closestMatch
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.lmScannerCoroutine
import com.vynce.app.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class LocalMediaScanner(scannerImpl: ScannerImpl) {
    private val TAG = LocalMediaScanner::class.simpleName.toString()
    private var advancedScannerImpl: MetadataScanner = when (scannerImpl) {
        ScannerImpl.TAGLIB -> TagLibScanner()
        ScannerImpl.FFMPEG_EXT -> if (ENABLE_FFMETADATAEX) FFmpegScanner() else TagLibScanner()
        ScannerImpl.MEDIASTORE -> MediaStoreExtractor() // unused
    }

    init {
        Log.i(
            TAG,
            "Creating scanner instance with scannerImpl:  ${advancedScannerImpl.javaClass.name}, requested: $scannerImpl"
        )
    }

    suspend fun advancedScan(
        context: Context,
        uri: Uri,
    ): SongTempData {
        val file = fileFromUri(context, uri) ?: throw IOException("Could not access file")
        return advancedScan(file)
    }

    /**
     * Compiles a song with all it's necessary metadata. Unlike MediaStore,
     * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
     */
    suspend fun advancedScan(
        file: File,
    ): SongTempData {
        val path = file.absolutePath
        try {
            if (!file.exists()) throw IOException("File not found")

            // decide which scanner to use
            val ffmpegData =
                if (advancedScannerImpl is TagLibScanner || (ENABLE_FFMETADATAEX && advancedScannerImpl is FFmpegScanner)) {
                    advancedScannerImpl.getAllMetadataFromFile(file)
                } else {
                    throw RuntimeException("Unsupported extractor")
                }

            return ffmpegData
        } catch (e: Exception) {
            when (e) {
                is IOException, is IllegalArgumentException, is IllegalStateException, is SecurityException -> {
                    if (SCANNER_DEBUG) {
                        e.printStackTrace()
                    }
                    throw InvalidAudioFileException("Failed to access file or not in a playable format: ${e.message} for: $path")
                }

                else -> {
                    if (SCANNER_DEBUG) {
                        Log.w(TAG, "ERROR READING METADATA: ${e.message} for: $path")
                        e.printStackTrace()
                    }

                    // we still want the song to be playable even if metadata extractor fails
                    return SongTempData(
                        Song(
                            SongEntity(
                                SongEntity.generateSongId(),
                                path.substringAfterLast('/'),
                                thumbnailUrl = null,
                                isLocal = true,
                                inLibrary = LocalDateTime.now(),
                                localPath = path
                            ),
                            artists = ArrayList()
                        ),
                        null
                    )
                }
            }
        }
    }


    /**
     * Scan the given scan paths for songs given a list of paths to scan for.
     * This will replace all data in the database for a given song.
     *
     * @param scanPaths List of whitelist paths to scan under. This assumes
     * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
     * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun scanLocal(
        context: Context,
        scanPaths: String,
        excludedScanPaths: String,
    ): List<Uri> {
        val songs = ArrayList<Uri>()
        Log.i(TAG, "------------ SCAN: Starting Full Scanner ------------")
        scannerState.value = 1
        scannerProgressProbe.value = 0
        scannerProgressTotal.value = 0
        scannerProgressCurrent.value = -1

        val scanPaths = uriListFromString(scanPaths)
        val excludedScanPaths = uriListFromString(excludedScanPaths)

        getScanFiles(scanPaths, excludedScanPaths, context).forEach { uri ->
            if (SCANNER_DEBUG)
                Log.v(TAG, "PATH: $uri")

            songs.add(uri)
        }

        scannerState.value = 0
        Log.i(TAG, "------------ SCAN: Finished Full Scanner ------------")
        return songs.toList()
    }

    /**
     * Update the Database with local files
     *
     * @param database
     * @param newSongs
     * @param matchStrength How lax should the scanner be
     * @param strictFileNames Whether to consider file names
     * @param refreshExisting Setting this this to true will updated existing songs
     * with new information, else existing song's data will not be touched, regardless
     * whether it was actually changed on disk
     *
     * Inserts a song if not found
     * Updates a song information depending on if refreshExisting value
     */
    suspend fun syncDB(
        database: MusicDatabase,
        newSongs: java.util.ArrayList<SongTempData>,
        matchStrength: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
        refreshExisting: Boolean = false,
        noDisable: Boolean = false
    ) {
        if (scannerState.value > 0) {
            Log.i(TAG, "------------ SYNC: Scanner in use. Aborting Local Library Sync ------------")
            return
        }
        Log.i(TAG, "------------ SYNC: Starting Local Library Sync ------------")
        scannerState.value = 3
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0
        // deduplicate
        val finalSongs = ArrayList<SongTempData>()
        if (strictFilePaths) {
            finalSongs.addAll(newSongs)
        } else {
            newSongs.forEach { song ->
                if (finalSongs.none { s -> compareSong(song.song, s.song, matchStrength, strictFileNames) }) {
                    finalSongs.add(song)
                }
            }
        }
        Log.d(TAG, "Entries to process: ${newSongs.size}. After dedup: ${finalSongs.size}")
        scannerProgressTotal.value = finalSongs.size
        val mod = if (newSongs.size < 200) {
            30
        } else if (newSongs.size < 800) {
            70
        } else {
            140
        }
        val allLocalSongsList = database.allLocalDbSongs()
        // Index existing songs for much faster lookup
        val songsByPath = allLocalSongsList.filter { it.song.localPath != null }.associateBy { it.song.localPath!! }
        val songsByTitle = allLocalSongsList.groupBy { it.song.title.lowercase() }

        // Caches for metadata entities to avoid repeated fuzzy searches
        val artistCache = mutableMapOf<String, ArtistEntity?>()
        val albumCache = mutableMapOf<String, AlbumEntity?>()
        val genreCache = mutableMapOf<String, GenreEntity?>()

        // Sync in batches of 100 to balance performance and UI responsiveness
        var runs = 0
        finalSongs.chunked(100).forEach { chunk ->
            if (scannerRequestCancel) {
                throw ScannerAbortException("Scanner canceled during Local Library Sync")
            }

            database.runInTransaction {
                chunk.forEach { songData ->
                    val song = songData.song
                    runs++
                    if (runs % mod == 0) {
                        scannerProgressCurrent.value = runs
                    }

                    // 1. Try exact path match first (fastest)
                    val pathMatch = song.song.localPath?.let { songsByPath[it] }
                    
                    // 2. Fallback to similar song matching if no path match
                    val songMatchList = if (pathMatch != null) {
                        listOf(pathMatch)
                    } else {
                        songsByTitle[song.song.title.lowercase()]?.filter {
                            compareSong(it, song, matchStrength, strictFileNames, strictFilePaths)
                        } ?: emptyList()
                    }

                    if (songMatchList.isNotEmpty()) {
                        val oldSong = songMatchList.first().song
                        val songToUpdate = song.song.copy(id = oldSong.id, localPath = song.song.localPath)

                        if (!refreshExisting) {
                            if (oldSong.inLibrary == null || oldSong.localPath == null) {
                                update(songToUpdate)
                                if (songData.format != null) {
                                    upsert(songData.format.copy(id = songToUpdate.id))
                                }
                            } else if (oldSong.localPath != songToUpdate.localPath) {
                                updateLocalSongPath(songToUpdate.id, songToUpdate.inLibrary, songToUpdate.localPath)
                            }
                            return@forEach
                        }
                        
                        // Full rescan branch
                        val artistsToDo = song.artists.mapIndexed { index, artist ->
                            val dbArtist = artistCache.getOrPut(artist.name) {
                                val dbQuery = localArtistsByNameFuzzy(artist.name).sortedBy { it.name.length }
                                closestMatch(artist.name, dbQuery)
                            }
                            Pair(dbArtist, artist)
                        }

                        val genreToDo = song.genre?.map { genre ->
                             genreCache.getOrPut(genre.title) {
                                localGenreByNameFuzzy(genre.title).firstOrNull()
                            } to genre
                        } ?: emptyList()

                        val albumToDo = song.album?.let { album ->
                            val dbAlbum = albumCache.getOrPut(album.title) {
                                val dbQuery = localAlbumsByNameFuzzy(album.title).sortedBy { it.title.length }
                                closestAlbumMatch(album.title, dbQuery)
                            }
                            Pair(dbAlbum, album)
                        }

                        update(
                            songToUpdate.copy(
                                albumId = albumToDo?.first?.id ?: albumToDo?.second?.id,
                                albumName = albumToDo?.first?.title ?: albumToDo?.second?.title
                            )
                        )
                        if (songData.format != null) {
                            upsert(songData.format.copy(id = songToUpdate.id))
                        }

                        unlinkSongArtists(songToUpdate.id)
                        unlinkSongAlbums(songToUpdate.id)
                        unlinkSongGenres(songToUpdate.id)

                        artistsToDo.forEachIndexed { index, item ->
                            val finalArtist = if (item.first == null) {
                                insert(item.second)
                                item.second
                            } else item.first!!
                            insert(SongArtistMap(songToUpdate.id, finalArtist.id, index))
                        }

                        genreToDo.forEachIndexed { index, item ->
                            val finalGenre = if (item.first == null) {
                                insert(item.second)
                                item.second
                            } else item.first!!
                            insert(SongGenreMap(songToUpdate.id, finalGenre.id, index))
                        }

                        albumToDo?.let { album ->
                            val finalAlbumId = if (album.first == null) {
                                insert(album.second)
                                album.second.id
                            } else {
                                update(
                                    album.first!!.copy(
                                        thumbnailUrl = album.second.thumbnailUrl,
                                        songCount = album.first!!.songCount + 1
                                    )
                                )
                                album.first!!.id
                            }
                            insert(SongAlbumMap(songToUpdate.id, finalAlbumId, 0))
                        }
                    } else {
                        // Brand new song
                        insert(song.toMediaMetadata())
                        songData.format?.let {
                            upsert(it.copy(id = song.song.id))
                        }
                    }
                }
            }
        }

        scannerProgressCurrent.value = scannerProgressTotal.value
        // do not delete songs from database automatically, we just disable them
        if (!noDisable) {
            finalize(database)
            disableSongs(finalSongs.map { it.song }, database)
        }
        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Local Library Sync ------------")
    }

    /**
     * A faster scanner implementation that adds new songs to the database,
     * and does not touch older songs entries (apart from removing
     * inacessable songs from libaray).
     *
     * No remote artist lookup is done
     *
     * WARNING: cachedDirectoryTree is not refreshed and may lead to inconsistencies.
     * It is highly recommend to rebuild the tree after scanner operation
     *
     * @param newSongs List of songs. This is expecting a barebones DirectoryTree
     * (only paths are necessary), thus you may use the output of refreshLocal().toList()
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun quickSync(
        context: Context,
        database: MusicDatabase,
        newSongs: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
    ) {
        Log.i(TAG, "------------ SYNC: Starting Quick (additive delta) Library Sync ------------")
        Log.d(TAG, "Entries to process: ${newSongs.size}")
        scannerState.value = 2
        scannerProgressTotal.value = newSongs.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0

        Log.d(TAG, "Scanning for files...")
        // get list of all songs in db, then get songs unknown to the database
        // TODO: duplicate songs with different paths will cycle through paths, causing it to be synced instead of ignored...
        val allSongs = database.allLocalSongs().fastMapNotNull { it.song.localPath }.toSet()
        val converted = newSongs.fastMapNotNull { fileFromUri(context, it)?.absolutePath }
        val delta = converted.minus(allSongs)
        Log.d(TAG, "Songs found: ${delta.size}")
        val mod = if (newSongs.size < 20) {
            2
        } else if (newSongs.size < 50) {
            8
        } else {
            20
        }

        val finalSongs = ArrayList<SongTempData>()
        val scannerJobs = ArrayList<Deferred<SongTempData?>>()

        // Get song basic metadata
        delta.forEach { s ->
            if (scannerRequestCancel) {
                Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
            }

            if (SCANNER_DEBUG) {
                Log.v(TAG, "PATH: $s")
            }

            if (!SYNC_SCANNER) {
                // use async scanner
                runBlocking {
                    scannerJobs.add(
                        async(lmScannerCoroutine) {
                            var ret: SongTempData?
                            if (scannerRequestCancel) {
                                Log.i(TAG, "WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                ret = advancedScan(File(s))
                                scannerProgressProbe.value++
                                if (SCANNER_DEBUG && scannerProgressProbe.value % mod == 0) {
                                    Log.d(
                                        TAG,
                                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                                    )
                                }
                                if (scannerProgressProbe.value % mod == 0) {
                                    scannerProgressCurrent.value = scannerProgressProbe.value
                                }
                            } catch (e: InvalidAudioFileException) {
                                ret = null
                            }
                            ret
                        }
                    )
                }
            } else {
                if (scannerRequestCancel) {
                    Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                    throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
                }
                // force synchronous scanning of songs. Do not catch errors
                finalSongs.add(advancedScan(File(s)))
                scannerProgressProbe.value++
                if (SCANNER_DEBUG && scannerProgressProbe.value % 5 == 0) {
                    Log.d(
                        TAG,
                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                    )
                }
                if (scannerProgressProbe.value % 5 == 0) {
                    scannerProgressCurrent.value = scannerProgressProbe.value
                }
            }
        }

        if (!SYNC_SCANNER) {
            // use async scanner
            scannerJobs.awaitAll()
        }

        // add to finished list
        scannerJobs.forEach {
            val song = it.getCompleted()
            song?.song?.let { finalSongs.add(song) }
        }

        if (finalSongs.isNotEmpty()) {
            scannerState.value = 0
            syncDB(database, finalSongs, matchCriteria, strictFileNames, strictFilePaths, noDisable = true)
            scannerState.value = 2
        } else {
            Log.i(TAG, "Not syncing, no valid songs found!")
        }

        scannerProgressCurrent.value = scannerProgressProbe.value
        // we handle disabling songs here instead
        scannerState.value = 3
        disableSongsByPath(converted, database)
        finalize(database)

        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
    }


    /**
     * Run a full scan and ful database update. This will update all song data in the
     * database of all songs, and also disable inacessable songs
     *
     * No remote artist lookup is done
     *
     * WARNING: cachedDirectoryTree is not refreshed and may lead to inconsistencies.
     * It is highly recommend to rebuild the tree after scanner operation
     *
     * @param newSongs List of songs. This is expecting a barebones DirectoryTree
     * (only paths are necessary), thus you may use the output of refreshLocal().toList()
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fullSync(
        context: Context,
        database: MusicDatabase,
        newSongs: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
    ) {
        Log.i(TAG, "------------ SYNC: Starting FULL Library Sync ------------")
        Log.d(TAG, "Entries to process: ${newSongs.size}")
        scannerState.value = 2
        scannerProgressTotal.value = newSongs.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0
        val mod = if (newSongs.size < 20) {
            2
        } else if (newSongs.size < 50) {
            8
        } else {
            20
        }

        val finalSongs = ArrayList<SongTempData>()
        val scannerJobs = ArrayList<Deferred<SongTempData?>>()

        // Get song basic metadata
        newSongs.forEach { uri ->
            if (scannerRequestCancel) {
                Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                throw ScannerAbortException("Scanner canceled during FULL Library Sync")
            }

            if (SCANNER_DEBUG)
                Log.d(TAG, "PATH: $uri")

            if (!SYNC_SCANNER) {
                // use async scanner
                runBlocking {
                    scannerJobs.add(
                        async(lmScannerCoroutine) {
                            if (scannerRequestCancel) {
                                Log.i(TAG, "WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                val ret = advancedScan(context, uri)
                                scannerProgressProbe.value++
                                if (SCANNER_DEBUG && scannerProgressProbe.value % mod == 0) {
                                    Log.d(
                                        TAG,
                                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                                    )
                                }
                                if (scannerProgressProbe.value % mod == 0) {
                                    scannerProgressCurrent.value = scannerProgressProbe.value
                                }
                                ret
                            } catch (e: InvalidAudioFileException) {
                                null
                            }
                        }
                    )
                }
            } else {
                // force synchronous scanning of songs. Do not catch errors
                finalSongs.add(advancedScan(context, uri))
            }
        }

        if (!SYNC_SCANNER) {
            // use async scanner
            scannerJobs.awaitAll()
        }

        // add to finished list
        scannerJobs.forEach {
            val song = it.getCompleted()
            song?.song?.let { finalSongs.add(song) }
        }

        scannerProgressCurrent.value = scannerProgressProbe.value
        if (finalSongs.isNotEmpty()) {
            /**
             * TODO: Delete all local format entity before scan
             */
            scannerState.value = 0
            syncDB(database, finalSongs, matchCriteria, strictFileNames, strictFilePaths, refreshExisting = true)
            scannerState.value = 2
        } else {
            Log.i(TAG, "Not syncing, no valid songs found!")
        }

        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
    }


    /**
     * Run a full scan and ful database update. This will update all song data in the
     * database of all songs, and also disable inacessable songs
     *
     * No remote artist lookup is done
     *
     * WARNING: cachedDirectoryTree is not refreshed and may lead to inconsistencies.
     * It is highly recommend to rebuild the tree after scanner operation
     *
     * @param newSongs List of songs. This is expecting a barebones DirectoryTree
     * (only paths are necessary), thus you may use the output of refreshLocal().toList()
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fullMediaStoreSync(
        context: Context,
        database: MusicDatabase,
        scanPaths: List<Uri>,
        excludedScanPaths: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
        refreshExisting: Boolean,
    ) {
        Log.i(
            TAG,
            "------------ SYNC: Starting MediaStore FULL Library Sync, refreshExisting = $refreshExisting ------------"
        )
        scannerState.value = 2
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0

        val projection = arrayListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.BITRATE)
                if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 15) {
                    add(MediaStore.Audio.Media.BITS_PER_SAMPLE)
                }
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.CD_TRACK_NUMBER)
//                add(MediaStore.Audio.Media.WRITER)
                add(MediaStore.Audio.Media.DISC_NUMBER)
            }
        }

        val mediaStoreSongs = ArrayList<SongTempData>()


        val contentResolver: ContentResolver = context.contentResolver
        val selectionBuilder = StringBuilder("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        val selectionArgs = mutableListOf<String>()
        if (scanPaths.isNotEmpty()) {
            selectionBuilder.append(" AND (")
            scanPaths.forEachIndexed { index, path ->
                val convertedPath = absoluteFilePathFromUri(context, path)
                if (index > 0) {
                    selectionBuilder.append(" OR ")
                }
                selectionBuilder.append("${MediaStore.Audio.Media.DATA} LIKE ?")
                selectionArgs.add("$convertedPath%")
            }
            selectionBuilder.append(")")
        }
        val selection = selectionBuilder.toString()

        // Query for audio files
        val cursor = try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                selection,
                selectionArgs.toTypedArray(),
                null
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for MediaStore query", e)
            null
        }

        cursor?.use { cursor ->
            // Columns indices
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            var bitrateColumn: Int? = null
            var bitsPerSampleColumn: Int? = null
            var genreColumn: Int? = null
            var trackNumberColumn: Int? = null
            var discNumberColumn: Int? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitrateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
                if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 15) {
                    bitsPerSampleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITS_PER_SAMPLE)
                }
                genreColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
                trackNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.CD_TRACK_NUMBER)
                discNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISC_NUMBER)
            }

            while (cursor.moveToNext()) {
                val id = SongEntity.generateSongId()
                val name = cursor.getString(nameColumn) // file name
                var title = cursor.getString(titleColumn) // song title
                val duration = cursor.getInt(durationColumn) / 1000
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val rawYear = cursor.getString(yearColumn)
                val rawDateModified = cursor.getString(dateModifiedColumn)
                val path = cursor.getString(pathColumn)
                val mime = cursor.getString(mimeColumn)
                if (excludedScanPaths.any { path.startsWith(it.path ?: "") }) continue

                // extra stream info
                var bitrate: Int? = null
                var bitsPerSample: Int? = null
                var genre: String? = null
                var trackNumber: Int? = null
                var discNumber: Int? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitrate = cursor.getInt(bitrateColumn!!)
                    if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 15) {
                        bitsPerSample = cursor.getInt(bitsPerSampleColumn!!)
                    }
                    genre = cursor.getString(genreColumn!!)
                    trackNumber = cursor.getInt(trackNumberColumn!!)
                    discNumber = cursor.getInt(discNumberColumn!!)
                }

                if (SCANNER_DEBUG)
                    Log.d(TAG, "ID: $id, Name: $name, ARTIST: $artist, PATH: $path")

                if (title.isBlank()) { // songs with no title tag
                    title = name.substringBeforeLast('.')
                }

                val year = rawYear?.toIntOrNull()
                var dateModified: LocalDateTime? = null

                try {
                    rawDateModified?.toLongOrNull()?.let {
                        dateModified = LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val artistList = ArrayList<ArtistEntity>()
                val genresList = ArrayList<GenreEntity>()


                artist.split(ARTIST_SEPARATORS).forEach { artistVal ->
                    artistList.add(ArtistEntity(ArtistEntity.generateArtistId(), artistVal, isLocal = true))
                }

                genre?.split(ARTIST_SEPARATORS)?.forEach { genreVal ->
                    genresList.add(GenreEntity(GenreEntity.generateGenreId(), genreVal, isLocal = true))
                }
                val albumID = AlbumEntity.generateAlbumId()
                val albumEntity = if (album != null) AlbumEntity(
                    id = albumID,
                    title = album,
                    thumbnailUrl = path,
                    songCount = 1,
                    duration = duration,
                    isLocal = true
                ) else null

                mediaStoreSongs.add(
                    SongTempData(
                        Song(
                            song = SongEntity(
                                id = id,
                                title = title,
                                duration = duration,
                                thumbnailUrl = path,
                                inLibrary = LocalDateTime.now(),
                                isLocal = true,
                                localPath = path,
                                trackNumber = trackNumber,
                                discNumber = discNumber,
                                albumId = albumID, // this is replaced later anwyays
                                albumName = album,
                                year = year,
                                dateModified = dateModified,
                            ),
                            artists = artistList,
                            // album not working
                            album = albumEntity,
                            genre = genresList
                        ),
                        FormatEntity(
                            id = id,
                            itag = -1,
                            mimeType = mime,
                            codecs = mime.substringAfter('/'),
                            bitrate = bitrate ?: -1,
                            sampleRate = bitsPerSample,
                            contentLength = duration.toLong(),
                            loudnessDb = null,
                        )
                    )
                )
            }
        }

        // TODO: duplicate songs with different paths will cycle through paths, causing it to be synced instead of ignored...
        val finalSongs = if (!refreshExisting) {
            val allSongs = database.allLocalSongs().fastMapNotNull { it.song.localPath }.toSet()
            ArrayList(mediaStoreSongs.filterNot { it.song.song.localPath in allSongs })
        } else {
            mediaStoreSongs
        }

        scannerProgressCurrent.value = finalSongs.size
        if (finalSongs.isNotEmpty()) {
            /**
             * TODO: Delete all local format entity before scan
             */
            scannerState.value = 0
            syncDB(
                database, finalSongs, matchCriteria, strictFileNames, strictFilePaths,
                refreshExisting = refreshExisting, noDisable = true
            )

        } else {
            Log.i(TAG, "Not syncing, no valid songs found!")
        }
        // we handle disabling songs here instead
        scannerState.value = 3
        disableSongsByPath(mediaStoreSongs.mapNotNull { it.song.song.localPath }, database)
        finalize(database)
        scannerState.value = 0


        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished MediaStore FULL Library Sync ------------")
    }

    private suspend fun disableSongsByPath(newSongs: List<String>, database: MusicDatabase) {
        Log.i(TAG, "Start finalize (disableSongsByPath) job. Number of valid songs: ${newSongs.size}")
        // get list of all local songs in db
        database.disableInvalidLocalSongs() // make sure path is existing
        val allSongs = database.allLocalSongs()
        // Use a HashSet for O(1) lookup performance
        val newPathsSet = newSongs.toHashSet()
        database.runInTransaction {
            // disable if not in directory anymore
            for (song in allSongs) {
                val path = song.song.localPath ?: continue

                // new songs is all songs that are known to be valid
                // delete all songs in the DB that do not match a path
                if (!newPathsSet.contains(path)) {
                    if (SCANNER_DEBUG)
                        Log.v(TAG, "Disabling song $path")
                    disableLocalSong(song.song.id)
                }
            }
        }
        Log.i(TAG, "Finished (disableSongsByPath) job")
    }

    private suspend fun disableSongs(newSongs: List<Song>, database: MusicDatabase) {
        Log.i(TAG, "Start finalize (disableSongs) job. Number of valid songs: ${newSongs.size}")

        // get list of all local songs in db
        database.disableInvalidLocalSongs() // make sure path is existing
        val allSongs = database.allLocalSongs()
        // Use a HashSet for O(1) lookup performance
        val newPathsSet = newSongs.mapNotNull { it.song.localPath }.toHashSet()

        database.runInTransaction {
            // disable if not in directory anymore
            for (song in allSongs) {
                val path = song.song.localPath ?: continue

                // new songs is all songs that are known to be valid
                // delete all songs in the DB that do not match a path
                if (!newPathsSet.contains(path)) {
                    if (SCANNER_DEBUG)
                        Log.v(TAG, "Disabling song $path")
                    database.disableLocalSong(song.song.id)
                }
            }
        }
        Log.i(TAG, "Finished (disableSongs) job")
    }

    /**
     * Remove inaccessible, and duplicate songs from the library
     */
    private suspend fun finalize(database: MusicDatabase) {
        Log.i(TAG, "Start finalize (database cleanup job)")

        // remove duplicates
        val dupes = database.duplicatedLocalSongs().toMutableList()

        Log.d(TAG, "Start finalize (duplicate removal) job. Number of candidates: ${dupes.size}")
        database.runInTransaction {
            var index = 0
            while (index < dupes.size) {
                val contenders = ArrayList<Pair<SongEntity, Int>>()
                val localPath = dupes[index].localPath
                while (index < dupes.size && dupes[index].localPath == localPath) {
                    contenders.add(Pair(dupes[index], getLifetimePlayCount(dupes[index].id)))
                    index++
                }
                // yeet the lower play count songs
                val sortedContenders = contenders.sortedByDescending { it.second }
                sortedContenders.drop(1).forEach {
                    if (SCANNER_DEBUG)
                        Log.v(TAG, "Deleting song ${it.first.id} (${it.first.title})")
                    delete(it.first)
                }
            }

            // remove duplicated local artists
            database.localArtistsByName()
                .groupBy { it.title.lowercase() }
                .filter { it.value.size > 1 }
                .forEach { (_, artists) ->
                    try {
                        val oldestArtist = artists.first().artist
                        artists.drop(1).sortedBy { it.artist.bookmarkedAt }.forEach { duplicate ->
                            swapArtists(duplicate.artist, oldestArtist, database)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }

            // remove duplicated local albums
            database.allLocalAlbumsByName()
                .groupBy { it.title.lowercase() }
                .filter { it.value.size > 1 }
                .forEach { (_, albums) ->
                    try {
                        val oldestAlbum = albums.first()
                        albums.drop(1).sortedBy { it.bookmarkedAt }.forEach { duplicate ->
                            swapAlbums(duplicate, oldestAlbum, database)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }

            // remove duplicated genres
            database.allLocalGenresByName()
                .groupBy { it.title.lowercase() }
                .filter { it.value.size > 1 }
                .forEach { (_, genres) ->
                    try {
                        val oldestGenre = genres.first()
                        genres.drop(1).sortedBy { it.bookmarkedAt }.forEach { duplicate ->
                            swapGenres(duplicate, oldestGenre, database)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
        }

        Log.i(TAG, "Finished finalize (duplicate removal) job")
    }


    companion object {
        // do not put any thing that should adhere to the scanner lock in here
        const val TAG = "LocalMediaScanner"

        private var ownerId = -1
        private var localScanner: LocalMediaScanner? = null


        var scannerRequestCancel = false

        /**
         * -1: Inactive
         * 0: Idle
         * 1: Discovering (Crawling files)
         * 2: Scanning (Extract metadata and checking playability)
         * 3: Syncing (Update database)
         * 4: Scan finished
         */
        var scannerState = MutableStateFlow(-1)
        var scannerProgressTotal = MutableStateFlow(-1)
        var scannerProgressCurrent = MutableStateFlow(-1)
        var scannerProgressProbe = MutableStateFlow(-1)


        /**
         * ==========================
         * Scanner management
         * ==========================
         */

        /**
         * Trust me bro, it should never be null
         */
        fun getScanner(context: Context, scannerImpl: ScannerImpl, owner: Int): LocalMediaScanner {

            if (localScanner == null) {
                // reset to taglib if ffMetadataEx disappears
                if (scannerImpl == ScannerImpl.FFMPEG_EXT && !ENABLE_FFMETADATAEX) {
                    CoroutineScope(lmScannerCoroutine).launch {
                        context.dataStore.edit { settings ->
                            settings[ScannerImplKey] = ScannerImpl.TAGLIB.toString()
                            settings[AutomaticScannerKey] = false
                            runBlocking(Dispatchers.Main) {
                                // TODO: string resource (but will anyone even notice this...)
                                Toast.makeText(context, "FFmpeg extractors are missing", Toast.LENGTH_SHORT).show()
                                Toast.makeText(
                                    context,
                                    "Auto scanner has been disabled to prevent data conflicts. You will need to enable this in local media settings again if you want automatic scanning.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                localScanner = LocalMediaScanner(scannerImpl)
                scannerProgressTotal.value = 0
                scannerProgressCurrent.value = -1
                scannerProgressProbe.value = 0
            }

            ownerId = owner
            return localScanner!!
        }

        suspend fun destroyScanner(owner: Int) {
            if (owner != ownerId && ownerId != -1) {
                Log.w(TAG, "Scanner instance can only be destroyed by the owner. Aborting. Check your ownerId.")
                return
            }
            ownerId = -1
            localScanner = null
            scannerState.value = -1
            scannerRequestCancel = false
            scannerProgressTotal.value = -1
            scannerProgressCurrent.value = -1
            scannerProgressProbe.value = -1

            Log.i(TAG, "Scanner instance destroyed")
        }


        /**
         * ==========================
         * Scanner extra scan utils
         * ==========================
         */


        /**
         * Build a list of files to scan, taking in exclusions into account. Exclusions
         * will override inclusions. All subdirectories will also be affected.
         *
         * Uri.path can be assumed to be non-null
         */
        fun getScanFiles(scanPaths: List<Uri>, excludedScanPaths: List<Uri>, context: Context): List<Uri> {
            val allSongs = ArrayList<Uri>()
            val resultingPaths =
                scanPaths.filterNot { incl ->
                    excludedScanPaths.any { excl -> incl.path?.startsWith(excl.path.toString()) == true }
                }

            resultingPaths.forEach { path ->
                try {
                    val file = documentFileFromUri(context, path)
                    if (file != null) {
                        val songsHere = ArrayList<DocumentFile>()
                        scanDfRecursive(file, songsHere) {
                            // Allow: audio mime, or certain audio exts
                            // Disallow: x-mpegurl (m3u)
                            val mime = it.type ?: return@scanDfRecursive false
                            if (!mime.startsWith("audio")) {
                                if (it.name?.substringAfterLast('.') !in scannerWhitelistExts) {
                                    return@scanDfRecursive false
                                }
                            }
                            if (mime == "audio/x-mpegurl") {
                                return@scanDfRecursive false
                            }

                            return@scanDfRecursive true
                        }

                        allSongs.addAll(songsHere.fastFilter { incl ->
                            !excludedScanPaths.any {
                                incl.uri.path?.startsWith(it.path.toString()) == true
                            }
                        }.map { it.uri })
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    throw Exception("oh well idk man this should never happen")
                }
            }

            return allSongs.fastDistinctBy { it.toString() }
        }

        fun scanDfRecursive(
            dir: DocumentFile,
            result: ArrayList<DocumentFile>,
            scanHidden: Boolean = false,
            validator: ((DocumentFile) -> Boolean)? = null
        ): DocumentFile? {
            val files = dir.listFiles()
            for (file in files) {
                if (!scanHidden && file.name?.startsWith(".") == true) continue
                if (file.isDirectory && (scanHidden || !file.listFiles().any { it.name == ".nomedia" })) {
                    // look into subdirs
                    scanDfRecursive(file, result, scanHidden, validator)
                } else {
                    // add if file matches
                    if (validator == null || validator(file)) {
                        result.add(file)
                        scannerProgressProbe.value++
                        if (scannerProgressProbe.value % 20 == 0) {
                            scannerProgressTotal.value = scannerProgressProbe.value
                        }
                    }
                }
            }
            return null
        }

        /**
         * Quickly rebuild a skeleton directory tree of local files based on the database
         *
         * Notes:
         * If files move around, that's on you to re run the scanner.
         * If the metadata changes, that's also on you to re run the scanner.
         *
         * @param scanPaths List of whitelist paths to scan under. This assumes
         * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
         * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
         * @param filter Raw file path
         */
        suspend fun refreshLocal(
            database: MusicDatabase,
            filter: String
        ): DirectoryTree {
            val newDirectoryStructure = DirectoryTree(filter.trimEnd { it == '/' }, CulmSongs(0))

            // get songs from db
            val existingSongs: List<Song> = database.localSongsInDirShallow(filter)

            Log.i(TAG, "------------ SCAN: Starting Quick Directory Rebuild ------------")

            // Build directory tree with existing files
            existingSongs.forEach { s ->
                val path = s.song.localPath ?: return@forEach
                val filterPath =
                    (if (path.startsWith(filter)) path.substringAfter(filter) else path).trimStart { it == '/' }
                newDirectoryStructure.insert(filterPath, s)
            }

            Log.i(TAG, "------------ SCAN: Finished Quick Directory Rebuild ------------")
            return newDirectoryStructure.androidStorageWorkaround()
        }


        /**
         * ==========================
         * Scanner helpers
         * ==========================
         */


        /**
         * Check if artists are the same
         *
         *  Both null == same artists
         *  Either null == different artists
         */
        fun compareArtist(a: List<ArtistEntity>, b: List<ArtistEntity>): Boolean {
            if (a.isEmpty() && b.isEmpty()) {
                return true
            } else if (a.isEmpty() || b.isEmpty()) {
                return false
            }

            // compare entries
            if (a.size != b.size) {
                return false
            }
            val matchingArtists = a.filter { artist ->
                b.any { it.name.equals(artist.name, false) }
            }

            return matchingArtists.size == a.size
        }

        /**
         * Check if albums are the same
         *
         *  Both null == same albums
         *  Either null == different albums
         */
        fun compareAlbum(a: AlbumEntity?, b: AlbumEntity?): Boolean {
            if (a == null && b == null) return true
            if (a == null || b == null) return false

            return a.title.equals(b.title, false)
        }

        /**
         * Check the similarity of a song
         *
         * @param a
         * @param b
         * @param matchStrength How lax should the scanner be
         */
        fun compareM3uSong(
            a: Song,
            b: Song,
            matchStrength: ScannerM3uMatchCriteria = ScannerM3uMatchCriteria.LEVEL_1,
        ): Boolean {
            val matchStrength = when (matchStrength) {
                ScannerM3uMatchCriteria.LEVEL_1 -> ScannerMatchCriteria.LEVEL_1
                ScannerM3uMatchCriteria.LEVEL_2 -> ScannerMatchCriteria.LEVEL_2
                else -> ScannerMatchCriteria.LEVEL_1
            }
            return compareSong(a, b, matchStrength)
        }

        /**
         * Check the similarity of a song
         *
         * @param a
         * @param b
         * @param matchStrength How lax should the scanner be
         * @param strictFileNames Whether to consider file names
         */
        fun compareSong(
            a: Song,
            b: Song,
            matchStrength: ScannerMatchCriteria = ScannerMatchCriteria.LEVEL_2,
            strictFileNames: Boolean = false,
            strictFilePaths: Boolean = false,
        ): Boolean {
            /**
             * Compare file paths
             *
             * I draw the "user error" line here
             */
            fun closeEnough(): Boolean {
                return a.song.localPath == b.song.localPath
            }
            if (strictFilePaths) {
                return closeEnough()
            }
            // if match file names
            if (strictFileNames &&
                (a.song.localPath?.substringAfterLast('/') !=
                        b.song.localPath?.substringAfterLast('/'))
            ) {
                return false
            }

            // compare songs based on scanner strength
            return when (matchStrength) {
                ScannerMatchCriteria.LEVEL_1 -> a.song.title == b.song.title
                ScannerMatchCriteria.LEVEL_2 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists))

                ScannerMatchCriteria.LEVEL_3 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists) && compareAlbum(a.album, b.album))
            }
        }



        /**
         * Swap all participation(s) with old artist to use new artist
         *
         * p.s. This is here instead of DatabaseDao because it won't compile there because
         * "oooga boooga error in generated code"
         */
        fun swapArtists(old: ArtistEntity, new: ArtistEntity, database: MusicDatabase) {
            database.transaction {
                if (artistById(old.id) == null) {
                    reportException(Exception("Attempting to swap with non-existent old artist in database with id: ${old.id}"))
                    return@transaction
                }
                if (artistById(new.id) == null) {
                    reportException(Exception("Attempting to swap with non-existent new artist in database with id: ${new.id}"))
                    return@transaction
                }

                // update participation(s)
                updateSongArtistMap(old.id, new.id)
                updateAlbumArtistMap(old.id, new.id)

                // nuke old artist
                safeDeleteArtist(old.id)
            }
        }

        fun swapAlbums(old: AlbumEntity, new: AlbumEntity, database: MusicDatabase) {
            database.transaction {
                if (albumById(old.id) == null) {
                    reportException(Exception("Attempting to swap with non-existent old album in database with id: ${old.id}"))
                    return@transaction
                }
                if (albumById(new.id) == null) {
                    reportException(Exception("Attempting to swap with non-existent new album in database with id: ${new.id}"))
                    return@transaction
                }

                // update participation(s)
                updateSongAlbumMap(old.id, new.id)

                // nuke old artist
                safeDeleteAlbum(old.id)
            }
        }

        fun swapGenres(old: GenreEntity, new: GenreEntity, database: MusicDatabase) {
            database.transaction {
                if (genreById(old.id) == null) {
                    reportException(Exception("Attempting to swap with non-existent old album in database with id: ${old.id}"))
                    return@transaction
                }
                if (genreById(new.id) == null) {
                    reportException(Exception("Attempting to swap with non-existent new album in database with id: ${new.id}"))
                    return@transaction
                }

                // update participation(s)
                updateSongGenreMap(old.id, new.id)

                // nuke old genre
                safeDeleteGenre(old.id)
            }
        }
    }
}

class InvalidAudioFileException(message: String) : Throwable(message)
class ScannerAbortException(message: String) : Throwable(message)
class ScannerCriticalFailureException(message: String) : Throwable(message)

