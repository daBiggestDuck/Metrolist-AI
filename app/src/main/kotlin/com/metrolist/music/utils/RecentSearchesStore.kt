/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.constants.RecentSearchEntitiesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Spotify-style recent searches persisted in DataStore (artwork + title + subtitle).
 * Prefer songs; also stores artists, albums, playlists, and plain query text.
 */
object RecentSearchesStore {
    private const val MAX_ITEMS = 20

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Serializable
    data class Entry(
        val id: String,
        val type: String,
        val title: String,
        val subtitle: String? = null,
        val thumbnailUrl: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        companion object {
            const val TYPE_SONG = "song"
            const val TYPE_ARTIST = "artist"
            const val TYPE_ALBUM = "album"
            const val TYPE_PLAYLIST = "playlist"
            const val TYPE_PODCAST = "podcast"
            const val TYPE_EPISODE = "episode"
            const val TYPE_QUERY = "query"
        }
    }

    fun flow(context: Context): Flow<List<Entry>> =
        context.dataStore.data.map { prefs ->
            decode(prefs[RecentSearchEntitiesKey].orEmpty())
        }

    suspend fun add(context: Context, entry: Entry) {
        if (context.dataStore.get(PauseSearchHistoryKey, false)) return
        context.safeDataStoreEdit { prefs ->
            val current = decode(prefs[RecentSearchEntitiesKey].orEmpty())
            val next =
                listOf(entry.copy(timestamp = System.currentTimeMillis())) +
                    current.filterNot { it.id == entry.id && it.type == entry.type }
            prefs[RecentSearchEntitiesKey] = encode(next.take(MAX_ITEMS))
        }
    }

    suspend fun addYtItem(context: Context, item: YTItem) {
        fromYtItem(item)?.let { add(context, it) }
    }

    suspend fun addQuery(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        add(
            context,
            Entry(
                id = "query:$trimmed",
                type = Entry.TYPE_QUERY,
                title = trimmed,
            ),
        )
    }

    suspend fun remove(context: Context, entry: Entry) {
        context.safeDataStoreEdit { prefs ->
            val current = decode(prefs[RecentSearchEntitiesKey].orEmpty())
            prefs[RecentSearchEntitiesKey] =
                encode(current.filterNot { it.id == entry.id && it.type == entry.type })
        }
    }

    suspend fun clear(context: Context) {
        context.safeDataStoreEdit { prefs ->
            prefs[RecentSearchEntitiesKey] = "[]"
        }
    }

    fun fromYtItem(item: YTItem): Entry? =
        when (item) {
            is SongItem ->
                Entry(
                    id = item.id,
                    type = Entry.TYPE_SONG,
                    title = item.title,
                    subtitle = item.artists.joinToString { it.name }.ifEmpty { null },
                    thumbnailUrl = item.thumbnail,
                )
            is ArtistItem ->
                Entry(
                    id = item.id,
                    type = Entry.TYPE_ARTIST,
                    title = item.title,
                    thumbnailUrl = item.thumbnail,
                )
            is AlbumItem ->
                Entry(
                    id = item.id,
                    type = Entry.TYPE_ALBUM,
                    title = item.title,
                    subtitle = item.artists?.joinToString { it.name },
                    thumbnailUrl = item.thumbnail,
                )
            is PlaylistItem ->
                Entry(
                    id = item.id,
                    type = Entry.TYPE_PLAYLIST,
                    title = item.title,
                    subtitle = item.author?.name,
                    thumbnailUrl = item.thumbnail,
                )
            is PodcastItem ->
                Entry(
                    id = item.id,
                    type = Entry.TYPE_PODCAST,
                    title = item.title,
                    subtitle = item.author?.name,
                    thumbnailUrl = item.thumbnail,
                )
            is EpisodeItem ->
                Entry(
                    id = item.id,
                    type = Entry.TYPE_EPISODE,
                    title = item.title,
                    subtitle = item.podcast?.name ?: item.author?.name,
                    thumbnailUrl = item.thumbnail,
                )
        }

    private fun encode(entries: List<Entry>): String = json.encodeToString(entries)

    private fun decode(raw: String): List<Entry> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Entry>>(raw) }.getOrDefault(emptyList())
    }
}
