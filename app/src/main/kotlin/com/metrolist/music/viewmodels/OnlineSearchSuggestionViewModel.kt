/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.utils.RecentSearchesStore
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val query = MutableStateFlow("")
        private val _viewState = MutableStateFlow(SearchSuggestionViewState())
        val viewState = _viewState.asStateFlow()

        init {
            viewModelScope.launch {
                query
                    .flatMapLatest { searchQuery ->
                        if (searchQuery.isEmpty()) {
                            RecentSearchesStore.flow(context).map { recents ->
                                SearchSuggestionViewState(recents = recents)
                            }
                        } else {
                            val parsedUrl = YouTubeUrlParser.parse(searchQuery)
                            if (parsedUrl != null) {
                                flow {
                                    val parsedItem = fetchParsedUrlItem(parsedUrl)
                                    emit(
                                        SearchSuggestionViewState(
                                            suggestions = emptyList(),
                                            items = parsedItem?.let { listOf(it) } ?: emptyList(),
                                            parsedUrlItem = parsedItem,
                                            isUrlQuery = true,
                                        ),
                                    )
                                }
                            } else {
                                flow {
                                    val result = YouTube.searchSuggestions(searchQuery).getOrNull()
                                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

                                    var items =
                                        result
                                            ?.recommendedItems
                                            ?.distinctBy { it.id }
                                            ?.filterExplicit(hideExplicit)
                                            ?.filterVideoSongs(hideVideoSongs)
                                            .orEmpty()

                                    if (items.isEmpty()) {
                                        items =
                                            YouTube
                                                .searchSummary(searchQuery)
                                                .getOrNull()
                                                ?.summaries
                                                ?.flatMap { it.items }
                                                ?.distinctBy { it.id }
                                                ?.filterExplicit(hideExplicit)
                                                ?.filterVideoSongs(hideVideoSongs)
                                                ?.take(12)
                                                .orEmpty()
                                    }

                                    emit(
                                        SearchSuggestionViewState(
                                            suggestions = result?.queries.orEmpty(),
                                            items = items,
                                        ),
                                    )
                                }
                            }
                        }
                    }.collect { state ->
                        _viewState.value = state
                    }
            }
        }

        fun clearRecents() {
            viewModelScope.launch(Dispatchers.IO) {
                RecentSearchesStore.clear(context)
            }
        }

        fun removeRecent(entry: RecentSearchesStore.Entry) {
            viewModelScope.launch(Dispatchers.IO) {
                RecentSearchesStore.remove(context, entry)
            }
        }

        fun rememberItem(item: YTItem) {
            viewModelScope.launch(Dispatchers.IO) {
                RecentSearchesStore.addYtItem(context, item)
            }
        }

        fun rememberQuery(queryText: String) {
            viewModelScope.launch(Dispatchers.IO) {
                RecentSearchesStore.addQuery(context, queryText)
            }
        }

        private suspend fun fetchParsedUrlItem(parsedUrl: YouTubeUrlParser.ParsedUrl): YTItem? =
            when (parsedUrl) {
                is YouTubeUrlParser.ParsedUrl.Video -> {
                    YouTube
                        .next(WatchEndpoint(videoId = parsedUrl.id))
                        .getOrNull()
                        ?.items
                        ?.firstOrNull()
                }

                is YouTubeUrlParser.ParsedUrl.Playlist -> {
                    YouTube
                        .playlist(parsedUrl.id)
                        .getOrNull()
                        ?.playlist
                }

                is YouTubeUrlParser.ParsedUrl.Album -> {
                    val albumResult = YouTube.album("MPREb_${parsedUrl.id}")
                    if (albumResult.isSuccess) {
                        albumResult.getOrNull()?.album
                    } else {
                        YouTube
                            .playlist(parsedUrl.id)
                            .getOrNull()
                            ?.playlist
                    }
                }

                is YouTubeUrlParser.ParsedUrl.Artist -> {
                    YouTube
                        .artist(parsedUrl.id)
                        .getOrNull()
                        ?.artist
                }
            }
    }

data class SearchSuggestionViewState(
    val recents: List<RecentSearchesStore.Entry> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val parsedUrlItem: YTItem? = null,
    val isUrlQuery: Boolean = false,
)
