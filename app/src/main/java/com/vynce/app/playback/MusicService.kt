/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.vynce.app.MainActivity
import com.vynce.app.R
import com.vynce.app.constants.AudioNormalizationKey
import com.vynce.app.constants.AudioOffloadKey
import com.vynce.app.constants.AudioQuality
import com.vynce.app.constants.AudioQualityKey
import com.vynce.app.constants.AutoLoadMoreKey
import com.vynce.app.constants.ENABLE_FFMETADATAEX
import com.vynce.app.constants.KeepAliveKey
import com.vynce.app.constants.MAX_PLAYER_CONSECUTIVE_ERR
import com.vynce.app.constants.MaxQueuesKey
import com.vynce.app.constants.MediaSessionConstants.CommandToggleLike
import com.vynce.app.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.vynce.app.constants.MediaSessionConstants.CommandToggleShuffle
import com.vynce.app.constants.MediaSessionConstants.CommandToggleStartRadio
import com.vynce.app.constants.PauseListenHistoryKey
import com.vynce.app.constants.PauseListenHistoryKey
import com.vynce.app.constants.PersistentQueueKey
import com.vynce.app.constants.LastFmScrobblingEnabledKey
import com.vynce.app.constants.PlayerVolumeKey
import com.vynce.app.constants.RepeatModeKey
import com.vynce.app.constants.SkipOnErrorKey
import com.vynce.app.constants.SkipSilenceKey
import com.vynce.app.constants.StopMusicOnTaskClearKey
import com.vynce.app.constants.minPlaybackDurKey
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.ArtistEntity
import com.vynce.app.db.entities.Event
import com.vynce.app.db.entities.FormatEntity
import com.vynce.app.db.entities.RelatedSongMap
import com.vynce.app.db.entities.Song
import com.vynce.app.db.entities.SongEntity
import com.vynce.app.di.AppModule.PlayerCache
import com.vynce.app.di.DownloadCache
import com.vynce.app.extensions.SilentHandler
import com.vynce.app.extensions.collect
import com.vynce.app.extensions.collectLatest
import com.vynce.app.extensions.currentMetadata
import com.vynce.app.extensions.findNextMediaItemById
import com.vynce.app.extensions.metadata
import com.vynce.app.extensions.setOffloadEnabled
import com.vynce.app.lyrics.LyricsHelper
import com.vynce.app.models.HybridCacheDataSinkFactory
import com.vynce.app.models.MediaMetadata
import com.vynce.app.models.MultiQueueObject
import com.vynce.app.models.toMediaMetadata
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.playback.queues.Queue
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong
import dagger.hilt.android.AndroidEntryPoint
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.get
import com.vynce.app.utils.LastFmScrobbler
import com.vynce.app.utils.NetworkConnectivityObserver
import com.vynce.app.utils.CoilBitmapLoader
import com.vynce.app.playback.SleepTimer
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import com.vynce.app.utils.reportException

