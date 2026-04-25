/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.utils.scanners

import android.util.Log
import com.vynce.app.models.SongTempData
import java.io.File

class TagLibScanner : MetadataScanner {

    companion object {
        private const val TAG = "TagLibScanner"

        /**
         * Check if TagLib native libraries are available.
         * Since the taglib submodule was not initialized, this always returns false.
         */
        fun isAvailable(): Boolean {
            return try {
                // Try to load the native library - will fail if not present
                System.loadLibrary("taglib_jni")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        Log.w(TAG, "TagLib scanner is unavailable (native submodules not initialized). File: ${file.name}")
        throw UnavailableScannerException("TagLib scanner is unavailable because native submodules were not initialized")
    }
}

/**
 * Exception indicating a scanner implementation is not available.
 * This is caught and handled gracefully by the LocalMediaScanner.
 */
class UnavailableScannerException(message: String) : Exception(message)
