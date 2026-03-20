/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.vynce.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vynce.app.R
import com.vynce.app.constants.FlatSubfoldersKey
import com.vynce.app.constants.ProxyEnabledKey
import com.vynce.app.constants.ProxyTypeKey
import com.vynce.app.constants.ProxyUrlKey
import com.vynce.app.constants.ShowLikedAndDownloadedPlaylist
import com.vynce.app.constants.TopBarInsets
import com.vynce.app.ui.component.ColumnWithContentPadding
import com.vynce.app.ui.component.EditTextPreference
import com.vynce.app.ui.component.ListPreference
import com.vynce.app.ui.component.PreferenceEntry
import com.vynce.app.ui.component.PreferenceGroupTitle
import com.vynce.app.ui.component.SettingsClickToReveal
import com.vynce.app.ui.component.SwitchPreference
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.screens.settings.fragments.ListenHistoryFrag
import com.vynce.app.ui.screens.settings.fragments.LocalizationFrag
import com.vynce.app.ui.screens.settings.fragments.SearchHistoryFrag
import com.vynce.app.ui.utils.backToMain
import com.vynce.app.utils.rememberEnumPreference
import com.vynce.app.utils.rememberPreference
import java.net.Proxy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (showLikedAndDownloadedPlaylist, onShowLikedAndDownloadedPlaylistChange) = rememberPreference(
        key = ShowLikedAndDownloadedPlaylist,
        defaultValue = true
    )
    val (flatSubfolders, onFlatSubfoldersChange) = rememberPreference(FlatSubfoldersKey, defaultValue = true)

    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")


    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.content)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.local_player_settings_title)) },
                icon = { Icon(Icons.Rounded.SdCard, null) },
                onClick = { navController.navigate("settings/local") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.lyrics_settings_title)) },
                icon = { Icon(Icons.Rounded.Lyrics, null) },
                onClick = { navController.navigate("settings/library/lyrics") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.storage)) },
                icon = { Icon(Icons.Rounded.Storage, null) },
                onClick = { navController.navigate("settings/storage") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.grp_localization)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            LocalizationFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        PreferenceGroupTitle(
            title = stringResource(R.string.privacy)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListenHistoryFrag()
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SearchHistoryFrag()
        }

        SettingsClickToReveal(stringResource(R.string.advanced)) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.show_liked_and_downloaded_playlist)) },
                    icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                    checked = showLikedAndDownloadedPlaylist,
                    onCheckedChange = onShowLikedAndDownloadedPlaylistChange
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.flat_subfolders_title)) },
                    description = stringResource(R.string.flat_subfolders_description),
                    icon = { Icon(Icons.Rounded.FolderCopy, null) },
                    checked = flatSubfolders,
                    onCheckedChange = onFlatSubfoldersChange
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_proxy)) },
                    checked = proxyEnabled,
                    onCheckedChange = onProxyEnabledChange
                )

                AnimatedVisibility(proxyEnabled) {
                    Column {
                        ListPreference(
                            title = { Text(stringResource(R.string.proxy_type)) },
                            selectedValue = proxyType,
                            values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                            valueText = { it.name },
                            onValueSelected = onProxyTypeChange
                        )
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_url)) },
                            value = proxyUrl,
                            onValueChange = onProxyUrlChange
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }


    TopAppBar(
        title = { Text(stringResource(R.string.grp_library_and_content)) },
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