private val AudioDecoderPreferenceKey = intPreferencesKey("audio_decoder")
private val AudioGaplessOffloadPreferenceKey = booleanPreferencesKey("audio_gapless_offload")

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    val TAG = MusicService::class.simpleName.toString()

    @Inject
    lateinit var database: MusicDatabase
    private val scope = CoroutineScope(Dispatchers.Main)
    private val offloadScope = CoroutineScope(Dispatchers.Default)

    // Critical player components
    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private val binder = MusicBinder()
    private lateinit var connectivityManager: ConnectivityManager

    val qbInit = MutableStateFlow(false)
    var queueBoard = MutableStateFlow(QueueBoard(this, maxQueues = 1))
    var queuePlaylistId: String? = null

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    // Player components

    private val streamUrlCache = mutableMapOf<String, String>()
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(true)

    lateinit var sleepTimer: SleepTimer

    // Player vars
    val currentMediaMetadata = MutableStateFlow<MediaMetadata?>(null)

    private val currentSong = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        if (mediaMetadata?.id?.startsWith("saavn:") == true) {
            // For JioSaavn songs, create a synthetic Song from MediaMetadata
            flowOf(
                Song(
                    song = SongEntity(
                        id = mediaMetadata.id,
                        title = mediaMetadata.title ?: "Unknown",
                        thumbnailUrl = mediaMetadata.thumbnailUrl,
                        albumName = mediaMetadata.album?.title,
                        localPath = null
                    ),
                    artists = mediaMetadata.artists?.map { artist ->
                        ArtistEntity(
                            id = artist.id ?: "unknown_${artist.name}",
                            name = artist.name
                        )
                    } ?: emptyList(),
                    album = null,
                    genre = null,
                    playCount = null
                )
            )
        } else {
            // Regular database lookup for Saavn/Local songs
            database.song(mediaMetadata?.id)
        }
    }.stateIn(offloadScope, SharingStarted.Lazily, null)

    private val lastFmScrobbler by lazy { LastFmScrobbler() }

    private var previousMediaId: String? = null

    private val currentFormat = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    private val normalizeFactor = MutableStateFlow(1f)

    private val audioDecoder = dataStore.get(AudioDecoderPreferenceKey, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    private val isGaplessOffloadAllowed = dataStore.get(AudioGaplessOffloadPreferenceKey, false)
    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    private var isAudioEffectSessionOpened = false

    var consecutivePlaybackErr = 0

    override fun onCreate() {
        Log.i(TAG, "Starting MusicService")
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setRenderersFactory(createRenderersFactory(isGaplessOffloadAllowed))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                // listeners
                addListener(this@MusicService)
                sleepTimer = SleepTimer(scope, this)
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))

                // misc
                setOffloadEnabled(dataStore.get(AudioOffloadKey, false))
            }

        mediaLibrarySessionCallback.apply {
            service = this@MusicService
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }

        mediaSession = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            // TODO: do i even want to have smaller art for media notification
            .setBitmapLoader(CoilBitmapLoader(this))
            .build()

        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!

        currentSong.collect(scope) {
            updateNotification()
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this@MusicService,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.small_icon)
                }
        )

        // lateinit tasks
        offloadScope.launch {
            Log.i(TAG, "Launching MusicService offloadScope tasks")
            if (!qbInit.value) {
                initQueue()
            }

            combine(playerVolume, normalizeFactor) { playerVolume, normalizeFactor ->
                playerVolume * normalizeFactor
            }.collectLatest(scope) {
                withContext(Dispatchers.Main) {
                    player.volume = it
                }
            }

            playerVolume.debounce(1000).collect(scope) { volume ->
                dataStore.edit { settings ->
                    settings[PlayerVolumeKey] = volume
                }
            }

            dataStore.data
                .map { it[SkipSilenceKey] ?: false }
                .distinctUntilChanged()
                .collectLatest(scope) {
                    withContext(Dispatchers.Main) {
                        player.skipSilenceEnabled = it
                    }
                }

            combine(
                currentFormat,
                dataStore.data
                    .map { it[AudioNormalizationKey] ?: true }
                    .distinctUntilChanged()
            ) { format, normalizeAudio ->
                format to normalizeAudio
            }.collectLatest(scope) { (format, normalizeAudio) ->
                normalizeFactor.value = if (normalizeAudio && format?.loudnessDb != null) {
                    min(10f.pow(-format.loudnessDb.toFloat() / 20), 1f)
                } else {
                    1f
                }
            }


            // network connectivity
            try {
                connectivityObserver.unregister()
            } catch (e: UninitializedPropertyAccessException) {
                // lol
            }
            connectivityObserver = NetworkConnectivityObserver(this@MusicService)

            offloadScope.launch {
                connectivityObserver.networkStatus.collect { isConnected: Boolean ->
                    isNetworkConnected.value = isConnected

                    if (isConnected && waitingForNetworkConnection.value) {
                        waitingForNetworkConnection.value = false
                        withContext(Dispatchers.Main) {
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
        }
    }


// Library functions

    fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                val song = it.song.toggleLike()
                update(song)

                if (!song.isLocal) {
//                    syncUtils.likeSong(song)
                }
            }
        }
    }

    fun toggleStartRadio() {
        // Radio is disabled as it was YouTube-based
    }


