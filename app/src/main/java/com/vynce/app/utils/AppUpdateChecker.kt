/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.vynce.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for new app versions and handles APK download + install.
 */
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/2300030811/Vynce/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val newVersion: String,
        val currentVersion: String,
        val releaseNotes: String,
        val downloadUrl: String?,
        val publishedAt: String = ""
    )

    /**
     * Check GitHub for the latest release and compare with the installed version.
     */
    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API returned ${response.code}")
                return@withContext noUpdate()
            }

            val body = response.body?.string() ?: return@withContext noUpdate()
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val releaseNotes = json.optString("body", "No release notes available.")
            val publishedAt = json.optString("published_at", "")
            val assets = json.optJSONArray("assets") ?: JSONArray()

            // Find the universal APK or any APK asset
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    // Prefer universal APK
                    if (name.contains("universal", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                    // Fallback to first APK found
                    if (downloadUrl == null) {
                        downloadUrl = asset.optString("browser_download_url")
                    }
                }
            }

            val remoteVersion = tagName.removePrefix("v").removePrefix("V")
            val currentVersion = BuildConfig.VERSION_NAME

            val isNewer = isNewerVersion(remoteVersion, currentVersion)
            Log.i(TAG, "Current: $currentVersion, Remote: $remoteVersion, Update: $isNewer")

            UpdateInfo(
                isUpdateAvailable = isNewer,
                newVersion = remoteVersion,
                currentVersion = currentVersion,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                publishedAt = publishedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            noUpdate()
        }
    }

    /**
     * Download the APK to the app's cache directory.
     * @param onProgress callback with progress (0.0 to 1.0), -1 for indeterminate
     * @return the downloaded File, or null on failure
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            updateDir.mkdirs()

            // Clean up old downloads
            updateDir.listFiles()?.forEach { it.delete() }

            val apkFile = File(updateDir, "vynce-update.apk")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body ?: return@withContext null
            val contentLength = responseBody.contentLength()
            val inputStream = responseBody.byteStream()

            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read

                    if (contentLength > 0) {
                        onProgress(bytesRead.toFloat() / contentLength.toFloat())
                    } else {
                        onProgress(-1f)
                    }
                }
            }

            // Verify the download completed fully
            if (contentLength > 0 && apkFile.length() != contentLength) {
                Log.e(TAG, "Download incomplete: expected $contentLength bytes, got ${apkFile.length()}")
                apkFile.delete()
                return@withContext null
            }

            Log.i(TAG, "APK downloaded: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK", e)
            null
        }
    }

    /**
     * Trigger the Android package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(intent)
    }

    /**
     * Compare two semantic version strings (e.g., "2.0.1" > "2.0.0").
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Version comparison failed: remote=$remote, current=$current", e)
        }
        return false
    }

    private fun noUpdate() = UpdateInfo(
        isUpdateAvailable = false,
        newVersion = BuildConfig.VERSION_NAME,
        currentVersion = BuildConfig.VERSION_NAME,
        releaseNotes = "",
        downloadUrl = null
    )
}
