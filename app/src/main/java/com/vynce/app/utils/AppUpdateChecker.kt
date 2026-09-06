/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.vynce.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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

            val body = response.body.string()
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val releaseNotes = json.optString("body", "No release notes available.")
            val publishedAt = json.optString("published_at", "")
            val assets = json.optJSONArray("assets") ?: JSONArray()

            val apkAssets = mutableListOf<Pair<String, String>>()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    apkAssets.add(name to url)
                }
            }

            // Find best matching APK based on device supported ABIs, fallback to universal, then first APK
            var downloadUrl: String? = null
            val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()

            // 1. Check for device-specific ABI match
            for (abi in supportedAbis) {
                val match = apkAssets.firstOrNull { it.first.contains(abi, ignoreCase = true) }
                if (match != null) {
                    downloadUrl = match.second
                    Log.i(TAG, "Selected ABI-specific APK (${match.first}) for ABI $abi")
                    break
                }
            }

            // 2. Check for universal APK fallback
            if (downloadUrl == null) {
                val universalMatch = apkAssets.firstOrNull { it.first.contains("universal", ignoreCase = true) }
                if (universalMatch != null) {
                    downloadUrl = universalMatch.second
                    Log.i(TAG, "Selected universal APK (${universalMatch.first})")
                }
            }

            // 3. Fallback to any APK
            if (downloadUrl == null && apkAssets.isNotEmpty()) {
                downloadUrl = apkAssets.first().second
                Log.i(TAG, "Selected fallback APK (${apkAssets.first().first})")
            }

            val remoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val currentVersion = BuildConfig.VERSION_NAME.trim()

            val isNewer = isNewerVersion(remoteVersion, currentVersion)
            Log.i(TAG, "Current: $currentVersion, Remote: $remoteVersion, Update: $isNewer, Url: $downloadUrl")

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

    private var activeDownloadCall: okhttp3.Call? = null

    /**
     * Cancel an ongoing download.
     */
    fun cancelDownload() {
        activeDownloadCall?.cancel()
        activeDownloadCall = null
    }

    /**
     * Check if a completed update APK already exists in cache and verify it matches the expected version.
     */
    fun getCachedApk(context: Context, expectedVersion: String? = null): File? {
        try {
            val updateDir = File(context.cacheDir, "updates")
            val apkFile = File(updateDir, "vynce-update.apk")
            if (!apkFile.exists() || apkFile.length() < 1_000_000L) {
                return null
            }

            // Validate that the cached APK is a valid package and matches the expected version
            val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (pkgInfo == null) {
                Log.w(TAG, "Cached APK is invalid or corrupt. Deleting.")
                apkFile.delete()
                return null
            }

            if (expectedVersion != null && pkgInfo.versionName != expectedVersion) {
                Log.i(TAG, "Cached APK version (${pkgInfo.versionName}) != expected ($expectedVersion). Deleting stale APK.")
                apkFile.delete()
                return null
            }

            return apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cached APK", e)
            return null
        }
    }

    /**
     * Download the APK to the app's cache directory via a temporary file.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates")
        updateDir.mkdirs()

        val tmpFile = File(updateDir, "vynce-update.apk.tmp")
        val apkFile = File(updateDir, "vynce-update.apk")

        if (tmpFile.exists()) {
            tmpFile.delete()
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Vynce-App/${BuildConfig.VERSION_NAME}")
                .build()

            val call = client.newCall(request)
            activeDownloadCall = call
            val response = call.execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: HTTP ${response.code}")
                return@withContext null
            }

            val responseBody = response.body
            val contentLength = responseBody.contentLength()
            val inputStream = responseBody.byteStream()

            FileOutputStream(tmpFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read

                    val progress = if (contentLength > 0) bytesRead.toFloat() / contentLength.toFloat() else -1f
                    onProgress(progress, bytesRead, contentLength)
                }
                output.flush()
            }

            activeDownloadCall = null

            // Verify the download completed fully
            if (contentLength > 0 && tmpFile.length() != contentLength) {
                Log.e(TAG, "Download incomplete: expected $contentLength bytes, got ${tmpFile.length()}")
                tmpFile.delete()
                return@withContext null
            }

            // Verify the downloaded file is a valid Android APK package
            val pkgInfo = context.packageManager.getPackageArchiveInfo(tmpFile.absolutePath, 0)
            if (pkgInfo == null) {
                Log.e(TAG, "Downloaded file is not a valid APK package")
                tmpFile.delete()
                return@withContext null
            }

            if (apkFile.exists()) {
                apkFile.delete()
            }

            val renamed = tmpFile.renameTo(apkFile)
            val finalFile = if (renamed) apkFile else tmpFile

            Log.i(TAG, "APK downloaded & verified: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
            finalFile
        } catch (e: Exception) {
            activeDownloadCall = null
            Log.e(TAG, "Failed to download APK", e)
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            null
        }
    }

    /**
     * Trigger the Android package installer to install the downloaded APK.
     * Handles unknown sources permission check on Android 8.0+ (Oreo+).
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val resolveInfoList = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
        }
    }

    /**
     * Compare two semantic version strings (e.g., "3.0.0" > "2.2.0").
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

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
