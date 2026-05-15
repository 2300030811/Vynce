package com.vynce.app.playback

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.vynce.app.constants.AudioQuality
import com.vynce.app.constants.AudioQualityKey
import com.vynce.app.constants.DownloadExtraPathKey
import com.vynce.app.constants.DownloadPathKey
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.FormatEntity
import com.vynce.app.db.entities.PlaylistSong
import com.vynce.app.db.entities.Song
import com.vynce.app.db.entities.SongEntity
import com.vynce.app.di.AppModule.PlayerCache
import com.vynce.app.di.DownloadCache
import com.vynce.app.models.MediaMetadata
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong
import com.vynce.app.playback.DownloadUtil.Companion.STATE_DOWNLOADING
import com.vynce.app.playback.DownloadUtil.Companion.STATE_INVALID
import com.vynce.app.playback.downloadManager.DownloadDirectoryManagerOt
import com.vynce.app.playback.downloadManager.DownloadManagerOt
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.SaavnStreamResolver
import com.vynce.app.utils.dlCoroutine
import com.vynce.app.utils.enumPreference
import com.vynce.app.utils.get
import com.vynce.app.utils.reportException
import com.vynce.app.utils.scanners.InvalidAudioFileException
import com.vynce.app.utils.scanners.fileFromUri
import com.vynce.app.utils.scanners.uriListFromString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
    val saavnStreamResolver: SaavnStreamResolver,
) {
    val TAG = DownloadUtil::class.simpleName.toString()

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val dataSourceFactory = ResolvingDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(
                    OkHttpClient.Builder()
                        .build()
                )
            )
    ) { dataSpec ->
        val mediaId = dataSpec.key ?: error("No media id")
        val length = if (dataSpec.length >= 0) dataSpec.length else 1
        if (playerCache.isCached(mediaId, dataSpec.position, length)) {
            return@Factory dataSpec
        }

        if (mediaId.startsWith("saavn:")) {
            val saavnId = mediaId.removePrefix("saavn:")
            val streamUrl = saavnStreamResolver.resolve(saavnId)
                ?: throw java.io.IOException("Failed to resolve Saavn stream URL for $saavnId")
            
            return@Factory dataSpec.withUri(streamUrl.toUri())
        }

        throw java.io.IOException("Unsupported URI scheme for ${mediaId}")
    }
    val downloadNotificationHelper = DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)
    val downloadManager: DownloadManager =
        DownloadManager(context, databaseProvider, downloadCache, dataSourceFactory, Executor(Runnable::run)).apply {
            maxParallelDownloads = 3
            addListener(
                ExoDownloadService.TerminalStateNotificationHelper(
                    context = context,
                    notificationHelper = downloadNotificationHelper,
                    nextNotificationId = ExoDownloadService.NOTIFICATION_ID + 1
                )
            )
        }
    val downloads = MutableStateFlow<Map<String, LocalDateTime>>(emptyMap())

    var localMgr = DownloadDirectoryManagerOt(
        context,
        context.dataStore.get(DownloadPathKey, "").toUri(),
        uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
    )
    val downloadMgr = DownloadManagerOt(localMgr)
    var isProcessingDownloads = MutableStateFlow(false)

    fun getDownload(songId: String): Flow<LocalDateTime?> = downloads.map { it[songId] }

    fun download(songs: List<MediaMetadata>) {
        songs.forEach { song -> downloadSong(song.id, song.title) }
    }

    fun download(song: MediaMetadata) {
        downloadSong(song.id, song.title)
    }

    fun download(song: SongEntity) {
        downloadSong(song.id, song.title)
    }

    private fun downloadSong(id: String, title: String) {
        if (downloads.value[id] != null) return
        val downloadRequest = DownloadRequest.Builder(id, id.toUri())
            .setCustomCacheKey(id)
            .setData(title.toByteArray())
            .build()
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            downloadRequest,
            false
        )
    }

    fun resumeDownloadsOnStart() {
        DownloadService.sendResumeDownloads(
            context,
            ExoDownloadService::class.java,
            false
        )
    }


