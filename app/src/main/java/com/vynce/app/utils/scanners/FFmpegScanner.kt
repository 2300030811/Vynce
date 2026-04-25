package com.vynce.app.utils.scanners

import android.util.Log
import com.vynce.app.models.SongTempData
import java.io.File

class FFmpegScanner : MetadataScanner {
    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        Log.w("FFmpegScanner", "FFmpeg scanner is unavailable (native submodules not initialized). File: ${file.name}")
        throw UnavailableScannerException("FFmpeg scanner is unavailable because native submodules were not initialized")
    }

    companion object {
        const val VERSION_STRING = "unavailable"
    }
}
