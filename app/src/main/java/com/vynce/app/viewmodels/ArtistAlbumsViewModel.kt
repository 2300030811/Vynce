package com.vynce.app.viewmodels

import androidx.lifecycle.SavedStateHandle
import com.vynce.app.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ArtistAlbumsViewModel @Inject constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : DatabaseViewModel(database) {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist = database.artist(artistId).asStateFlow(null)

    val albums = database.artistAlbumsPreview(artistId).asStateFlow(emptyList())
}