// Queue

    /**
     * Play a queue.
     *
     * @param queue Queue to play.
     * @param playWhenReady
     * @param shouldResume Set to true for the player should resume playing at the current song's last save position or
     * false to start from the beginning.
     * @param replace Replace media items instead of the underlying logic
     * @param title Title override for the queue. If this value us unspecified, this method takes the value from queue.
     * If both are unspecified, the title will default to "Queue".
     */
    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
        shouldResume: Boolean = false,
        replace: Boolean = false,
        isRadio: Boolean = false,
        title: String? = null
    ) {
        if (!qbInit.value) {
            runBlocking(Dispatchers.IO) {
                initQueue()
            }
        }

        var queueTitle = title
        queuePlaylistId = queue.playlistId
        var q: MultiQueueObject? = null
        val preloadItem = queue.preloadItem
        // do not use scope.launch ... it breaks randomly... why is this bug back???
        CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "playQueue: Resolving additional queue data...")
            try {
                if (preloadItem != null) {
                    q = queueBoard.value.addQueue(
                        queueTitle ?: "Radio\u2060temp",
                        listOf(preloadItem),
                        shuffled = queue.startShuffled,
                        replace = replace,
                        continuationEndpoint = null // fulfilled later on after initial status
                    )
                    queueBoard.value.setCurrQueue(q, true)
                }

                val initialStatus = withContext(Dispatchers.IO) { queue.getInitialStatus() }
                // do not find a title if an override is provided
                if ((title == null) && initialStatus.title != null) {
                    queueTitle = initialStatus.title

                    if (preloadItem != null && q != null) {
                        queueBoard.value.renameQueue(q!!, queueTitle)
                    }
                }

                val items = ArrayList<MediaMetadata>()
                Log.d(TAG, "playQueue: Queue initial status item count: ${initialStatus.items.size}")
                if (!initialStatus.items.isEmpty()) {
                    if (preloadItem != null) {
                        items.add(preloadItem)
                        items.addAll(initialStatus.items.subList(1, initialStatus.items.size))
                    } else {
                        items.addAll(initialStatus.items)
                    }
                    val q = queueBoard.value.addQueue(
                        queueTitle ?: getString(R.string.queue),
                        items,
                        shuffled = queue.startShuffled,
                        startIndex = if (initialStatus.mediaItemIndex > 0) initialStatus.mediaItemIndex else 0,
                        replace = replace || preloadItem != null,
                        continuationEndpoint = if (isRadio) items.takeLast(4).shuffled().first().id else null // yq?.getContinuationEndpoint()
                    )
                    queueBoard.value.setCurrQueue(q, shouldResume)
                }

                player.prepare()
                player.playWhenReady = playWhenReady
            } catch (e: Exception) {
                reportException(e)
                Toast.makeText(this@MusicService, "plr: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }

            Log.d(TAG, "playQueue: Queue additional data resolution complete")
        }
    }

    /**
     * Add items to queue, right after current playing item
     */
    fun enqueueNext(items: List<MediaItem>) {
        scope.launch {
            if (!qbInit.value) {

                // when enqueuing next when player isn't active, play as a new song
                if (items.isNotEmpty()) {
                    playQueue(
                        ListQueue(
                            title = items.first().mediaMetadata.title.toString(),
                            items = items.mapNotNull { it.metadata }
                        )
                    )
                }
            } else {
                // enqueue next
                queueBoard.value.getCurrentQueue()?.let {
                    queueBoard.value.addSongsToQueue(it, player.currentMediaItemIndex + 1, items.mapNotNull { it.metadata })
                }
            }
        }
    }

    /**
     * Add items to end of current queue
     */
    fun enqueueEnd(items: List<MediaItem>) {
        queueBoard.value.enqueueEnd(items.mapNotNull { it.metadata })
    }

    fun triggerShuffle() {
        val oldIndex = player.currentMediaItemIndex
        queueBoard.value.setCurrQueuePosIndex(oldIndex)
        val currentQueue = queueBoard.value.getCurrentQueue() ?: return

        // shuffle and update player playlist
        if (!currentQueue.shuffled) {
            queueBoard.value.shuffleCurrent()
        } else {
            queueBoard.value.unShuffleCurrent()
        }
        queueBoard.value.setCurrQueue()

        updateNotification()
    }

    suspend fun initQueue() {
        Log.i(TAG, "+initQueue()")
        val persistQueue = dataStore.get(PersistentQueueKey, true)
        val maxQueues = dataStore.get(MaxQueuesKey, 19)
        if (persistQueue) {
            queueBoard.value = QueueBoard(this, queueBoard.value.masterQueues, database.readQueue().toMutableList(), maxQueues)
        } else {
            queueBoard.value = QueueBoard(this, queueBoard.value.masterQueues, maxQueues = maxQueues)
        }
        Log.d(TAG, "Queue with $maxQueues queue limit. Persist queue = $persistQueue. Queues loaded = ${queueBoard.value.masterQueues.size}")
        qbInit.value = true
        Log.i(TAG, "-initQueue()")
    }

    fun deInitQueue() {
        Log.i(TAG, "+deInitQueue()")
        val pos = player.currentPosition
        queueBoard.value.shutdown()
        if (dataStore.get(PersistentQueueKey, true)) {
            runBlocking(Dispatchers.IO) {
                saveQueueToDisk(pos)
            }
        }
        // do not replace the object. Can lead to entire queue being deleted even though it is supposed to be saved already
        qbInit.value = false
        Log.i(TAG, "-deInitQueue()")
    }

    suspend fun saveQueueToDisk(currentPosition: Long) {
        val data = queueBoard.value.getAllQueues()
        data.last().lastSongPos = currentPosition
        database.updateAllQueues(data)
    }


