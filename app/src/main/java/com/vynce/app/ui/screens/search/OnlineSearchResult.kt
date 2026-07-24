package com.vynce.app.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.ui.component.items.SaavnSongRow
import com.vynce.app.utils.playJioSaavnSong
import com.vynce.app.viewmodels.OnlineSearchViewModel

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val results by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Simple Column layout instead of nested Scaffold to avoid double-header stacking
    // with the parent NavHost's scrollBehavior.
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Lightweight header row with back button and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = androidx.compose.ui.res.stringResource(com.vynce.app.R.string.back)
                )
            }
            Text(
                text = "Search results for \"${viewModel.query}\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
        ) {
            if (isLoading && results.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            items(results) { song ->
                SaavnSongRow(
                    song = song,
                    navController = navController,
                    onClick = {
                        playJioSaavnSong(song, playerConnection)
                    }
                )
            }

            item {
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}
