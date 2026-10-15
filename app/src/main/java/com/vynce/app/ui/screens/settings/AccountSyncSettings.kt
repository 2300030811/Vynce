package com.vynce.app.ui.screens.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vynce.app.R
import com.vynce.app.constants.LastFmScrobblingEnabledKey
import com.vynce.app.constants.LastFmSessionKey
import com.vynce.app.constants.LastFmUsernameKey
import com.vynce.app.constants.TopBarInsets
import com.vynce.app.ui.component.ColumnWithContentPadding
import com.vynce.app.ui.component.PreferenceEntry
import com.vynce.app.ui.component.PreferenceGroupTitle
import com.vynce.app.ui.component.SwitchPreference
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.dialog.DefaultDialog
import com.vynce.app.ui.utils.backToMain
import com.vynce.app.utils.rememberPreference

@SuppressLint("PrivateResource")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSyncSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current

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

    var showAccountDialog by remember { mutableStateOf(false) }

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = "Last.fm"
        )

        val isConnected = lastFmSessionKey.isNotBlank() || lastFmUsername.isNotBlank()

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
                    title = { Text("Last.fm Account") },
                    description = if (isConnected) "Connected as ${lastFmUsername.ifBlank { "Active User" }}" else "Tap to connect account or session key",
                    icon = { Icon(Icons.Rounded.AccountCircle, null) },
                    onClick = { showAccountDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showAccountDialog) {
        var inputUsername by remember { mutableStateOf(lastFmUsername) }
        var inputSessionKey by remember { mutableStateOf(lastFmSessionKey) }

        DefaultDialog(
            onDismiss = { showAccountDialog = false },
            icon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
            title = { Text("Last.fm Account", fontWeight = FontWeight.Bold) },
            buttons = {
                if (lastFmSessionKey.isNotBlank() || lastFmUsername.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onLastFmUsernameChange("")
                            onLastFmSessionKeyChange("")
                            showAccountDialog = false
                        }
                    ) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        onLastFmUsernameChange(inputUsername.trim())
                        onLastFmSessionKeyChange(inputSessionKey.trim())
                        showAccountDialog = false
                    }
                ) {
                    Text("Save")
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter your Last.fm username and 32-character session key to authenticate your scrobbles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputUsername,
                    onValueChange = { inputUsername = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = inputSessionKey,
                    onValueChange = { inputSessionKey = it },
                    label = { Text("Session Key / Token") },
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        try {
                            uriHandler.openUri("https://www.last.fm/api/account/create")
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Get Last.fm API Key", style = MaterialTheme.typography.bodySmall)
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    }
                }
            }
        }
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


