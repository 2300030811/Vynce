package com.vynce.app.utils.scanners

import com.vynce.app.models.SongTempData
import java.io.File

class FFmpegScanner : MetadataScanner {
    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        throw UnsupportedOperationException("FFmpeg scanner is unavailable because native submodules were not initialized")
    }

    companion object {
        const val VERSION_STRING = "unavailable"
    }
}
