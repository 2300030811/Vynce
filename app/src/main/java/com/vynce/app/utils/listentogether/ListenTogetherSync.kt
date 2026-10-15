package com.vynce.app.utils.listentogether

import android.content.Context
import com.vynce.app.data.spotify.SpotifyResolver
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.utils.ProxyHelper
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.toSaavnMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

object ListenTogetherSync {
    private const val ACTIVE_SYNC_MS = 400L
    private const val STABLE_SYNC_MS = 1000L
    private const val CONTROLLER_PUBLISH_MS = 600L
    private const val SEEK_TOLERANCE_MS = 750L
    private const val USER_SEEK_TOLERANCE_MS = 1800L
    private const val APPLY_GRACE_MS = 1200L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var appContext: Context? = null
    private var playerConnection: PlayerConnection? = null
    private var lastAppliedVersion = 0L
    private var lastControllerPublishAt = 0L
    private var suppressLocalPublishUntil = 0L
    private var consecutiveFailures = 0

    private val _session = MutableStateFlow<ListenTogetherSession?>(null)
    val session = _session.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost = _isHost.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _displayName = MutableStateFlow(ListenTogetherStore.defaultName())
    val displayName = _displayName.asStateFlow()

    fun start(
        context: Context,
        connection: PlayerConnection,
    ) {
        appContext = context.applicationContext
        playerConnection = connection
        ListenTogetherClient.proxy = ProxyHelper.getProxy(context.applicationContext.dataStore)

        val saved = ListenTogetherStore.load(context.applicationContext)
        if (saved != null && _session.value == null) {
            _displayName.value = saved.displayName
            _isHost.value = saved.isHost
            scope.launch {
                runCatching {
                    ListenTogetherClient.getSession(saved.code).copy(participantId = saved.participantId)
                }.onSuccess {
                    _session.value = it
                    lastAppliedVersion = it.stateVersion
                    restartSync()
                }.onFailure {
                    ListenTogetherStore.clear(context.applicationContext)
                }
            }
        } else if (_session.value != null) {
            restartSync()
        }
    }

    fun setDisplayName(name: String) {
        _displayName.value = name.ifBlank { ListenTogetherStore.defaultName() }
    }

    fun createSession() {
        val context = appContext ?: return
        val connection = playerConnection ?: return
        val metadata = connection.mediaMetadata.value ?: run {
            _message.value = "Play a song first to start a room"
            return
        }

        scope.launch {
            runCatching {
                ListenTogetherClient.createSession(
                    context = context,
                    displayName = _displayName.value,
                    state =
                        ListenTogetherPlaybackState(
                            songId = metadata.id,
                            title = metadata.title,
                            artists = metadata.artists.map { it.name },
                            thumbnailUrl = metadata.thumbnailUrl,
                            positionMs = connection.player.currentPosition,
                            isPlaying = connection.player.playWhenReady,
                        )
                )
            }.onSuccess {
                _session.value = it
                _isHost.value = true
                lastAppliedVersion = it.stateVersion
                ListenTogetherStore.save(context, it, _displayName.value, true)
                _message.value = "Room created"
                restartSync()
            }.onFailure {
                _message.value = it.message ?: "Failed to create room"
            }
        }
    }

    fun joinSession(code: String) {
        val context = appContext ?: return
        scope.launch {
            runCatching {
                ListenTogetherClient.joinSession(code.trim().uppercase(), _displayName.value)
            }.onSuccess {
                _session.value = it
                _isHost.value = false
                lastAppliedVersion = 0L
                ListenTogetherStore.save(context, it, _displayName.value, false)
                _message.value = "Joined room"
                restartSync()
            }.onFailure {
                _message.value = it.message ?: "Failed to join room"
            }
        }
    }

    fun leaveSession() {
        val context = appContext
        val activeSession = _session.value
        scope.launch {
            if (activeSession != null) {
                runCatching {
                    ListenTogetherClient.leaveSession(activeSession.code, activeSession.participantId)
                }
            }
            if (context != null) ListenTogetherStore.clear(context)
            _session.value = null
            _isHost.value = false
            lastAppliedVersion = 0L
            lastControllerPublishAt = 0L
            suppressLocalPublishUntil = 0L
            consecutiveFailures = 0
            _message.value = "Left room"
            syncJob?.cancel()
        }
    }

    private fun restartSync() {
        syncJob?.cancel()
        val connection = playerConnection ?: return
        consecutiveFailures = 0
        syncJob =
            scope.launch {
                while (true) {
                    val activeSession = _session.value ?: break
                    val isChanging = syncOnce(connection, activeSession)
                    val isPlaying = connection.player.playWhenReady
                    val nextDelay = when {
                        isChanging -> 350L
                        isPlaying -> 1000L
                        else -> 2000L
                    }
                    delay(nextDelay)
                }
            }
    }