// Deletes from custom dl

    suspend fun delete(song: PlaylistSong) = deleteSong(song.song.id)

    suspend fun delete(song: Song) = deleteSong(song.song.id)

    suspend fun delete(song: SongEntity) = deleteSong(song.id)

    suspend fun delete(song: MediaMetadata) = deleteSong(song.id)

    private suspend fun deleteSong(id: String): Boolean {
        val deleted = localMgr.deleteFile(id)
        if (!deleted) return false
        downloads.update { map ->
            map.toMutableMap().apply {
                remove(id)
            }
        }

        database.removeDownloadSong(id)
        return true
    }

    /**
     * Retrieve song from cache, and delete it from cache afterwards
     */
    fun getFromCache(cache: SimpleCache, mediaId: String): ByteArray? {
        val spans: Set<CacheSpan> = cache.getCachedSpans(mediaId)
        if (spans.isEmpty()) return null

        val output = ByteArrayOutputStream()
        try {
            for (span in spans) {
                val file: File? = span.file
                FileInputStream(file).use { fis ->
                    fis.copyTo(output)
                }
            }
            return output.toByteArray()
        } catch (e: IOException) {
            reportException(e)
        } finally {
            output.close()
        }
        return null
    }

    /**
     * Migrated existing downloads from the download cache to the new system in external storage
     */
    suspend fun migrateDownloads() {
        Log.i(TAG, "+migrateDownloads()")
        isProcessingDownloads.value = true
        val dbDownloads = database.downloadedOrQueuedSongs().first()
        dbDownloads.forEach { s ->
            val mediaId = s.song.id
            if (s.song.localPath != null) return@forEach
            val bytes = getFromCache(downloadCache, mediaId) ?: return@forEach
            localMgr.saveFile(mediaId, bytes)
            database.updateDownloadStatus(mediaId, s.song.dateDownload)
        }
        isProcessingDownloads.value = false
        Log.i(TAG, "-migrateDownloads()")
    }

    fun cd() {
        localMgr.doInit(
            context,
            context.dataStore.get(DownloadPathKey, "").toUri(),
            uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
        )
    }

    /**
     * Rescan download directory and updates songs
     */
    suspend fun rescanDownloads() {
        Log.i(TAG, "+rescanDownloads()")
        isProcessingDownloads.value = true
        val dbDownloads = database.downloadedOrQueuedSongs().first()
        val result = mutableMapOf<String, LocalDateTime>()

        // Only check for missing files among songs that were explicitly saved to the custom
        // download directory (i.e., have a non-null localPath). Streaming songs (saavn: prefixed
        // IDs with no localPath) are NOT stored on disk and must never be removed here.
        val customDownloadCandidates = dbDownloads.filter { it.song.dateDownload != null && it.song.localPath != null }
        val missingFiles = localMgr.getMissingFiles(customDownloadCandidates).toMutableList()
        Log.d(TAG, "Found ${missingFiles.size}/${customDownloadCandidates.size} custom-download files missing from disk")

        // Also check ExoPlayer internal cache: if the song is in the internal download index
        // it is NOT missing even if it isn't in the custom folder.
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                missingFiles.removeIf { it.id == cursor.download.request.id }
            }
        }
        Log.d(
            TAG,
            "Found ${missingFiles.size} songs truly missing (not in custom dir or internal cache). Removing these now."
        )

        database.transaction {
            missingFiles.forEach {
                Log.v(TAG, "Shedding: [${it.id}] ${it.song.title}")
                removeDownloadSong(it.song.id)
            }
        }

        // Build the in-memory map for all songs still considered downloaded.
        // Include streaming songs (no localPath) — they track their download date in DB.
        val removedIds = missingFiles.map { it.id }.toSet()
        dbDownloads.forEach { s ->
            if (s.song.dateDownload != null && s.song.id !in removedIds) {
                result[s.song.id] = s.song.dateDownload!!
            }
        }

        downloads.value = result
        isProcessingDownloads.value = false
        Log.i(TAG, "-rescanDownloads()")
    }


    /**
     * Scan and import downloaded songs from main and extra directories.
     *
     * This is intended for re-importing existing songs (ex. songs get moved, after restoring app backup), thus all
     * songs will already need to exist in the database.
     */
    suspend fun scanDownloads() {
        Log.i(TAG, "+scanDownloads()")
        if (isProcessingDownloads.value) {
            Log.i(TAG, "-scanDownloads()")
            return
        }
        isProcessingDownloads.value = true

//            val scanner = LocalMediaScanner.getScanner(context, ScannerImpl.TAGLIB, SCANNER_OWNER_DL)
        database.removeAllDownloadedSongs()
        val timeNow = LocalDateTime.now()

        // add custom downloads
        val availableFiles = localMgr.getAvailableFiles(false)
        val validExtensions = setOf("mp3", "flac", "ogg", "m4a", "aac", "wav", "opus", "wma", "alac")
        var validCount = 0
        var invalidCount = 0
        database.transaction {
            availableFiles.forEach { f ->
                try {
                    val file = fileFromUri(context, f.value)
                    if (file == null) throw InvalidAudioFileException("Null file from URI ${f.value}")
                    if (!file.exists()) throw InvalidAudioFileException("File does not exist: ${file.absolutePath}")
                    if (file.length() == 0L) throw InvalidAudioFileException("File is empty: ${file.absolutePath}")
                    val ext = file.extension.lowercase()
                    if (ext !in validExtensions) throw InvalidAudioFileException("Unsupported format .$ext: ${file.absolutePath}")
                    database.registerDownloadSong(f.key, timeNow, file.absolutePath)
                    validCount++
                } catch (e: InvalidAudioFileException) {
                    Log.w(TAG, "Skipping invalid download file: ${e.message}")
                    invalidCount++
                }
            }
        }
        Log.d(TAG, "Registered $validCount files from custom downloads ($invalidCount invalid skipped)")

        // add internal downloads
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            var count = 0
            database.transaction {
                while (cursor.moveToNext()) {
                    database.updateDownloadStatus(cursor.download.request.id, stateToLocalDateTime(cursor.download))
                    count ++
                }
            }
            Log.d(TAG, "Registered $count files from internal downloads")
        }
        isProcessingDownloads.value = false
        Log.d(TAG, "Database registration complete, triggering map registry rebuild")
        rescanDownloads()
        Log.i(TAG, "-scanDownloads()")
    }

    companion object {
        val STATE_DOWNLOADING: LocalDateTime = Instant.ofEpochMilli(1).atZone(ZoneOffset.UTC).toLocalDateTime()
        val STATE_INVALID: LocalDateTime = Instant.ofEpochMilli(0).atZone(ZoneOffset.UTC).toLocalDateTime()
    }


    init {
        Log.i(TAG, "DownloadUtil init")
        CoroutineScope(dlCoroutine).launch {
            rescanDownloads()
        }

        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    downloads.update { map ->
                        map.toMutableMap().apply {
                            val state = stateToLocalDateTime(download)
                            if (state == STATE_INVALID) {
                                Log.w(TAG, "Invalid download state for ${download.request.id}. Removing download")
                                remove(download.request.id)
                            } else {
                                set(download.request.id, state)
                            }
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        when (download.state) {
                            Download.STATE_COMPLETED -> {
                                val updateTime =
                                    Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
                                database.updateDownloadStatus(download.request.id, updateTime)
                            }
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                database.updateDownloadStatus(download.request.id, STATE_DOWNLOADING)
                            }
                            Download.STATE_FAILED -> {
                                database.updateDownloadStatus(download.request.id, STATE_INVALID)
                            }
                            else -> {
                                database.updateDownloadStatus(download.request.id, null)
                            }
                        }
                    }
                }
            }
        )
    }
}

fun stateToLocalDateTime(download: Download): LocalDateTime {
    return when (download.state) {
        Download.STATE_COMPLETED -> {
            Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
        }

        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> STATE_DOWNLOADING
        else -> STATE_INVALID
    }
}
