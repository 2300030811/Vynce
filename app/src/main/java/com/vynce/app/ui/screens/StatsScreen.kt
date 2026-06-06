package com.vynce.app.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.vynce.app.LocalMenuState
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.LocalPlayerConnection
import com.vynce.app.R
import com.vynce.app.constants.ListThumbnailSize
import com.vynce.app.constants.StatPeriod
import com.vynce.app.constants.SwipeToQueueKey
import com.vynce.app.constants.TopBarInsets
import com.vynce.app.models.toMediaMetadata
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.component.ChipsRow
import com.vynce.app.ui.component.LazyColumnScrollbar
import com.vynce.app.ui.component.NavigationTitle
import com.vynce.app.ui.component.button.IconButton
import com.vynce.app.ui.component.items.AlbumGridItem
import com.vynce.app.ui.component.items.ArtistGridItem
import com.vynce.app.ui.component.items.SongListItem
import com.vynce.app.ui.menu.AlbumMenu
import com.vynce.app.ui.menu.ArtistMenu
import com.vynce.app.ui.utils.backToMain
import com.vynce.app.utils.rememberPreference
import com.vynce.app.viewmodels.StatsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val density = LocalDensity.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val statPeriod by viewModel.statPeriod.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val lostMemories by viewModel.lostMemories.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val mostPlayedSongTitle = stringResource(R.string.most_played_songs)
    val lostMemoriesTitle = stringResource(R.string.lost_memories)

    // Compute thumbnail size in composable scope (not in LazyListScope)
    val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .asPaddingValues(),
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
    ) {
        item {
            ChipsRow(
                chips = listOf(
                    StatPeriod.`1_WEEK` to pluralStringResource(R.plurals.n_week, 1, 1),
                    StatPeriod.`1_MONTH` to pluralStringResource(R.plurals.n_month, 1, 1),
                    StatPeriod.`3_MONTH` to pluralStringResource(R.plurals.n_month, 3, 3),
                    StatPeriod.`6_MONTH` to pluralStringResource(R.plurals.n_month, 6, 6),
                    StatPeriod.`1_YEAR` to pluralStringResource(R.plurals.n_year, 1, 1),
                    StatPeriod.ALL to stringResource(R.string.filter_all)
                ),
                currentValue = statPeriod,
                onValueUpdate = { viewModel.statPeriod.value = it }
            )
        }

        if (lostMemories.isNotEmpty()) {
            item(key = "lostMemoriesTitle") {
                NavigationTitle(
                    title = stringResource(R.string.lost_memories),
                    modifier = Modifier.animateItem()
                )
                Text(
                    text = stringResource(R.string.on_this_day),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 8.dp)
                        .animateItem()
                )
            }

            items(
                items = lostMemories,
                key = { "lost_${it.id}" }
            ) { song ->
                SongListItem(
                    song = song,
                    navController = navController,
                    isActive = song.song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    inSelectMode = false,
                    isSelected = false,
                    onSelectedChange = {},
                    swipeEnabled = swipeEnabled,
                    thumbnailSize = thumbnailSize,
                    onPlay = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = lostMemoriesTitle,
                                items = lostMemories.map { it.toMediaMetadata() }
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                )
            }
        }

        if (mostPlayedSongs.isNotEmpty()) {
            item(key = "mostPlayedSongs") {
                NavigationTitle(
                    title = stringResource(R.string.most_played_songs),
                    modifier = Modifier.animateItem()
                )
            }

        items(
            items = mostPlayedSongs,
            key = { it.id }
        ) { song ->
            SongListItem(
                song = song,
                navController = navController,

                isActive = song.song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                inSelectMode = false,
                isSelected = false,
                onSelectedChange = {},
                swipeEnabled = swipeEnabled,

                thumbnailSize = thumbnailSize,
                onPlay = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = mostPlayedSongTitle,
                            items = mostPlayedSongs.map { it.toMediaMetadata() }
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
            )
        }
        } // End of mostPlayedSongs.isNotEmpty() condition

        if (mostPlayedArtists.isNotEmpty()) {
            item(key = "mostPlayedArtists") {
                NavigationTitle(
                    title = stringResource(R.string.most_played_artists),
                    modifier = Modifier.animateItem()
                )

                LazyRow(
                    modifier = Modifier.animateItem()
                ) {
                items(
                    items = mostPlayedArtists,
                    key = { it.artist.id }
                ) { artist ->
                    ArtistGridItem(
                        artist = artist.artist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    navController.navigate("artist/${artist.artist.id}")
                                },
                                onLongClick = {
                                    menuState.show {
                                        ArtistMenu(
                                            originalArtist = artist.artist,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                            .animateItem()
                    )
                }
            }
            } // End of mostPlayedArtists.isNotEmpty() condition
        }

        if (mostPlayedAlbums.isNotEmpty()) {
            item(key = "mostPlayedAlbums") {
                NavigationTitle(
                    title = stringResource(R.string.most_played_albums),
                    modifier = Modifier.animateItem()
                )

                LazyRow(
                    modifier = Modifier.animateItem()
                ) {
                    items(
                        items = mostPlayedAlbums,
                        key = { it.id }
                    ) { album ->
                        AlbumGridItem(
                            album = album,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            coroutineScope = coroutineScope,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("album/${album.id}")
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            AlbumMenu(
                                                originalAlbum = album,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }
            }
        }
    }
    LazyColumnScrollbar(
        state = lazyListState,
    )

    TopAppBar(
        title = { Text(stringResource(R.string.stats)) },
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
    )
}

