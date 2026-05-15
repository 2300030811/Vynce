/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.vynce.app.constants.MaxImageCacheSizeKey
import com.vynce.app.utils.CoilBitmapLoader
import com.vynce.app.utils.LocalArtworkPathKeyer
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.get
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {
    private val TAG = App::class.simpleName.toString()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            System.setProperty("kotlinx.coroutines.debug", "on")
        }

        instance = this

        // JioSaavn initialization (if any needed)
        // No special locale/visitor data needed for basic JioSaavn usage
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        // When cacheSize is 0, disk cache is disabled to prevent corruption
        // of existing cache entries. Memory cache remains active.
        if (cacheSize == 0) {
            return ImageLoader.Builder(this)
                .components {
                    add(CoilBitmapLoader.Factory(this@App))
                    add(LocalArtworkPathKeyer())
                }
                .crossfade(150)
                // allowHardware MUST be true for fast GPU-accelerated rendering in LazyLists.
                // Only disable if you read raw bitmap pixels (e.g. palette extraction from Bitmap).
                .allowHardware(true)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.4)
                        .build()
                }
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }

        return ImageLoader.Builder(this)
            .components {
                add(CoilBitmapLoader.Factory(this@App))
                add(LocalArtworkPathKeyer())
            }
            .crossfade(150)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.4)
                    .build()
            }
            .diskCache(
                // Local images should bypass with DataSource.DISK
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes((cacheSize ?: 512) * 1024 * 1024L)
                    .build()
            )
            .build()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
