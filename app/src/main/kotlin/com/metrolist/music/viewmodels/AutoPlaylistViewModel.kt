/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.ListeningTasteExcludedSongIdsKey
import com.metrolist.music.constants.SongSortDescendingKey
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.constants.SongSortTypeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.filterExplicit
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private data class PlaylistPreferences(
        val sortType: SongSortType,
        val descending: Boolean,
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
        val excludedSongIds: Set<String>,
    )

    val likedSongs =
        context.dataStore.data
            .map {
                PlaylistPreferences(
                    sortType = it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    descending = it[SongSortDescendingKey] ?: true,
                    hideExplicit = it[HideExplicitKey] ?: false,
                    hideVideoSongs = it[HideVideoSongsKey] ?: false,
                    excludedSongIds = it[ListeningTasteExcludedSongIdsKey].orEmpty(),
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { preferences ->
                when (playlist) {
                    "liked" -> database.likedSongs(preferences.sortType, preferences.descending)
                        .map { it.filterExplicit(preferences.hideExplicit).filterVideoSongs(preferences.hideVideoSongs) }

                    "downloaded" -> database.downloadedSongs(preferences.sortType, preferences.descending)
                        .map { it.filterExplicit(preferences.hideExplicit).filterVideoSongs(preferences.hideVideoSongs) }

                    "uploaded" -> database.uploadedSongs(preferences.sortType, preferences.descending)
                        .map { it.filterExplicit(preferences.hideExplicit).filterVideoSongs(preferences.hideVideoSongs) }

                    "disliked" -> kotlinx.coroutines.flow.flow {
                        val songs =
                            if (preferences.excludedSongIds.isEmpty()) {
                                emptyList()
                            } else {
                                database.getSongsByIds(preferences.excludedSongIds.toList())
                                    .filter { !it.song.isEpisode }
                                    .filterExplicit(preferences.hideExplicit)
                                    .filterVideoSongs(preferences.hideVideoSongs)
                                    .sortedWith(dislikedComparator(preferences.sortType, preferences.descending))
                            }
                        emit(songs)
                    }

                    else -> flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    private fun dislikedComparator(
        sortType: SongSortType,
        descending: Boolean,
    ): Comparator<com.metrolist.music.db.entities.Song> {
        val comparator =
            when (sortType) {
                SongSortType.NAME -> compareBy<com.metrolist.music.db.entities.Song> { it.song.title.lowercase() }
                SongSortType.ARTIST -> compareBy { song -> song.orderedArtists.firstOrNull()?.name?.lowercase().orEmpty() }
                SongSortType.PLAY_TIME -> compareBy { it.song.totalPlayTime }
                SongSortType.CREATE_DATE -> compareBy { it.song.inLibrary ?: java.time.LocalDateTime.MIN }
            }
        return if (descending) comparator.reversed() else comparator
    }

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            when (playlist) {
                "liked" -> syncUtils.syncLikedSongsSuspend()
                "uploaded" -> syncUtils.syncUploadedSongsSuspend()
            }
            _isRefreshing.value = false
        }
    }
}