    private suspend fun syncOnce(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ): Boolean {
        var isDynamic = false
        runCatching {
            ListenTogetherClient.getSession(session.code, session.participantId)
        }.onSuccess { remoteSession ->
            consecutiveFailures = 0
            val keptSession = remoteSession.copy(participantId = session.participantId)
            _session.value = keptSession
            val remoteState = remoteSession.state ?: return@onSuccess
            val now = System.currentTimeMillis()
            val isController = remoteSession.controllerId == session.participantId || _isHost.value

            if (remoteSession.stateVersion > lastAppliedVersion) {
                applyRemoteState(connection, remoteSession)
                lastAppliedVersion = remoteSession.stateVersion
                isDynamic = true
                return isDynamic
            }

            if (isController && now - lastControllerPublishAt >= CONTROLLER_PUBLISH_MS) {
                publishLocalState(connection, keptSession)
                lastControllerPublishAt = now
                return isDynamic
            }

            if (isController && now > suppressLocalPublishUntil && localUserChangedPlayback(connection, remoteSession)) {
                publishLocalState(connection, keptSession)
                isDynamic = true
            } else if (!isController) {
                softCorrectPosition(connection, remoteSession)
            }
        }.onFailure {
            consecutiveFailures++
            if (consecutiveFailures == 5) {
                _message.value = "Connection lost. Reconnecting to room..."
            }
        }
        return isDynamic
    }

    private fun localUserChangedPlayback(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ): Boolean {
        val remoteState = remoteSession.state ?: return false
        val localSongId = connection.player.currentMediaItem?.mediaId
        if (localSongId != null && localSongId != remoteState.songId) return true
        if (connection.player.playWhenReady != remoteState.isPlaying) return true
        return abs(connection.player.currentPosition - expectedPosition(remoteSession)) > USER_SEEK_TOLERANCE_MS
    }

    private suspend fun publishLocalState(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        val metadata = connection.mediaMetadata.value ?: return
        runCatching {
            ListenTogetherClient.updateState(
                code = session.code,
                participantId = session.participantId,
                state =
                    ListenTogetherPlaybackState(
                        songId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        thumbnailUrl = metadata.thumbnailUrl,
                        positionMs = connection.player.currentPosition,
                        isPlaying = connection.player.playWhenReady,
                    )
            )
        }.onSuccess {
            _session.value = it.copy(participantId = session.participantId)
            lastAppliedVersion = it.stateVersion
            lastControllerPublishAt = System.currentTimeMillis()
        }.onFailure {
            // Suppress minor transient error
        }
    }

    private suspend fun applyRemoteState(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        val remoteState = remoteSession.state ?: return
        suppressLocalPublishUntil = System.currentTimeMillis() + APPLY_GRACE_MS

        val currentId = connection.player.currentMediaItem?.mediaId
        if (currentId != remoteState.songId) {
            val metadata = if (remoteState.songId.startsWith("saavn:") || remoteState.songId.startsWith("file:") || remoteState.songId.startsWith("content:")) {
                remoteState.toMediaMetadata()
            } else {
                SpotifyResolver.resolveToSaavn(
                    trackName = remoteState.title,
                    artistName = remoteState.artists.firstOrNull() ?: ""
                )?.toSaavnMediaMetadata() ?: remoteState.toMediaMetadata()
            }

            connection.playQueue(
                ListQueue(
                    title = remoteState.title,
                    items = listOf(metadata),
                    startIndex = 0,
                    position = expectedPosition(remoteSession)
                )
            )
            delay(300)
        }

        val target = expectedPosition(remoteSession)
        if (abs(connection.player.currentPosition - target) > SEEK_TOLERANCE_MS) {
            connection.player.seekTo(target)
        }

        if (remoteState.isPlaying && !connection.player.playWhenReady) {
            connection.player.play()
        } else if (!remoteState.isPlaying && connection.player.playWhenReady) {
            connection.player.pause()
        }
    }

    private fun softCorrectPosition(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        val target = expectedPosition(remoteSession)
        if (abs(connection.player.currentPosition - target) > SEEK_TOLERANCE_MS * 2) {
            suppressLocalPublishUntil = System.currentTimeMillis() + APPLY_GRACE_MS
            connection.player.seekTo(target)
        }
    }

    private fun expectedPosition(session: ListenTogetherSession): Long {
        val state = session.state ?: return 0L
        val serverAge = (session.serverNow - state.updatedAt).coerceAtLeast(0)
        return if (state.isPlaying) {
            state.positionMs + serverAge
        } else {
            state.positionMs
        }.coerceAtLeast(0)
    }
}
