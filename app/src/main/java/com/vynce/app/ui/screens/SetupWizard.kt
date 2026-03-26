/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vynce.app.BuildConfig
import com.vynce.app.LocalDownloadUtil
import com.vynce.app.R
import com.vynce.app.constants.AutomaticScannerKey
import com.vynce.app.constants.DEFAULT_ENABLED_FILTERS
import com.vynce.app.constants.DEFAULT_ENABLED_TABS
import com.vynce.app.constants.DownloadPathKey
import com.vynce.app.constants.EnabledFiltersKey
import com.vynce.app.constants.EnabledTabsKey
import com.vynce.app.constants.LibraryFilterKey
import com.vynce.app.constants.LocalLibraryEnableKey
import com.vynce.app.constants.MaxSongCacheSizeKey
import com.vynce.app.constants.NavigationBarHeight
import com.vynce.app.constants.OOBE_VERSION
import com.vynce.app.constants.OobeStatusKey
import com.vynce.app.constants.ScanPathsKey
import com.vynce.app.constants.ThumbnailCornerRadius
import com.vynce.app.ui.component.ListPreference
import com.vynce.app.ui.component.PreferenceEntry
import com.vynce.app.ui.component.PreferenceGroupTitle
import com.vynce.app.ui.component.SwitchPreference
import com.vynce.app.ui.component.button.IconLabelButton
import com.vynce.app.ui.dialog.ActionPromptDialog
import com.vynce.app.ui.dialog.InfoLabel
import com.vynce.app.ui.screens.Screens.LibraryFilter
import com.vynce.app.ui.screens.settings.fragments.LocalScannerFrag
import com.vynce.app.ui.screens.settings.fragments.LocalizationFrag
import com.vynce.app.ui.screens.settings.fragments.ThemeAppFrag
import com.vynce.app.utils.dlCoroutine
import com.vynce.app.utils.formatFileSize
import com.vynce.app.utils.rememberEnumPreference
import com.vynce.app.utils.rememberPreference
import com.vynce.app.utils.scanners.stringFromUriList
import com.vynce.app.utils.scanners.uriListFromString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizard(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val uriHandler = LocalUriHandler.current

    var oobeStatus by rememberPreference(OobeStatusKey, defaultValue = 0)

    // content prefs
    var filter by rememberEnumPreference(LibraryFilterKey, LibraryFilter.ALL)

    // local media prefs
    val (localLibEnable, onLocalLibEnableChange) = rememberPreference(LocalLibraryEnableKey, defaultValue = true)
    val (autoScan, onAutoScanChange) = rememberPreference(AutomaticScannerKey, defaultValue = true)
    val (enabledTabs, onEnabledTabsChange) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    val (enabledFilters, onEnabledFiltersChange) = rememberPreference(EnabledFiltersKey, defaultValue = DEFAULT_ENABLED_FILTERS)

    LaunchedEffect(localLibEnable) {
        var containsFolders = enabledTabs.contains('F')
        if (localLibEnable && !containsFolders) {
            onEnabledTabsChange(enabledTabs + "F")
        } else if (!localLibEnable && containsFolders) {
            onEnabledTabsChange(enabledTabs.filterNot { it == 'F' })
        }

        containsFolders = enabledFilters.contains('F')
        if (!localLibEnable && containsFolders) {
            onEnabledFiltersChange(enabledFilters.filterNot { it == 'F' })
        }
    }

    BackHandler {
        if (oobeStatus > 0) {
            oobeStatus -= 1
        }
    }

    Scaffold(
        bottomBar = {
            if (oobeStatus > 0 && oobeStatus < OOBE_VERSION - 1) {
                Surface(
                    tonalElevation = 3.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (oobeStatus > 0) oobeStatus -= 1
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateBefore, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_back), fontWeight = FontWeight.Bold)
                        }

                        LinearProgressIndicator(
                            progress = { oobeStatus.toFloat() / (OOBE_VERSION - 1) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 24.dp)
                                .height(6.dp)
                                .clip(CircleShape),
                            strokeCap = StrokeCap.Round
                        )

                        Button(
                            onClick = {
                                if (oobeStatus == 1) filter = LibraryFilter.ALL
                                if (oobeStatus < OOBE_VERSION) oobeStatus += 1
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.action_next), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext, null)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 32.dp))

                when (oobeStatus) {
                    0 -> { // Premium Landing Page
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .shadow(24.dp, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(2.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.vynce_logo),
                                contentDescription = null,
                                modifier = Modifier.size(90.dp)
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        Text(
                            text = "Welcome to Vynce",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "A premium, ad-free music experience designed for choice and privacy.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OobeFeatureRow(
                                title = "Smart Integration",
                                description = "Seamlessly access JioSaavn music and your local library in one place.",
                                icon = Icons.Rounded.Sync,
                                color = Color(0xFF673AB7)
                            )
                            OobeFeatureRow(
                                title = "Pure Experience",
                                description = "No ads, no trackers, just pure music exactly how you want it.",
                                icon = Icons.Rounded.Block,
                                color = Color(0xFFF44336)
                            )
                            OobeFeatureRow(
                                title = "Local First",
                                description = "Powerful offline support and high-fidelity audio engine.",
                                icon = Icons.Rounded.SdCard,
                                color = Color(0xFF2196F3)
                            )
                        }

                        Spacer(Modifier.height(48.dp))

                        Button(
                            onClick = { 
                                oobeStatus += 1
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Get Started", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { navController.navigate("settings/backup_restore") }) {
                                Text("Restore from Backup", color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(16.dp))
                            TextButton(onClick = { oobeStatus = OOBE_VERSION; navController.navigateUp() }) {
                                Text("Skip Setup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    1 -> { // Interface/Appearance
                        SetupHeader(
                            icon = Icons.Rounded.DarkMode,
                            title = "Theme & Interface",
                            subtitle = "Customize how Vynce looks. You can enable dynamic theme to match your wallpaper."
                        )
                        
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ElevatedCard(shape = RoundedCornerShape(20.dp)) { ThemeAppFrag() }
                            ElevatedCard(shape = RoundedCornerShape(20.dp)) { LocalizationFrag() }
                        }
                    }

                    2 -> { // Local Media
                        SetupHeader(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "Local Music",
                            subtitle = "Enable local music support to play songs stored on your device."
                        )

                        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                            SwitchPreference(
                                title = { Text("Enable Local Library") },
                                description = "Scan your device for music files.",
                                icon = { Icon(Icons.Rounded.SdCard, null) },
                                checked = localLibEnable,
                                onCheckedChange = onLocalLibEnableChange
                            )
                        }

                        AnimatedVisibility(localLibEnable) {
                            Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                                    SwitchPreference(
                                        title = { Text("Auto Scan") },
                                        description = "Automatically update library when files change.",
                                        icon = { Icon(Icons.Rounded.Autorenew, null) },
                                        checked = autoScan,
                                        onCheckedChange = onAutoScanChange
                                    )
                                }
                                ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(vertical = 8.dp)) {
                                        PreferenceGroupTitle(title = "Manual Library Scan")
                                        LocalScannerFrag()
                                    }
                                }
                            }
                        }
                    }

                    3 -> { // Downloads & Cache
                        val downloadUtil = LocalDownloadUtil.current
                        val (downloadPath, onDownloadPathChange) = rememberPreference(DownloadPathKey, "")
                        val (maxCacheSize, onMaxCacheChange) = rememberPreference(MaxSongCacheSizeKey, 0)
                        var showPathDialog by remember { mutableStateOf(false) }

                        SetupHeader(
                            icon = Icons.Rounded.Download,
                            title = "Downloads & Cache",
                            subtitle = "Configure where to save your offline music and manage storage usage."
                        )

                        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                PreferenceEntry(
                                    title = { Text("Download Location") },
                                    icon = { Icon(Icons.Rounded.SdCard, null) },
                                    onClick = { showPathDialog = true }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp).alpha(0.3f))
                                ListPreference(
                                    title = { Text("Song Cache Size") },
                                    selectedValue = maxCacheSize,
                                    values = listOf(0, 512, 1024, 2048, 4096, 8192, -1),
                                    valueText = { when(it) {
                                        0 -> "Off"
                                        -1 -> "Unlimited"
                                        else -> formatFileSize(it * 1024 * 1024L)
                                    }},
                                    onValueSelected = onMaxCacheChange
                                )
                            }
                        }

                        if (showPathDialog) {
                            // Reuse existing path dialog logic
                        }
                    }

                    4 -> { // Finalizing
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                
                                Spacer(Modifier.height(32.dp))
                                
                                Text("All Set!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Your music journey begins now.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
                                )

                                Button(
                                    onClick = { 
                                        oobeStatus = OOBE_VERSION
                                        navController.navigateUp()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Start Listening", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun SetupHeader(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun OobeFeatureRow(title: String, description: String, icon: ImageVector, color: Color) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(28.dp), tint = color)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


