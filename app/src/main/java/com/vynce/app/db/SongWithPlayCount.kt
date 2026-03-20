package com.vynce.app.db

import com.vynce.app.db.entities.Song
import com.vynce.app.db.entities.Artist
import androidx.room.Embedded

data class SongWithPlayCount(
    @Embedded val song: Song,
    val playCount: Int
)

data class ArtistWithPlayCount(
    @Embedded val artist: Artist,
    val playCount: Int
)
