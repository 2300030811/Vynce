package com.vynce.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneOffset
import com.vynce.app.constants.AUTO_SCAN_COOLDOWN
import com.vynce.app.constants.AUTO_SCAN_SOFT_COOLDOWN
import com.vynce.app.constants.AutomaticScannerKey
import com.vynce.app.constants.ExcludedScanPathsKey
import com.vynce.app.constants.LastLocalScanKey
import com.vynce.app.constants.LastVersionKey
import com.vynce.app.constants.LocalLibraryEnableKey
import com.vynce.app.constants.OOBE_VERSION
import com.vynce.app.constants.OobeStatusKey
import com.vynce.app.constants.SCANNER_OWNER_LM
import com.vynce.app.constants.ScanPathsKey
import com.vynce.app.constants.ScannerImpl
import com.vynce.app.constants.ScannerImplKey
import com.vynce.app.constants.ScannerMatchCriteria
import com.vynce.app.constants.ScannerSensitivityKey
import com.vynce.app.constants.ScannerStrictExtKey
import com.vynce.app.constants.ScannerStrictFilePathsKey
import com.vynce.app.constants.UpdateAvailableKey
import com.vynce.app.db.MusicDatabase
import com.vynce.app.models.toMediaMetadata
import com.vynce.app.playback.DownloadUtil
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.utils.MEDIA_PERMISSION_LEVEL
import com.vynce.app.ui.utils.clearDtCache
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.enumPreference
import com.vynce.app.utils.get
import com.vynce.app.utils.reportException
import com.vynce.app.utils.scanners.LocalMediaScanner
import com.vynce.app.utils.scanners.LocalMediaScanner.Companion.destroyScanner
import com.vynce.app.utils.scanners.LocalMediaScanner.Companion.scannerState
import com.vynce.app.utils.scanners.LocalMediaScanner.Companion.getScanner
import com.vynce.app.utils.lmScannerCoroutine
import com.vynce.app.utils.scanners.uriListFromString
import kotlinx.coroutines.delay

