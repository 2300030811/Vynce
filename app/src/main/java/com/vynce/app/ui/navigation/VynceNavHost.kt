package com.vynce.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vynce.app.constants.SongFilter
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.ui.screens.PlayerScreen
import com.vynce.app.ui.screens.Screens
import com.vynce.app.ui.screens.StatsScreen
import com.vynce.app.ui.screens.home.HomeScreen
import com.vynce.app.ui.screens.library.FolderScreen
import com.vynce.app.ui.screens.library.LibraryAlbumsScreen
import com.vynce.app.ui.screens.library.LibraryArtistsScreen
import com.vynce.app.ui.screens.library.LibraryFoldersScreen
import com.vynce.app.ui.screens.library.LibraryPlaylistsScreen
import com.vynce.app.ui.screens.library.LibraryScreen
import com.vynce.app.ui.screens.library.LibrarySongsScreen
import com.vynce.app.ui.screens.playlist.AutoPlaylistScreen
import com.vynce.app.ui.screens.playlist.LocalPlaylistScreen
import com.vynce.app.ui.screens.saavn.AlbumScreen
import com.vynce.app.ui.screens.saavn.ArtistScreen
import com.vynce.app.ui.screens.saavn.PlaylistScreen
import com.vynce.app.ui.screens.search.OnlineSearchResult
import com.vynce.app.ui.screens.search.SearchBarContainer
import com.vynce.app.ui.screens.settings.*
import com.vynce.app.ui.screens.settings.AboutScreen
import com.vynce.app.ui.screens.settings.AttributionScreen
import com.vynce.app.ui.screens.SetupWizard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VynceNavHost(
    navController: NavHostController,
    defaultOpenTab: String,
    navigationItems: List<Screens>,
    scrollBehavior: TopAppBarScrollBehavior,
    playerConnection: PlayerConnection?,
    getNavPadding: @Composable () -> Dp
) {
    NavHost(
        navController = navController,
        startDestination = (Screens.getAllScreens()
            .find { it.route == defaultOpenTab })?.route
            ?: Screens.Home.route,
        enterTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }
            val previousRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }

            if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                slideInHorizontally { it / 8 } + fadeIn(tween(200))
            else
                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
        },
        exitTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }
            val targetRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }

            if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                slideOutHorizontally { -it / 8 } + fadeOut(tween(100))
            else
                slideOutHorizontally { it / 8 } + fadeOut(tween(100))
        },
        popEnterTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }
            val previousRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }

            if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                slideInHorizontally { it / 8 } + fadeIn(tween(200))
            else
                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
        },
        popExitTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }
            val targetRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }

            if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                slideOutHorizontally { -it / 8 } + fadeOut(tween(100))
            else
                slideOutHorizontally { it / 8 } + fadeOut(tween(100))
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    )
    {
        composable(Screens.Home.route) {
            HomeScreen(navController, playerConnection)
        }
        composable(Screens.Songs.route) {
            LibrarySongsScreen(navController)
        }
        composable(Screens.History.route) {
            LibrarySongsScreen(navController, initialFilter = SongFilter.HISTORY)
        }
        composable(Screens.Liked.route) {
            LibrarySongsScreen(navController, initialFilter = SongFilter.LIKED)
        }
        composable(Screens.Folders.route) {
            LibraryFoldersScreen(navController, scrollBehavior)
        }
        composable(
            route = "${Screens.Folders.route}/{path}",
            arguments = listOf(
                navArgument("path") {
                    type = NavType.StringType
                }
            )
        ) {
            FolderScreen(navController, scrollBehavior)
        }
        composable(Screens.Artists.route) {
            LibraryArtistsScreen(navController)
        }
        composable(Screens.Albums.route) {
            LibraryAlbumsScreen(navController)
        }
        composable(Screens.Playlists.route) {
            LibraryPlaylistsScreen(navController)
        }
        composable(Screens.Library.route) {
            LibraryScreen(navController, scrollBehavior)
        }
        composable(Screens.Player.route) {
            PlayerScreen(navController, bottomPadding = getNavPadding())
        }
        composable(Screens.Stats.route) {
            StatsScreen(navController)
        }

        composable(
            route = "search",
        ) {
            SearchBarContainer(navController, scrollBehavior)
        }
        composable(
            route = "search/{query}",
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                }
            )
        ) {
            OnlineSearchResult(navController)
        }
        composable(
            route = "local_playlist/{playlistId}",
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                }
            )
        ) {
            LocalPlaylistScreen(navController, scrollBehavior)
        }
        composable(
            route = "auto_playlist/{playlistId}",
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                }
            )
        ) {
            AutoPlaylistScreen(navController, scrollBehavior)
        }
        composable("settings") {
            SettingsScreen(navController, scrollBehavior)
        }
        composable("settings/appearance") {
            AppearanceSettings(navController, scrollBehavior)
        }
        composable("settings/interface") {
            InterfaceSettings(navController, scrollBehavior)
        }
        composable("settings/library") {
            LibrarySettings(navController, scrollBehavior)
        }
        composable("settings/library/lyrics") {
            LyricsSettings(navController, scrollBehavior)
        }
        composable("settings/account_sync") {
            AccountSyncSettings(navController, scrollBehavior)
        }
        composable("settings/player") {
            PlayerSettings(navController, scrollBehavior)
        }
        composable("settings/storage") {
            StorageSettings(navController, scrollBehavior)
        }
        composable("settings/backup_restore") {
            BackupAndRestore(navController, scrollBehavior)
        }
        composable("settings/local") {
            LocalPlayerSettings(navController, scrollBehavior)
        }
        composable("settings/experimental") {
            ExperimentalSettings(navController, scrollBehavior)
        }
        composable("settings/about") {
            AboutScreen(navController, scrollBehavior)
        }
        composable("settings/about/attribution") {
            AttributionScreen(navController, scrollBehavior)
        }
        composable("settings/about/oss_licenses") {
            LibrariesScreen(navController, scrollBehavior)
        }

        composable("setup_wizard") {
            SetupWizard(navController)
        }

        composable("album/{albumId}") { backStack ->
            val albumId = backStack.arguments?.getString("albumId") ?: return@composable
            AlbumScreen(albumId = albumId, navController = navController, playerConnection = playerConnection)
        }
        composable("artist/{artistId}") { backStack ->
            val artistId = backStack.arguments?.getString("artistId") ?: return@composable
            ArtistScreen(artistId = artistId, navController = navController, playerConnection = playerConnection)
        }
        composable("playlist/{playlistId}") { backStack ->
            val playlistId = backStack.arguments?.getString("playlistId") ?: return@composable
            PlaylistScreen(playlistId = playlistId, navController = navController, playerConnection = playerConnection)
        }
    }
}
