package com.vynce.app.utils.listentogether

import android.content.Context
import com.vynce.app.data.spotify.SpotifyResolver
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
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
    private const val SYNC_MS = 280L
    private const val CONTROLLER_PUBLISH_MS = 500L
    private const val SEEK_TOLERANCE_MS = 750L
    private const val USER_SEEK_TOLERANCE_MS = 1800L
    private const val APPLY_GRACE_MS = 900L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var appContext: Context? = null
    private var playerConnection: PlayerConnection? = null
    private var lastAppliedVersion = 0L
    private var lastControllerPublishAt = 0L
    private var suppressLocalPublishUntil = 0L

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
                    _message.value = it.message
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
            _message.value = "Left room"
            syncJob?.cancel()
        }
    }

    private fun restartSync() {
        syncJob?.cancel()
        val connection = playerConnection ?: return
        syncJob =
            scope.launch {
                while (true) {
                    val activeSession = _session.value ?: break
                    syncOnce(connection, activeSession)
                    delay(SYNC_MS)
                }
            }
    }

    private suspend fun syncOnce(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        runCatching {
            ListenTogetherClient.getSession(session.code)
        }.onSuccess { remoteSession ->
            val keptSession = remoteSession.copy(participantId = session.participantId)
            _session.value = keptSession
            val remoteState = remoteSession.state ?: return@onSuccess
            val now = System.currentTimeMillis()
            val isController = remoteSession.controllerId == session.participantId

            if (remoteSession.stateVersion > lastAppliedVersion) {
                applyRemoteState(connection, remoteSession)
                lastAppliedVersion = remoteSession.stateVersion
                return@onSuccess
            }

            if (isController && now - lastControllerPublishAt >= CONTROLLER_PUBLISH_MS) {
                publishLocalState(connection, keptSession)
                lastControllerPublishAt = now
                return@onSuccess
            }

            if (now > suppressLocalPublishUntil && localUserChangedPlayback(connection, remoteSession)) {
                publishLocalState(connection, keptSession)
            } else if (!isController) {
                softCorrectPosition(connection, remoteSession)
            }
        }.onFailure {
            _message.value = it.message
        }
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
            _message.value = it.message
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
            // Attempt to resolve lossless JioSaavn song or fallback to generic metadata
            val resolvedSong = SpotifyResolver.resolveToSaavn(
                trackName = remoteState.title,
                artistName = remoteState.artists.firstOrNull() ?: ""
            )
            val metadata = resolvedSong?.toSaavnMediaMetadata() ?: remoteState.toMediaMetadata()

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
