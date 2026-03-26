/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vynce.app.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vynce.app.constants.AlbumFilter
import com.vynce.app.constants.AlbumFilterKey
import com.vynce.app.constants.AlbumSortDescendingKey
import com.vynce.app.constants.AlbumSortType
import com.vynce.app.constants.AlbumSortTypeKey
import com.vynce.app.constants.ArtistFilter
import com.vynce.app.constants.ArtistFilterKey
import com.vynce.app.constants.ArtistSongSortDescendingKey
import com.vynce.app.constants.ArtistSongSortType
import com.vynce.app.constants.ArtistSongSortTypeKey
import com.vynce.app.constants.ArtistSortDescendingKey
import com.vynce.app.constants.ArtistSortType
import com.vynce.app.constants.ArtistSortTypeKey
import com.vynce.app.constants.LibrarySortDescendingKey
import com.vynce.app.constants.LibrarySortType
import com.vynce.app.constants.LibrarySortTypeKey
import com.vynce.app.constants.PlaylistFilter
import com.vynce.app.constants.PlaylistFilterKey
import com.vynce.app.constants.PlaylistSortDescendingKey
import com.vynce.app.constants.PlaylistSortType
import com.vynce.app.constants.PlaylistSortTypeKey
import com.vynce.app.constants.SongFilter
import com.vynce.app.constants.SongFilterKey
import com.vynce.app.constants.SongSortDescendingKey
import com.vynce.app.constants.SongSortType
import com.vynce.app.constants.SongSortTypeKey
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.Album
import com.vynce.app.db.entities.Artist
import com.vynce.app.db.entities.Playlist
import com.vynce.app.db.entities.Song
import com.vynce.app.extensions.toEnum
import com.vynce.app.models.DirectoryTree
import com.vynce.app.ui.utils.STORAGE_ROOT
import com.vynce.app.ui.utils.cacheDirectoryTree
import com.vynce.app.ui.utils.getDirectoryTree
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.scanners.LocalMediaScanner.Companion.refreshLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    val allSongs = getSyncedSongs(context, database)
    val isSyncingRemoteLikedSongs = MutableStateFlow(false)
    val isSyncingRemoteSongs = MutableStateFlow(false)

    fun syncLibrarySongs(bypassCd: Boolean = false) {
    }

    fun syncLikedSongs(bypassCd: Boolean = false) {
    }

    private fun getSyncedSongs(context: Context, database: MusicDatabase): StateFlow<List<Song>?> {

        return context.dataStore.data
            .map {
                Triple(
                    it[SongFilterKey].toEnum(SongFilter.LIKED),
                    it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    (it[SongSortDescendingKey] != false)
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending)
                    SongFilter.LIKED -> database.likedSongs(sortType, descending)
                    SongFilter.DOWNLOADED -> database.downloadSongs(sortType, descending)
                    SongFilter.HISTORY -> database.historySongs()
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }
}

@HiltViewModel
class LibraryFoldersViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val TAG = LibraryFoldersViewModel::class.simpleName.toString()
    val path = savedStateHandle.get<String>("path")?.replace(';', '/') ?: STORAGE_ROOT

    val localSongDirectoryTree: MutableStateFlow<DirectoryTree> = MutableStateFlow(getDirectoryTree(path))
    val localSongDtSongCount = MutableStateFlow(0)
    val filteredSongs = mutableStateListOf<Song>()

    var uiInit = false
    var lastLocalScan = 0L

    /**
     * Trigger a scan of local directory
     */
    suspend fun getLocalSongs(dir: String? = null) {
        Log.d(TAG, "Loading folders page: ${dir ?: path}")
        val dt = refreshLocal(database, dir ?: path)
        dt.isSkeleton = false
        cacheDirectoryTree(dt)
        localSongDirectoryTree.value = dt
    }

    /**
     * Get total number of songs in directory
     */
    suspend fun getSongCount(dir: String? = null) {
        Log.d(TAG, "Loading folder song count: ${dir ?: path}")
        localSongDtSongCount.value = database.localSongCountInPath(dir ?: path).first()
    }

    /**
     * Update filteredSongs with search query
     */
    fun searchInDir(query: String, dir: String = path) {
        if (query.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val dbSongs = database.searchSongsAllLocalInDir(dir, query).first()
                filteredSongs.clear()
                filteredSongs.addAll(dbSongs)
            }
        }
    }
}

@HiltViewModel
class LibraryArtistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val isSyncingRemoteArtists = MutableStateFlow(false)

    val allArtists = context.dataStore.data
        .map {
            Triple(
                it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                it[ArtistSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            database.artists(filter, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun syncArtists(bypassCd: Boolean = false) {
    }

    init {
    }
}

@HiltViewModel
class LibraryAlbumsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val isSyncingRemoteAlbums = MutableStateFlow(false)

    val allAlbums = context.dataStore.data
        .map {
            Triple(
                it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                it[AlbumSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            database.albums(filter, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun syncAlbums(bypassCd: Boolean = false) {
    }

    init {
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val isSyncingRemotePlaylists = MutableStateFlow(false)

    val allPlaylists = context.dataStore.data
        .map {
            Triple(
                it[PlaylistFilterKey].toEnum(PlaylistFilter.LIBRARY),
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE),
                it[PlaylistSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            database.playlists(filter, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun syncPlaylists(bypassCd: Boolean = false) {
    }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {

    val isSyncingRemoteLikedSongs = MutableStateFlow(false)
    val isSyncingRemoteSongs = MutableStateFlow(false)
    val isSyncingRemoteAlbums = MutableStateFlow(false)
    val isSyncingRemoteArtists = MutableStateFlow(false)
    val isSyncingRemotePlaylists = MutableStateFlow(false)

    var artists = database.artistsBookmarkedAsc().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = database.albumsLikedAsc().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = database.playlistInLibraryAsc().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allItems = context.dataStore.data
        .map {
            it[LibrarySortTypeKey].toEnum(LibrarySortType.CREATE_DATE) to (it[LibrarySortDescendingKey] != false)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            combine(artists, albums, playlists) { artists, albums, playlists ->
                val items = artists + albums + playlists
                items.sortedBy { item ->
                    when (sortType) {
                        LibrarySortType.CREATE_DATE -> when (item) {
                            is Album -> item.album.bookmarkedAt
                            is Artist -> item.artist.bookmarkedAt
                            is Playlist -> item.playlist.bookmarkedAt
                            else -> LocalDateTime.now()
                        }

                        else -> when (item) {
                            is Album -> item.album.title.lowercase()
                            is Artist -> item.artist.name.lowercase()
                            is Playlist -> item.playlist.name.lowercase()
                            else -> ""
                        }
                    }.toString()
                }.let { if (descending) it.reversed() else it }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncAll(bypassCd: Boolean = false) {
    }
}

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs = context.dataStore.data
        .map {
            it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.artistSongs(artistId, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

