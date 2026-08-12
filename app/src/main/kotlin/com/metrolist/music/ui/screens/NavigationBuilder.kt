/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.metrolist.music.ui.navigation.nestedScreenEnter
import com.metrolist.music.ui.navigation.nestedScreenExit
import com.metrolist.music.ui.navigation.nestedScreenPopEnter
import com.metrolist.music.ui.navigation.nestedScreenPopExit
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.ui.screens.artist.ArtistAlbumsScreen
import com.metrolist.music.ui.screens.artist.ArtistItemsScreen
import com.metrolist.music.ui.screens.artist.ArtistScreen
import com.metrolist.music.ui.screens.artist.ArtistSongsScreen
import com.metrolist.music.ui.screens.equalizer.EqScreen
import com.metrolist.music.ui.screens.equalizer.wizard.WizardScreen
import com.metrolist.music.ui.screens.library.LibraryScreen
import com.metrolist.music.ui.screens.playlist.AutoPlaylistScreen
import com.metrolist.music.ui.screens.playlist.CachePlaylistScreen
import com.metrolist.music.ui.screens.playlist.LocalPlaylistScreen
import com.metrolist.music.ui.screens.playlist.OnlinePlaylistScreen
import com.metrolist.music.ui.screens.playlist.TopPlaylistScreen
import com.metrolist.music.ui.screens.podcast.OnlinePodcastScreen
import com.metrolist.music.ui.screens.recognition.RecognitionHistoryScreen
import com.metrolist.music.ui.screens.recognition.RecognitionScreen
import com.metrolist.music.ui.screens.search.OnlineSearchResult
import com.metrolist.music.ui.screens.search.SearchScreen
import com.metrolist.music.ui.screens.settings.AiSettings
import com.metrolist.music.ui.screens.settings.AndroidAutoSettings
import com.metrolist.music.ui.screens.settings.AppearanceSettings
import com.metrolist.music.ui.screens.settings.BackupAndRestore
import com.metrolist.music.ui.screens.settings.ContentSettings
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.screens.settings.DjSettings
import com.metrolist.music.ui.screens.settings.PlayerSettings
import com.metrolist.music.ui.screens.settings.PrivacySettings
import com.metrolist.music.ui.screens.settings.RomanizationSettings
import com.metrolist.music.ui.screens.settings.SettingsScreen
import com.metrolist.music.ui.screens.settings.StorageSettings
import com.metrolist.music.ui.screens.settings.StreamSourcesSettings
import com.metrolist.music.ui.screens.settings.ThemeScreen
import com.metrolist.music.ui.screens.settings.UpdaterScreen
import com.metrolist.music.ui.screens.settings.integrations.DiscordSettings
import com.metrolist.music.ui.screens.settings.integrations.IntegrationScreen
import com.metrolist.music.ui.screens.settings.integrations.LastFMSettings
import com.metrolist.music.ui.screens.settings.integrations.ListenTogetherSettings
import com.metrolist.music.ui.screens.settings.integrations.SpotifySettings
import com.metrolist.music.ui.screens.settings.integrations.SpotifyTasteScreen

import com.metrolist.music.ui.screens.wrapped.WrappedScreen
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    activity: Activity,
    snackbarHostState: SnackbarHostState,
) {
    // Primary tab content is rendered by MainTabContainer in MainActivity so each visited
    // tab stays mounted. These destinations remain as navigation anchors for nested routes.
    composable(Screens.Home.route) {}
    composable(Screens.Search.route) {}
    composable(Screens.Library.route) {}

    composable(Screens.ListenTogether.route) {
        ListenTogetherScreen(navController, showTopBar = false)
    }

    composable(
        route = "listen_together_from_topbar",
    ) {
        ListenTogetherScreen(navController, showTopBar = true)
    }

    composable("history") {
        HistoryScreen(navController)
    }

    composable("stats") {
        StatsScreen(navController)
    }

    composable("mood_and_genres") {
        MoodAndGenresScreen(navController)
    }

    composable("account") {
        AccountScreen(navController)
    }

    composable("new_release") {
        NewReleaseScreen(navController)
    }

    composable("charts_screen") {
        ChartsScreen(navController)
    }

    composable(
        route = "browse/{browseId}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        BrowseScreen(
            navController,
            it.arguments?.getString("browseId"),
        )
    }

    composable(
        route = "search/{query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) { backStackEntry ->
        OnlineSearchResult(
            savedStateHandle = backStackEntry.savedStateHandle
        )

    }

    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController)
    }

    composable(
        route = "artist/{artistId}?isPodcastChannel={isPodcastChannel}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("isPodcastChannel") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) {
        ArtistScreen(navController)
    }

    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController)
    }

    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController)
    }

    composable(
        route = "online_podcast/{podcastId}",
        arguments =
            listOf(
                navArgument("podcastId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePodcastScreen(navController, scrollBehavior)
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController)
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController)
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController)
    }

    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController)
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    composable(
        route = "settings",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        SettingsScreen(navController, latestVersionName)
    }

    composable(
        route = "settings/appearance",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        AppearanceSettings(navController, activity, snackbarHostState)
    }

    composable(
        route = "settings/appearance/theme",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        ThemeScreen(navController)
    }

    composable(
        route = "settings/content",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        ContentSettings(navController)
    }

    composable(
        route = "settings/content/romanization",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        RomanizationSettings(navController)
    }

    composable(
        route = "settings/ai",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        AiSettings(navController)
    }

    composable(
        route = "settings/dj",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        DjSettings(navController)
    }

    composable(
        route = "settings/player",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        PlayerSettings(navController)
    }

    composable(
        route = "settings/stream_sources",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        StreamSourcesSettings(navController)
    }

    composable(
        route = "settings/storage",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        StorageSettings(navController)
    }

    composable(
        route = "settings/privacy",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        PrivacySettings(navController)
    }

    composable(
        route = "settings/backup_restore",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        BackupAndRestore(navController)
    }

    composable(
        route = "settings/integrations",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        IntegrationScreen(navController)
    }

    composable(
        route = "settings/integrations/discord",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        DiscordSettings(navController)
    }

    composable(
        route = "settings/integrations/lastfm",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        LastFMSettings(navController)
    }

    composable(
        route = "settings/integrations/spotify",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        SpotifySettings(navController)
    }

    composable(
        route = "settings/integrations/spotify/taste",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        SpotifyTasteScreen(navController)
    }

    composable(
        route = "settings/integrations/listen_together",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        ListenTogetherSettings(navController)
    }

    composable(
        route = "settings/updater",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        UpdaterScreen(navController)
    }

    composable("login") {
        LoginScreen(navController)
    }

    composable("wrapped") {
        WrappedScreen()
    }

    composable("equalizer") {
        EqScreen()
    }

    composable("eq_wizard") {
        WizardScreen(onNavigateBack = {
            navController.popBackStack()
        })
    }

    composable(
        route = "recognition?autoStart={autoStart}",
        arguments =
            listOf(
                navArgument("autoStart") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) {
        RecognitionScreen(navController, it.arguments?.getBoolean("autoStart") ?: false)
    }

    composable("recognition_history") {
        RecognitionHistoryScreen(navController)
    }
    composable(
        route = "settings/android_auto",
        enterTransition = { nestedScreenEnter() },
        exitTransition = { nestedScreenExit() },
        popEnterTransition = { nestedScreenPopEnter() },
        popExitTransition = { nestedScreenPopExit() },
    ) {
        AndroidAutoSettings(navController)
    }
}
