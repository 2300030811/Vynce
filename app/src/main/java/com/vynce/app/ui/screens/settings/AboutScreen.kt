/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.ui.screens.settings

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vynce.app.BuildConfig
import com.vynce.app.R
import com.vynce.app.constants.ENABLE_FFMETADATAEX
import com.vynce.app.constants.LYRIC_FETCH_TIMEOUT
import com.vynce.app.constants.MAX_LM_SCANNER_JOBS
import com.vynce.app.constants.SNACKBAR_VERY_SHORT
import com.vynce.app.constants.TopBarInsets
import com.vynce.app.ui.component.ColumnWithContentPadding
import com.vynce.app.ui.component.ContributorCard
import com.vynce.app.ui.component.ContributorInfo
import com.vynce.app.ui.component.ContributorType.CUSTOM
import com.vynce.app.ui.component.PreferenceEntry
import com.vynce.app.ui.component.SettingsClickToReveal
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.component.button.IconLabelButton
import com.vynce.app.ui.dialog.UpdateDialog
import com.vynce.app.ui.utils.backToMain
import com.vynce.app.utils.AppUpdateChecker
import com.vynce.app.utils.scanners.FFmpegScanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val appName = stringResource(R.string.app_name)

    val showDebugInfo = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "userdebug"

    var checkingForUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateChecker.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.vynce_logo),
            contentDescription = null,
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .clickable { }
        )

        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "Vynce",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.width(4.dp))

            if (showDebugInfo) {
                Spacer(Modifier.width(4.dp))

                Text(
                    text = BuildConfig.BUILD_TYPE.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 2.dp
                        )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            IconLabelButton(
                text = "GitHub",
                painter = painterResource(R.drawable.github),
                onClick = { uriHandler.openUri("https://github.com/2300030811/Vynce") },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            IconLabelButton(
                text = stringResource(R.string.wiki),
                icon = Icons.Outlined.Info,
                onClick = { uriHandler.openUri("https://github.com/2300030811/Vynce/wiki") },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                PreferenceEntry(
                    title = { Text("Check for updates") },
                    description = if (checkingForUpdates) "Checking GitHub for latest release…" else "Installed: v${BuildConfig.VERSION_NAME}",
                    icon = {
                        if (checkingForUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.SystemUpdate, null)
                        }
                    },
                    onClick = {
                        if (!checkingForUpdates) {
                            checkingForUpdates = true
                            scope.launch {
                                try {
                                    val info = AppUpdateChecker.checkForUpdate()
                                    if (info.isUpdateAvailable) {
                                        updateInfo = info
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, "Vynce is up to date (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to check for updates: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    checkingForUpdates = false
                                }
                            }
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text(stringResource(R.string.attribution_title)) },
                    onClick = {
                        navController.navigate("settings/about/attribution")
                    }
                )
                PreferenceEntry(
                    title = { Text(stringResource(R.string.oss_licenses_title)) },
                    onClick = {
                        navController.navigate("settings/about/oss_licenses")
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.help_bug_report_action)) },
                    onClick = {
                        uriHandler.openUri("https://github.com/2300030811/Vynce/issues")
                    }
                )
                PreferenceEntry(
                    title = { Text(stringResource(R.string.help_support_forum)) },
                    onClick = {
                        uriHandler.openUri("https://github.com/2300030811/Vynce/discussions")
                    }
                )
                PreferenceEntry(
                    title = { Text(stringResource(R.string.help_contact_email_inquiries)) },
                    onClick = {
                        val clipData = ClipData.newPlainText(
                            appName,
                            AnnotatedString("Vynce@protonmail.com")
                        )
                        clipboardManager.nativeClipboard.setPrimaryClip(clipData)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsClickToReveal(stringResource(R.string.app_info_title)) {
                    val info = mutableListOf<String>(
                        "FFMetadataEx: $ENABLE_FFMETADATAEX",
                        "LM scanner concurrency: $MAX_LM_SCANNER_JOBS",
                        "LYRIC_FETCH_TIMEOUT: $LYRIC_FETCH_TIMEOUT",
                        "SNACKBAR_VERY_SHORT: $SNACKBAR_VERY_SHORT"
                    )
                    if (ENABLE_FFMETADATAEX) {
                        info.add("FFMetadataEx version: ${FFmpegScanner.VERSION_STRING}")
                        info.add("FFmpeg unavailable (native module not available)")
                    }

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        info.forEach {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                SettingsClickToReveal(stringResource(R.string.device_info_title)) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val info = mutableListOf<String>(
                            "Device: ${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})",
                            "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                            "Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}",
                            "Build ID: ${Build.ID}"
                        )

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            info.forEach {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            ContributorCard(
                contributor = ContributorInfo(
                    name = "Bhima",
                    type = listOf(CUSTOM),
                    description = "Maintainer and lead developer of Vynce Music Player.",
                    url = "https://github.com/2300030811"
                )
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false }
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}
