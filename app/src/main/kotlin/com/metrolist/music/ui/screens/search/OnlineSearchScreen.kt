/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.constants.SuggestionItemHeight
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.aura.AuraHairline
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.utils.RecentSearchesStore
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private enum class SuggestionCategory {
    ALL,
    SONGS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
}

private fun YTItem.matchesCategory(category: SuggestionCategory): Boolean =
    when (category) {
        SuggestionCategory.ALL -> true
        SuggestionCategory.SONGS -> this is SongItem || this is EpisodeItem
        SuggestionCategory.ARTISTS -> this is ArtistItem
        SuggestionCategory.ALBUMS -> this is AlbumItem
        SuggestionCategory.PLAYLISTS -> this is PlaylistItem || this is PodcastItem
    }

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
    FlowPreview::class,
)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)
    var itemCategory by remember { mutableStateOf(SuggestionCategory.ALL) }

    val canvasColor = if (pureBlack) Color.Black else Color(0xFF121212)
    val filteredItems =
        remember(viewState.items, itemCategory) {
            viewState.items.filter { it.matchesCategory(itemCategory) }
        }

    LaunchedEffect(query) {
        itemCategory = SuggestionCategory.ALL
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce(300L).collectLatest {
            viewModel.query.value = it
        }
    }

    fun bumpRecent(entry: RecentSearchesStore.Entry) {
        coroutineScope.launch(Dispatchers.IO) {
            RecentSearchesStore.add(context, entry)
        }
    }

    fun playRecent(entry: RecentSearchesStore.Entry) {
        bumpRecent(entry)
        when (entry.type) {
            RecentSearchesStore.Entry.TYPE_SONG,
            RecentSearchesStore.Entry.TYPE_EPISODE,
            -> {
                val metadata =
                    MediaMetadata(
                        id = entry.id,
                        title = entry.title,
                        artists =
                            entry.subtitle?.let { subtitle ->
                                listOf(MediaMetadata.Artist(id = null, name = subtitle))
                            }.orEmpty(),
                        duration = 0,
                        thumbnailUrl = entry.thumbnailUrl,
                        isEpisode = entry.type == RecentSearchesStore.Entry.TYPE_EPISODE,
                    )
                if (entry.id == mediaMetadata?.id) {
                    playerConnection.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        if (autoRadioQueue) {
                            YouTubeQueue.radio(metadata)
                        } else {
                            YouTubeQueue(WatchEndpoint(videoId = entry.id))
                        },
                    )
                    onDismiss()
                }
            }

            RecentSearchesStore.Entry.TYPE_ARTIST -> {
                navController.navigate("artist/${entry.id}")
                onDismiss()
            }

            RecentSearchesStore.Entry.TYPE_ALBUM -> {
                navController.navigate("album/${entry.id}")
                onDismiss()
            }

            RecentSearchesStore.Entry.TYPE_PLAYLIST -> {
                navController.navigate("online_playlist/${entry.id}")
                onDismiss()
            }

            RecentSearchesStore.Entry.TYPE_PODCAST -> {
                navController.navigate("online_podcast/${entry.id}")
                onDismiss()
            }

            RecentSearchesStore.Entry.TYPE_QUERY -> {
                onSearch(entry.title)
            }
        }
    }

    fun handleYtItemClick(item: YTItem) {
        viewModel.rememberItem(item)
        when (item) {
            is SongItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        if (autoRadioQueue) {
                            YouTubeQueue.radio(item.toMediaMetadata())
                        } else {
                            ListQueue(
                                title = item.title,
                                items = listOf(item.toMediaItem()),
                            )
                        },
                    )
                    onDismiss()
                }
            }

            is AlbumItem -> {
                navController.navigate("album/${item.id}")
                onDismiss()
            }

            is ArtistItem -> {
                navController.navigate("artist/${item.id}")
                onDismiss()
            }

            is PlaylistItem -> {
                navController.navigate("online_playlist/${item.id}")
                onDismiss()
            }

            is PodcastItem -> {
                navController.navigate("online_podcast/${item.id}")
                onDismiss()
            }

            is EpisodeItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        YouTubeQueue.radio(item.toMediaMetadata()),
                    )
                    onDismiss()
                }
            }
        }
    }

    fun showYtItemMenu(item: YTItem) {
        menuState.show {
            when (item) {
                is SongItem -> {
                    YouTubeSongMenu(
                        song = item,
                        onDismiss = {
                            menuState.dismiss()
                            onDismiss()
                        },
                    )
                }

                is AlbumItem -> {
                    YouTubeAlbumMenu(
                        albumItem = item,
                        onDismiss = {
                            menuState.dismiss()
                            onDismiss()
                        },
                    )
                }

                is ArtistItem -> {
                    YouTubeArtistMenu(
                        artist = item,
                        onDismiss = {
                            menuState.dismiss()
                            onDismiss()
                        },
                    )
                }

                is PlaylistItem -> {
                    YouTubePlaylistMenu(
                        playlist = item,
                        coroutineScope = coroutineScope,
                        onDismiss = {
                            menuState.dismiss()
                            onDismiss()
                        },
                    )
                }

                is PodcastItem -> {
                    YouTubePlaylistMenu(
                        playlist = item.asPlaylistItem(),
                        coroutineScope = coroutineScope,
                        onDismiss = {
                            menuState.dismiss()
                            onDismiss()
                        },
                    )
                }

                is EpisodeItem -> {
                    YouTubeSongMenu(
                        song = item.asSongItem(),
                        onDismiss = {
                            menuState.dismiss()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
        modifier =
            Modifier
                .fillMaxSize()
                .background(canvasColor),
    ) {
        if (query.isEmpty()) {
            if (viewState.recents.isNotEmpty()) {
                item(key = "recent_searches_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                                .animateItem(),
                    ) {
                        Text(
                            text = stringResource(R.string.recent_searches),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.clearRecents() }) {
                            Text(
                                text = stringResource(R.string.clear_recent_searches),
                                color = AuraSpotifyGreen,
                            )
                        }
                    }
                }

                items(
                    items = viewState.recents,
                    key = { "recent_${it.type}_${it.id}" },
                ) { entry ->
                    RecentSearchRow(
                        entry = entry,
                        isActive =
                            when (entry.type) {
                                RecentSearchesStore.Entry.TYPE_SONG,
                                RecentSearchesStore.Entry.TYPE_EPISODE,
                                -> mediaMetadata?.id == entry.id

                                else -> false
                            },
                        isPlaying = isPlaying,
                        pureBlack = pureBlack,
                        onClick = { playRecent(entry) },
                        onRemove = { viewModel.removeRecent(entry) },
                        onFillTextField = {
                            onQueryChange(TextFieldValue(entry.title, TextRange(entry.title.length)))
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        } else {
            if (viewState.isUrlQuery && viewState.parsedUrlItem != null) {
                item(key = "parsed_url_header") {
                    Text(
                        text = stringResource(R.string.parsed_from_link),
                        style = MaterialTheme.typography.labelMedium,
                        color = AuraSpotifyGreen,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .animateItem(),
                    )
                }

                item(key = "parsed_url_item") {
                    val parsedItem = viewState.parsedUrlItem!!
                    YouTubeListItem(
                        item = parsedItem,
                        isActive =
                            when (parsedItem) {
                                is SongItem -> mediaMetadata?.id == parsedItem.id
                                is AlbumItem -> mediaMetadata?.album?.id == parsedItem.id
                                is EpisodeItem -> mediaMetadata?.id == parsedItem.id
                                else -> false
                            },
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(onClick = { showYtItemMenu(parsedItem) }) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = { handleYtItemClick(parsedItem) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showYtItemMenu(parsedItem)
                                    },
                                )
                                .background(canvasColor)
                                .animateItem(),
                    )
                }

                item(key = "parsed_url_divider") {
                    HorizontalDivider(
                        modifier =
                            Modifier
                                .padding(vertical = 8.dp)
                                .animateItem(),
                    )
                }
            }

            if (viewState.items.isNotEmpty() && !viewState.isUrlQuery) {
                item(key = "category_pills") {
                    SearchCategoryPillsRow(
                        selected = itemCategory,
                        onSelected = { itemCategory = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .animateItem(),
                    )
                }
            }

            items(
                items = if (viewState.isUrlQuery) emptyList() else filteredItems,
                key = { "item_${it.id}" },
            ) { item ->
                YouTubeListItem(
                    item = item,
                    isActive =
                        when (item) {
                            is SongItem -> mediaMetadata?.id == item.id
                            is AlbumItem -> mediaMetadata?.album?.id == item.id
                            is EpisodeItem -> mediaMetadata?.id == item.id
                            else -> false
                        },
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(onClick = { showYtItemMenu(item) }) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = { handleYtItemClick(item) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showYtItemMenu(item)
                                },
                            )
                            .background(canvasColor)
                            .animateItem(),
                )
            }

            if (
                filteredItems.isNotEmpty() &&
                    viewState.suggestions.isNotEmpty() &&
                    !viewState.isUrlQuery
            ) {
                item(key = "search_divider") {
                    HorizontalDivider(modifier = Modifier.animateItem())
                }
                item(key = "search_divider_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(viewState.suggestions, key = { "suggestion_$it" }) { suggestion ->
                SuggestionItem(
                    query = suggestion,
                    online = true,
                    onClick = {
                        viewModel.rememberQuery(suggestion)
                        onSearch(suggestion)
                    },
                    onFillTextField = {
                        onQueryChange(TextFieldValue(suggestion, TextRange(suggestion.length)))
                    },
                    modifier = Modifier.animateItem(),
                    pureBlack = pureBlack,
                )
            }
        }
    }
}

@Composable
private fun SearchCategoryPillsRow(
    selected: SuggestionCategory,
    onSelected: (SuggestionCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val chips =
        listOf(
            SuggestionCategory.ALL to stringResource(R.string.filter_all),
            SuggestionCategory.SONGS to stringResource(R.string.filter_songs),
            SuggestionCategory.ARTISTS to stringResource(R.string.filter_artists),
            SuggestionCategory.ALBUMS to stringResource(R.string.filter_albums),
            SuggestionCategory.PLAYLISTS to stringResource(R.string.filter_playlists),
        )

    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(4.dp))
        chips.forEach { (category, label) ->
            val isSelected = selected == category
            val backgroundColor = if (isSelected) AuraSpotifyGreen else Color(0xFF282828)
            val contentColor = if (isSelected) Color.Black else Color.White
            val borderColor = if (isSelected) Color.Transparent else AuraHairline

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .height(36.dp)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .clip(pillShape)
                                    .background(backgroundColor)
                            } else {
                                Modifier.auraFloatingIsland(
                                    shape = pillShape,
                                    color = backgroundColor,
                                    borderColor = borderColor,
                                    elevation = 0.dp,
                                )
                            },
                        )
                        .clickable { onSelected(category) }
                        .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun RecentSearchRow(
    entry: RecentSearchesStore.Entry,
    isActive: Boolean,
    isPlaying: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onFillTextField: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canvasColor = if (pureBlack) Color.Black else Color(0xFF121212)
    val subtitle =
        entry.subtitle ?: recentEntityLabel(entry.type)
    val thumbnailShape =
        if (entry.type == RecentSearchesStore.Entry.TYPE_ARTIST) {
            CircleShape
        } else {
            RoundedCornerShape(ThumbnailCornerRadius)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ListItemHeight)
                .background(if (isActive) AuraSpotifyGreen.copy(alpha = 0.12f) else canvasColor)
                .clickable(onClick = onClick)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Box(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.type == RecentSearchesStore.Entry.TYPE_QUERY || entry.thumbnailUrl.isNullOrBlank()) {
                Box(
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(thumbnailShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(thumbnailShape),
                )
            }

            if (isActive && isPlaying) {
                Box(
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(thumbnailShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = AuraSpotifyGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) AuraSpotifyGreen else Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.55f),
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.alpha(0.5f),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.dismiss),
            )
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(0.5f),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_top_left),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun recentEntityLabel(type: String): String =
    stringResource(
        when (type) {
            RecentSearchesStore.Entry.TYPE_SONG -> R.string.search_entity_song
            RecentSearchesStore.Entry.TYPE_ARTIST -> R.string.search_entity_artist
            RecentSearchesStore.Entry.TYPE_ALBUM -> R.string.search_entity_album
            RecentSearchesStore.Entry.TYPE_PLAYLIST -> R.string.search_entity_playlist
            RecentSearchesStore.Entry.TYPE_PODCAST -> R.string.search_entity_podcast
            RecentSearchesStore.Entry.TYPE_EPISODE -> R.string.search_entity_episode
            RecentSearchesStore.Entry.TYPE_QUERY -> R.string.search_entity_query
            else -> R.string.search_entity_query
        },
    )

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SuggestionItemHeight)
                .background(if (pureBlack) Color.Black else Color(0xFF121212))
                .clickable(onClick = onClick)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Icon(
            painterResource(if (online) R.drawable.search else R.drawable.history),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).alpha(0.5f),
            tint = if (online) AuraSpotifyGreen.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f),
        )

        Text(
            text = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )

        if (!online) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(0.5f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.dismiss),
                )
            }
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(0.5f),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_top_left),
                contentDescription = null,
            )
        }
    }
}