suspend fun scanInit(
    context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    coroutineScope: CoroutineScope,
    playerConnection: PlayerConnection?,
    snackbarHostState: SnackbarHostState
) {
    val MAIN_TAG = "MainOtActivity"
    val oobeStatus = context.dataStore.get(OobeStatusKey, defaultValue = 0)
    val localLibEnable = context.dataStore.get(LocalLibraryEnableKey, defaultValue = true)
    val ds = context.dataStore.data.first()[ScannerSensitivityKey]
//        .map { it[SkipSilenceKey] ?: false }
    // auto scanner
    val scannerSensitivity by enumPreference(
        context = context,
        key = ScannerSensitivityKey,
        defaultValue = ScannerMatchCriteria.LEVEL_2
    )
    val scannerImpl by enumPreference(
        context = context,
        key = ScannerImplKey,
        defaultValue = ScannerImpl.TAGLIB
    )
    val scanPaths = context.dataStore.get(ScanPathsKey, defaultValue = "")
    val excludedScanPaths = context.dataStore.get(ExcludedScanPathsKey, defaultValue = "")
    val strictExtensions = context.dataStore.get(ScannerStrictExtKey, defaultValue = false)
    val strictFilePaths = context.dataStore.get(ScannerStrictFilePathsKey, defaultValue = false)
    val autoScan = context.dataStore.get(AutomaticScannerKey, defaultValue = true)
    val lastLocalScan = context.dataStore.get(LastLocalScanKey, 0L)

    // updater
    val updateAvailable = context.dataStore.get(
        UpdateAvailableKey,
        defaultValue = false
    )
    val lastVer = context.dataStore.get(LastVersionKey, defaultValue = "0.0.0")

    if (!autoScan || oobeStatus < OOBE_VERSION) {
        Log.i(MAIN_TAG, "Automatic scan is disabled, and/or user has not passed OOBE")
        return
    }
    val timeNow = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
    if (lastLocalScan + AUTO_SCAN_COOLDOWN > timeNow) {
        Log.i(MAIN_TAG, "Aborting automatic scan. Not enough time has passed since the last scan")
        downloadUtil.resumeDownloadsOnStart()
        return
    }
    Log.i(MAIN_TAG, "Starting local media and downloads auto scan")
    context.dataStore.edit { settings ->
        settings[LastLocalScanKey] =
            timeNow - AUTO_SCAN_COOLDOWN + AUTO_SCAN_SOFT_COOLDOWN // min cooldown to avoid crash loops
    }
    coroutineScope.launch {
        snackbarHostState.showSnackbar(
            message = context.getString(R.string.scanner_auto_start),
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
    }


    // scan download folders
    downloadUtil.scanDownloads()
    downloadUtil.resumeDownloadsOnStart()
    if (!localLibEnable) {
        context.dataStore.edit { settings ->
            settings[LastLocalScanKey] = timeNow
        }
        playerConnection?.service?.initQueue()
        Log.i(MAIN_TAG, "Downloads scan completed. Local media is disabled.")
    }


    // local media scan
    val perms = context.checkSelfPermission(MEDIA_PERMISSION_LEVEL)
    // Check if the permissions for local media access
    if (scannerState.value <= 0 && localLibEnable) {
        if (perms == PackageManager.PERMISSION_GRANTED) {
            // equivalent to (quick scan)
            try {
                withContext(Dispatchers.Main) {
                    playerConnection?.player?.pause()
                }
                val scanner = LocalMediaScanner.getScanner(
                    context, scannerImpl, SCANNER_OWNER_LM
                )
                val uris = scanner.scanLocal(context, scanPaths, excludedScanPaths)
                scanner.quickSync(context, database, uris, scannerSensitivity, strictExtensions, strictFilePaths)
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "${context.getString(R.string.scanner_scan_fail)}: ${e.message}",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
                reportException(e)
            } finally {
                clearDtCache()
                destroyScanner(SCANNER_OWNER_LM)
            }

            // post scan actions
            context.dataStore.edit { settings ->
                settings[LastLocalScanKey] = timeNow
            }
            playerConnection?.service?.initQueue()
            Log.i(MAIN_TAG, "Local media and downloads scan completed")
        } else if (perms == PackageManager.PERMISSION_DENIED) {
            // Check if we should show rationale or if it's "don't ask again"
            if ((context as MainActivity).shouldShowRequestPermissionRationale(MEDIA_PERMISSION_LEVEL)) {
                Log.w(MAIN_TAG, "Permission denied previously, rationale should be shown")
                // Still launch the request, the setContent block in MainActivity should ideally handle the UI
                context.permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
            } else {
                Log.w(MAIN_TAG, "Permission denied (possibly don't ask again). Direct request may fail.")
                context.permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
            }
        }
    } else if (localLibEnable) {
        Log.w(MAIN_TAG, "Cannot perform local media scan, scanner is in use")
    }

    Log.i(MAIN_TAG, "Local media and downloads auto scan complete")
    coroutineScope.launch {
        snackbarHostState.showSnackbar(
            message = context.getString(R.string.scanner_auto_end),
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
    }
}

suspend fun triggerMediaScan(
    context: Context,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    playerConnection: PlayerConnection?,
    snackbarHostState: SnackbarHostState,
    fullRescan: Boolean = false
) {
    val MAIN_TAG = "MainOtActivityScan"
    val localLibEnable = context.dataStore.get(LocalLibraryEnableKey, defaultValue = true)
    if (!localLibEnable) return

    val scannerSensitivity by enumPreference(
        context = context,
        key = ScannerSensitivityKey,
        defaultValue = ScannerMatchCriteria.LEVEL_2
    )
    val scannerImpl by enumPreference(
        context = context,
        key = ScannerImplKey,
        defaultValue = ScannerImpl.TAGLIB
    )
    val scanPaths = context.dataStore.get(ScanPathsKey, defaultValue = "")
    val excludedScanPaths = context.dataStore.get(ExcludedScanPathsKey, defaultValue = "")
    val strictExtensions = context.dataStore.get(ScannerStrictExtKey, defaultValue = false)
    val strictFilePaths = context.dataStore.get(ScannerStrictFilePathsKey, defaultValue = false)

    val perms = context.checkSelfPermission(MEDIA_PERMISSION_LEVEL)
    if (perms != PackageManager.PERMISSION_GRANTED) {
        if ((context as MainActivity).shouldShowRequestPermissionRationale(MEDIA_PERMISSION_LEVEL)) {
             Log.w(MAIN_TAG, "Manual scan: Permission denied previously, showing rationale implicitly")
        }
        (context as MainActivity).permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
        return
    }

    if (scannerState.value > 0) {
        Log.w(MAIN_TAG, "Cannot perform manual scan, scanner is in use")
        return
    }

    Log.i(MAIN_TAG, "Starting manual local media scan (fullRescan=$fullRescan)")
    
    coroutineScope.launch(lmScannerCoroutine) {
        try {
            withContext(Dispatchers.Main) {
                playerConnection?.player?.pause()
            }
            val scanner = getScanner(context, scannerImpl, SCANNER_OWNER_LM)
            
            if (fullRescan) {
                if (scannerImpl == ScannerImpl.MEDIASTORE) {
                    scanner.fullMediaStoreSync(
                        context,
                        database,
                        uriListFromString(scanPaths),
                        uriListFromString(excludedScanPaths),
                        scannerSensitivity,
                        strictExtensions,
                        strictFilePaths,
                        true,
                    )
                } else {
                    val uris = scanner.scanLocal(context, scanPaths, excludedScanPaths)
                    scanner.fullSync(context, database, uris, scannerSensitivity, strictExtensions, strictFilePaths)
                }
            } else {
                if (scannerImpl == ScannerImpl.MEDIASTORE) {
                    scanner.fullMediaStoreSync(
                        context,
                        database,
                        uriListFromString(scanPaths),
                        uriListFromString(excludedScanPaths),
                        scannerSensitivity,
                        strictExtensions,
                        strictFilePaths,
                        false,
                    )
                } else {
                    val uris = scanner.scanLocal(context, scanPaths, excludedScanPaths)
                    scanner.quickSync(context, database, uris, scannerSensitivity, strictExtensions, strictFilePaths)
                }
            }
            
            delay(1000)
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.scanner_progress_complete),
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            }
        } catch (e: Exception) {
            Log.e(MAIN_TAG, "Scanner failed", e)
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar(
                    message = "${context.getString(R.string.scanner_scan_fail)}: ${e.message}",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            }
            reportException(e)
        } finally {
            clearDtCache()
            destroyScanner(SCANNER_OWNER_LM)
            val timeNow = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
            context.dataStore.edit { settings ->
                settings[LastLocalScanKey] = timeNow
            }
        }
    }
}