// Audio playback

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    private fun createCacheDataSource(): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient.Builder()
                                    .build()
                            )
                        )
                    )
                    .setCacheWriteDataSinkFactory(
                        HybridCacheDataSinkFactory(playerCache) { dataSpec ->
                            val isLocal = queueBoard.value.getCurrentQueue()?.findSong(dataSpec.key ?: "")?.isLocal == true
                            Log.d(TAG, "SONG CACHE: ${!isLocal}")
                            !isLocal
                        }
                    )
                    .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val uri = dataSpec.uri.toString()

            // JioSaavn songs: resolve saavn:ID to actual stream URL
            if (uri.startsWith("saavn:")) {
                val saavnId = uri.removePrefix("saavn:")
                val cachedUrl = streamUrlCache[saavnId]
                if (cachedUrl != null) {
                    return@Factory dataSpec.withUri(cachedUrl.toUri())
                }

                // Fetch stream URL from JioSaavn API
                val song = runBlocking(Dispatchers.IO) {
                    JioSaavn.getSong(saavnId)
                }
                val streamUrl = with(JioSaavn) { song?.streamUrl() }
                    ?: throw java.io.IOException("Failed to resolve Saavn stream URL for $saavnId")

                streamUrlCache[saavnId] = streamUrl
                return@Factory dataSpec.withUri(streamUrl.toUri())
            }

            // JioSaavn direct CDN URLs pass through immediately
            if (dataSpec.uri.scheme == "https") {
                return@Factory dataSpec  // already a direct URL, play it
            }
            // Local files pass through
            if (dataSpec.uri.scheme == "content" || dataSpec.uri.scheme == "file") {
                return@Factory dataSpec
            }
            
            // Unsupported URI scheme — log and return original
            android.util.Log.w("MusicService", "Unsupported URI: $uri")
            dataSpec
        }
    }

    private fun createRenderersFactory(gaplessOffloadAllowed: Boolean): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(this@MusicService)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            emptyArray(),
                            SilenceSkippingAudioProcessor(),
                            SonicAudioProcessor()
                        )
                    )
                    .setAudioOffloadSupportProvider(
                        MyAudioOffloadSupportProvider(
                            DefaultAudioOffloadSupportProvider(context),
                            !gaplessOffloadAllowed
                        )
                    )
                    .build()
            }
        }
    }


