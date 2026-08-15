/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.ui.component.aura.auraContentPaddingBelowChrome
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_PLAYLIST
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistSortDescendingKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.PlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.constants.ShowCachedPlaylistKey
import com.metrolist.music.constants.ShowDislikedPlaylistKey
import com.metrolist.music.constants.ShowDownloadedPlaylistKey
import com.metrolist.music.constants.ShowLikedPlaylistKey
import com.metrolist.music.constants.ShowTopPlaylistKey
import com.metrolist.music.constants.ShowUploadedPlaylistKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.ui.component.LibrarySearchEmptyPlaceholder
import com.metrolist.music.ui.component.LibrarySearchHeader
import com.metrolist.music.ui.component.LibraryPlaylistGridItem
import com.metrolist.music.ui.component.LibraryPlaylistListItem
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.ui.component.PlaylistListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas
import com.metrolist.music.ui.component.aura.auraStickyChromeBackground
import com.metrolist.music.ui.menu.SelectionPlaylistMenu
import com.metrolist.music.extensions.matchesNormalizedQuery
import com.metrolist.music.extensions.normalizeForSearch
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LibraryPlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class VisiblePlaylistItem(
    val key: String,
    val playlist: Playlist,
    val autoPlaylist: Boolean,
    val route: String? = null,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit = {},
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var viewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.LIST)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSortTypeKey,
        PlaylistSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSortDescendingKey,
        true
    )
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val normalizedQuery = remember(searchQuery) { searchQuery.normalizeForSearch() }
    val filteredPlaylists = remember(playlists, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            playlists
        } else {
            playlists.filter { playlist ->
                matchesNormalizedQuery(normalizedQuery, playlist.playlist.name)
            }
        }
    }

    val topSize by viewModel.topValue.collectAsStateWithLifecycle(initialValue = "50")
    val likedName = stringResource(R.string.liked)
    val offlineName = stringResource(R.string.offline)
    val myTopName = stringResource(R.string.my_top) + " $topSize"
    val uploadedName = stringResource(R.string.uploaded_playlist)
    val cachedName = stringResource(R.string.cached_playlist)
    val dislikedName = stringResource(R.string.disliked_songs)

    val likedPlaylist = remember(likedName) {
        Playlist(
            playlist = PlaylistEntity(id = "auto_liked", name = likedName),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val downloadPlaylist = remember(offlineName) {
        Playlist(
            playlist = PlaylistEntity(id = "auto_downloaded", name = offlineName),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val topPlaylist = remember(myTopName) {
        Playlist(
            playlist = PlaylistEntity(id = "auto_top", name = myTopName),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val uploadedPlaylist = remember(uploadedName) {
        Playlist(
            playlist = PlaylistEntity(id = "auto_uploaded", name = uploadedName),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val cachedPlaylist = remember(cachedName) {
        Playlist(
            playlist = PlaylistEntity(id = "auto_cached", name = cachedName),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }
    val dislikedPlaylist = remember(dislikedName) {
        Playlist(
            playlist = PlaylistEntity(id = "auto_disliked", name = dislikedName),
            songCount = 0,
            songThumbnails = emptyList(),
        )
    }

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    val (showDisliked) = rememberPreference(ShowDislikedPlaylistKey, true)
    val showLikedPlaylist = showLiked && matchesNormalizedQuery(normalizedQuery, likedPlaylist.playlist.name)
    val showDownloadedPlaylist =
        showDownloaded && matchesNormalizedQuery(normalizedQuery, downloadPlaylist.playlist.name)
    val showCachedPlaylists = showCached && matchesNormalizedQuery(normalizedQuery, cachedPlaylist.playlist.name)
    val showDislikedPlaylists = showDisliked && matchesNormalizedQuery(normalizedQuery, dislikedPlaylist.playlist.name)
    val showTopPlaylists = showTop && matchesNormalizedQuery(normalizedQuery, topPlaylist.playlist.name)
    val showUploadedPlaylists =
        showUploaded && matchesNormalizedQuery(normalizedQuery, uploadedPlaylist.playlist.name)

    val visibleResults = remember(
        filteredPlaylists,
        showLikedPlaylist,
        showDownloadedPlaylist,
        showCachedPlaylists,
        showDislikedPlaylists,
        showTopPlaylists,
        showUploadedPlaylists,
        topSize,
    ) {
        buildList {
            if (showLikedPlaylist) {
                add(
                    VisiblePlaylistItem(
                        key = "likedPlaylist",
                        playlist = likedPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/liked",
                    ),
                )
            }
            if (showDownloadedPlaylist) {
                add(
                    VisiblePlaylistItem(
                        key = "downloadedPlaylist",
                        playlist = downloadPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/downloaded",
                    ),
                )
            }
            if (showCachedPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "cachedPlaylist",
                        playlist = cachedPlaylist,
                        autoPlaylist = true,
                        route = "cache_playlist/cached",
                    ),
                )
            }
            if (showDislikedPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "dislikedPlaylist",
                        playlist = dislikedPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/disliked",
                    ),
                )
            }
            if (showTopPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "TopPlaylist",
                        playlist = topPlaylist,
                        autoPlaylist = true,
                        route = "top_playlist/$topSize",
                    ),
                )
            }
            if (showUploadedPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "uploadedPlaylist",
                        playlist = uploadedPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/uploaded",
                    ),
                )
            }

            filteredPlaylists
                .distinctBy { it.id }
                .forEach { playlist ->
                    add(
                        VisiblePlaylistItem(
                            key = playlist.id,
                            playlist = playlist,
                            autoPlaylist = false,
                        ),
                    )
                }
        }
    }

    val selectablePlaylists = remember(visibleResults) {
        visibleResults.filter { !it.autoPlaylist }.map { it.playlist }
    }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(selectablePlaylists, inSelectMode) {
        if (!inSelectMode) return@LaunchedEffect
        val validIds = selectablePlaylists.map { it.id }.toSet()
        selection.removeAll { it !in validIds }
        if (selection.isEmpty() && selectablePlaylists.isEmpty()) {
            onExitSelectionMode()
        }
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            kotlinx.coroutines.delay(400)
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val librarySearchRequest =
        backStackEntry?.savedStateHandle?.getStateFlow("librarySearch", false)?.collectAsStateWithLifecycle()
    LaunchedEffect(librarySearchRequest?.value) {
        if (librarySearchRequest?.value == true) {
            isSearchActive = true
            backStackEntry?.savedStateHandle?.set("librarySearch", false)
        }
    }

    // Create playlist lives in the Library AuraTopBar "+" (Spotify header pattern).

    val selectionBarContent = @Composable {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .auraStickyChromeBackground(AuraPlayerCanvas)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExitSelectionMode) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.n_selected, selection.size, selection.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Checkbox(
                checked = selection.size == selectablePlaylists.size && selection.isNotEmpty(),
                onCheckedChange = {
                    if (selection.size == selectablePlaylists.size) {
                        selection.clear()
                    } else {
                        selection.clear()
                        selection.addAll(selectablePlaylists.map { it.id })
                    }
                },
            )
            IconButton(
                onClick = {
                    menuState.show {
                        SelectionPlaylistMenu(
                            playlists = selectablePlaylists.filter { it.id in selection },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode,
                        )
                    }
                },
                enabled = selection.isNotEmpty(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                )
            }
        }
    }

    val headerContent = @Composable {
        LibrarySearchHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onBack = {
                isSearchActive = false
                viewModel.updateSearchQuery("")
            },
            keyboardController = keyboardController,
            modifier = Modifier,
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        PlaylistSortType.NAME -> R.string.sort_by_name
                        PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                        PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(
                    R.plurals.n_playlist,
                    visibleResults.count { !it.autoPlaylist },
                    visibleResults.count { !it.autoPlaylist },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            IconButton(
                onClick = {
                    viewType = viewType.toggle()
                },
                modifier = Modifier.padding(end = 8.dp).size(40.dp),
            ) {
                Icon(
                    painter =
                    painterResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.drawable.list
                            LibraryViewType.GRID -> R.drawable.grid_view
                        },
                    ),
                    contentDescription = stringResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.string.switch_to_grid_view
                            LibraryViewType.GRID -> R.string.switch_to_list_view
                        },
                    ),
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = auraContentPaddingBelowChrome(),
                ) {
                    stickyHeader(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        if (inSelectMode) {
                            selectionBarContent()
                        } else {
                            filterContent()
                        }
                    }

                    stickyHeader(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        if (!inSelectMode) {
                            headerContent()
                        }
                    }

                    if (visibleResults.isEmpty()) {
                        item(key = "empty_placeholder") {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                LibrarySearchEmptyPlaceholder(
                                    modifier = Modifier.animateItem(),
                                    icon = R.drawable.playlist_play,
                                    text = stringResource(R.string.library_playlist_empty),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleResults,
                        key = { it.key },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        if (item.autoPlaylist) {
                            if (!inSelectMode) {
                                PlaylistListItem(
                                    playlist = item.playlist,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                item.route?.let(navController::navigate)
                                            }
                                            .animateItem(),
                                )
                            }
                        } else {
                            val selected = item.playlist.id in selection
                            LibraryPlaylistListItem(
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = item.playlist,
                                isSelected = selected,
                                inSelectMode = inSelectMode,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selection.add(item.playlist.id)
                                    } else {
                                        selection.remove(item.playlist.id)
                                    }
                                },
                                onLongClickSelect = {
                                    if (!inSelectMode) {
                                        inSelectMode = true
                                        selection.clear()
                                        selection.add(item.playlist.id)
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns =
                    GridCells.Adaptive(
                        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                    ),
                    contentPadding = auraContentPaddingBelowChrome(),
                ) {
                    stickyHeader(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        if (inSelectMode) {
                            selectionBarContent()
                        } else {
                            filterContent()
                        }
                    }

                    stickyHeader(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        if (!inSelectMode) {
                            headerContent()
                        }
                    }

                    if (visibleResults.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                LibrarySearchEmptyPlaceholder(
                                    modifier = Modifier.animateItem(),
                                    icon = R.drawable.playlist_play,
                                    text = stringResource(R.string.library_playlist_empty),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleResults,
                        key = { it.key },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        if (item.autoPlaylist) {
                            if (!inSelectMode) {
                                PlaylistGridItem(
                                    playlist = item.playlist,
                                    fillMaxWidth = true,
                                    autoPlaylist = true,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    item.route?.let(navController::navigate)
                                                },
                                            )
                                            .animateItem(),
                                )
                            }
                        } else {
                            val selected = item.playlist.id in selection
                            LibraryPlaylistGridItem(
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = item.playlist,
                                isSelected = selected,
                                inSelectMode = inSelectMode,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selection.add(item.playlist.id)
                                    } else {
                                        selection.remove(item.playlist.id)
                                    }
                                },
                                onLongClickSelect = {
                                    if (!inSelectMode) {
                                        inSelectMode = true
                                        selection.clear()
                                        selection.add(item.playlist.id)
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}
