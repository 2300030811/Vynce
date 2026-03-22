/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vynce.app.ui.screens.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.GraphicEq
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
import com.vynce.app.constants.LastFmSessionKey
import com.vynce.app.constants.LastFmScrobblingEnabledKey
import com.vynce.app.constants.LastFmUsernameKey
import com.vynce.app.constants.TopBarInsets
import com.vynce.app.ui.component.ColumnWithContentPadding
import com.vynce.app.utils.rememberPreference
import com.vynce.app.ui.component.PreferenceEntry
import com.vynce.app.ui.component.PreferenceGroupTitle
import com.vynce.app.ui.component.SwitchPreference
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.screens.settings.fragments.AccountExtrasFrag
import com.vynce.app.ui.screens.settings.fragments.AccountFrag
import com.vynce.app.ui.screens.settings.fragments.SyncAutoFrag
import com.vynce.app.ui.screens.settings.fragments.SyncExtrasFrag
import com.vynce.app.ui.screens.settings.fragments.SyncManualFrag
import com.vynce.app.ui.screens.settings.fragments.SyncParamsFrag
import com.vynce.app.ui.utils.backToMain

@SuppressLint("PrivateResource")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSyncSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val (lastFmEnabled, onLastFmEnabledChange) = rememberPreference(
            key = LastFmScrobblingEnabledKey,
            defaultValue = false
        )
        val (lastFmSessionKey, onLastFmSessionKeyChange) = rememberPreference(
            key = LastFmSessionKey,
            defaultValue = ""
        )
        val (lastFmUsername, onLastFmUsernameChange) = rememberPreference(
            key = LastFmUsernameKey,
            defaultValue = ""
        )

        PreferenceGroupTitle(
            title = "Last.fm"
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = { Text("Scrobbling") },
                description = "Send listening data to Last.fm",
                icon = { Icon(Icons.Rounded.GraphicEq, null) },
                checked = lastFmEnabled,
                onCheckedChange = onLastFmEnabledChange
            )
            if (lastFmEnabled) {
                PreferenceEntry(
                    title = { Text("Connect Last.fm account") },
                    description = if (lastFmSessionKey.isNotBlank() || lastFmUsername.isNotBlank()) "Connected as ${lastFmUsername.ifBlank { "Unknown" }}" else "Not connected",
                    icon = { Icon(Icons.Rounded.AccountCircle, null) },
                    onClick = {
                        // TODO: implement Last.fm OAuth login flow
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

    }

    TopAppBar(
        title = { Text(stringResource(R.string.grp_account_sync)) },
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