// Misc

    fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton.Builder(ICON_UNDEFINED)
                    .setDisplayName(getString(if (queueBoard.value.getCurrentQueue()?.shuffled == true) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setSessionCommand(CommandToggleShuffle)
                    .setCustomIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle_off)
                    .build(),
                CommandButton.Builder(ICON_UNDEFINED)
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            }
                        )
                    )
                    .setCustomIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat_off
                            REPEAT_MODE_ONE -> R.drawable.repeat_one
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        }
                    )
                    .setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton.Builder(if (currentSong.value?.song?.liked == true) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                    .setDisplayName(getString(if (currentSong.value?.song?.liked == true) R.string.action_remove_like else R.string.action_like))
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_RADIO)
                    .setDisplayName(getString(R.string.start_radio))
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build()
            )
        )
    }

    fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
        Toast.makeText(this@MusicService, getString(R.string.wait_to_reconnect), Toast.LENGTH_LONG).show()
    }

    fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_PLAYER_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()

            Toast.makeText(this@MusicService, getString(R.string.err_play_next_on_error), Toast.LENGTH_SHORT).show()
            return
        }

        player.pause()
        Toast.makeText(this@MusicService, getString(R.string.err_stop_on_too_many_errors), Toast.LENGTH_LONG).show()
        consecutivePlaybackErr = 0
    }

    fun stopOnError() {
        player.pause()
        Toast.makeText(this@MusicService, getString(R.string.err_stop_on_error), Toast.LENGTH_LONG).show()
    }


// Player overrides

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        // wait for reconnection
        val isConnectionError = (error.cause?.cause is PlaybackException)
                && (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        if (!isNetworkConnected.value || isConnectionError) {
            waitOnNetworkError()
            return
        }

        if (dataStore.get(SkipOnErrorKey, false)) {
            skipOnError()
        } else {
            stopOnError()
        }

        Toast.makeText(
            this@MusicService,
            "plr: ${error.message} (${error.errorCode}): ${error.cause?.message ?: ""} ",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) {
            val pos = player.currentPosition
            val q = queueBoard.value.getCurrentQueue()
            q?.lastSongPos = pos
        }
        super.onIsPlayingChanged(isPlaying)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)

        // Clear cache on song change
        streamUrlCache.clear()
        previousMediaId = mediaItem?.mediaId
        // +2 when and error happens, and -1 when transition. Thus when error, number increments by 1, else doesn't change
        if (consecutivePlaybackErr > 0) {
            consecutivePlaybackErr--
        }

        if (player.isPlaying && reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            player.prepare()
            player.play()
        }

        // Auto load more songs disabled (was YouTube-based)

        queueBoard.value.setCurrQueuePosIndex(player.currentMediaItemIndex)

        val lastFmEnabled = dataStore.get(LastFmScrobblingEnabledKey, false)
        if (lastFmEnabled) {
            val artist = mediaItem?.metadata?.artists?.firstOrNull()?.name ?: "Unknown"
            val track = mediaItem?.metadata?.title ?: currentSong.value?.song?.title ?: "Unknown"
            val album: String? = null

            if (track.isNotBlank()) {
                scope.launch {
                    lastFmScrobbler.updateNowPlaying(
                        artist = artist,
                        track = track,
                        album = album
                    )
                }

                scope.launch {
                    delay(30_000)
                    if (player.isPlaying) {
                        lastFmScrobbler.scrobble(
                            artist = artist,
                            track = track,
                            album = album,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        // reshuffle queue when shuffle AND repeat all are enabled
        // no, when repeat mode is on, player does not "STATE_ENDED"
        if (player.currentMediaItemIndex == player.mediaItemCount - 1 &&
            (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
            player.shuffleModeEnabled && player.repeatMode == REPEAT_MODE_ALL
        ) {
            scope.launch(SilentHandler) {
                // or else race condition: Assertions.checkArgument(eventTime.realtimeMs >= currentPlaybackStateStartTimeMs) fails in updatePlaybackState()
                delay(200)
                queueBoard.value.shuffleCurrent(player.mediaItemCount > 2)
                queueBoard.value.setCurrQueue()
            }
        }

        updateNotification() // also updates when queue changes
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == STATE_IDLE) {
            queuePlaylistId = null
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
            } else {
                closeAudioEffectSession()
                if (!player.playWhenReady) {
                    waitingForNetworkConnection.value = false
                }
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        offloadScope.launch {
            val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
            var minPlaybackDur = (dataStore.get(minPlaybackDurKey, 30).toFloat() / 100)
            // ensure within bounds
            if (minPlaybackDur >= 1f) {
                minPlaybackDur = 0.99f // Ehhh 99 is good enough to avoid any rounding errors
            } else if (minPlaybackDur < 0.01f) {
                minPlaybackDur = 0.01f // Still want "spam skipping" to not count as plays
            }

            val playRatio =
                playbackStats.totalPlayTimeMs.toFloat() / ((mediaItem.metadata?.duration?.times(1000)) ?: -1)
            Log.d(TAG, "Playback ratio: $playRatio Min threshold: $minPlaybackDur")
            if (playRatio >= minPlaybackDur && !dataStore.get(PauseListenHistoryKey, false)) {
                database.query {
                    incrementPlayCount(mediaItem.mediaId)
                    try {
                        insert(
                            Event(
                                songId = mediaItem.mediaId,
                                timestamp = LocalDateTime.now(),
                                playTime = playbackStats.totalPlayTimeMs
                            )
                        )
                    } catch (_: SQLException) {
                    }
                }

                // Remote history registration removed (YouTube/JioSaavn)
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        offloadScope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode as Int
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        val q = queueBoard.value.getCurrentQueue()
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(player.mediaItemCount))
        if (q == null || q.shuffled == shuffleModeEnabled) return
        triggerShuffle()
    }


    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        // FG keep alive
        if (player.isPlaying || !dataStore.get(KeepAliveKey, false)) {
            super.onUpdateNotification(session, startInForegroundRequired)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Terminating MusicService.")
        deInitQueue()

        mediaSession.player.stop()
        mediaSession.release()
        mediaSession.player.release()
        super.onDestroy()
        Log.i(TAG, "Terminated MusicService.")
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved called")
        if (dataStore.get(StopMusicOnTaskClearKey, true) && !dataStore.get(KeepAliveKey, false)) {
            Log.i(TAG, "onTaskRemoved kill")
            pauseAllPlayersAndStopSelf()
        } else {
            Log.i(TAG, "onTaskRemoved def")
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val SEARCH = "search"

        const val CHANNEL_ID = "music_channel_01"
        const val CHANNEL_NAME = "fgs_workaround"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        // const val CHUNK_LENGTH = 2 * 1024 * 1024L  // 2MB chunks — no longer needed for keepalive connections

        const val COMMAND_GET_BINDER = "GET_BINDER"
    }
}

class MyAudioOffloadSupportProvider(
    private val default: DefaultAudioOffloadSupportProvider,
    private val disableGaplessOffload: Boolean
) : DefaultAudioSink.AudioOffloadSupportProvider by default {
    override fun getAudioOffloadSupport(
        format: Format,
        audioAttributes: AudioAttributes
    ): AudioOffloadSupport {
        val defaultResult = default.getAudioOffloadSupport(format, audioAttributes)
        val audioOffloadSupport = AudioOffloadSupport.Builder()
        return audioOffloadSupport
            .setIsFormatSupported(defaultResult.isFormatSupported)
            .setIsGaplessSupported(defaultResult.isGaplessSupported && !disableGaplessOffload)
            .setIsSpeedChangeSupported(defaultResult.isSpeedChangeSupported)
            .build()
    }
}
