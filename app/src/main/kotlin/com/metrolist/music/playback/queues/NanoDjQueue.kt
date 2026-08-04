/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.ai.NanoDjEngine
import com.metrolist.music.ai.NanoDjSession
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Continuous radio queue curated by Gemini Nano (Spotify DJ replacement).
 * Each page asks Nano for the next batch of song queries, then resolves them on YouTube Music.
 */
class NanoDjQueue(
    private val tasteSummary: String,
    private val seedArtists: List<String>,
    private val seedTracks: List<String>,
    private val enableNano: Boolean,
    private val initialItems: List<MediaItem> = emptyList(),
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private val playedIds = LinkedHashSet<String>()
    private val playedTitles = ArrayDeque<String>()
    private var exhausted = false
    private var pagesLoaded = 0

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            NanoDjSession.start(null, usedAi = false)
            val opening =
                NanoDjEngine.openingLine(
                    context = currentContext(),
                    enableNano = enableNano,
                )
            NanoDjSession.start(opening, usedAi = enableNano)

            val seeded = initialItems.filterNot { it.mediaId in playedIds }
            val items =
                if (seeded.isNotEmpty()) {
                    seeded.forEach { remember(it) }
                    seeded
                } else {
                    fetchBatch(batchSize = 5)
                }

            if (items.isEmpty()) {
                exhausted = true
                NanoDjSession.shutdown()
                throw IllegalStateException(
                    "Nano DJ could not resolve any playable tracks from your taste profile.",
                )
            }

            Queue.Status(
                title = QUEUE_TITLE,
                items = items,
                mediaItemIndex = 0,
            )
        }

    override fun hasNextPage(): Boolean = !exhausted && pagesLoaded < MAX_PAGES

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (!hasNextPage()) return@withContext emptyList()
            val batch = fetchBatch(batchSize = 4)
            if (batch.isEmpty()) {
                exhausted = true
            }
            batch
        }

    private suspend fun fetchBatch(batchSize: Int): List<MediaItem> {
        pagesLoaded++
        val pick =
            NanoDjEngine.pickNext(
                context = currentContext(),
                batchSize = batchSize,
                enableNano = enableNano,
            )
        NanoDjSession.publish(pick.commentary, usedAi = pick.usedAi)

        val resolved = ArrayList<MediaItem>(batchSize)
        for (query in pick.queries) {
            if (resolved.size >= batchSize) break
            val item = searchFirstSong(query) ?: continue
            if (item.mediaId in playedIds) continue
            remember(item)
            resolved += item
        }

        // Soft fallback: related radio from last played if Nano/search under-delivered
        if (resolved.isEmpty() && playedIds.isNotEmpty()) {
            val seedId = playedIds.last()
            runCatching {
                val next = YouTube.next(com.metrolist.innertube.models.WatchEndpoint(videoId = seedId)).getOrNull()
                next?.items?.filterIsInstance<SongItem>()?.forEach { song ->
                    if (song.id !in playedIds && resolved.size < batchSize) {
                        val media = song.toMediaItem()
                        remember(media)
                        resolved += media
                    }
                }
            }.onFailure {
                Timber.tag(TAG).w(it, "Radio fallback failed")
            }
        }

        Timber.tag(TAG).i(
            "page=%d resolved=%d ai=%s",
            pagesLoaded,
            resolved.size,
            pick.usedAi,
        )
        return resolved
    }

    private suspend fun searchFirstSong(query: String): MediaItem? {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() ?: return null
        val song = result.items.filterIsInstance<SongItem>().firstOrNull() ?: return null
        return song.toMediaItem()
    }

    private fun remember(item: MediaItem) {
        playedIds += item.mediaId
        val title = item.mediaMetadata.title?.toString().orEmpty()
        if (title.isNotBlank()) {
            playedTitles.addLast(title)
            while (playedTitles.size > 30) playedTitles.removeFirst()
        }
    }

    private fun currentContext() =
        NanoDjEngine.DjContext(
            tasteSummary = tasteSummary,
            recentTitles = playedTitles.toList(),
            seedArtists = seedArtists,
            seedTracks = seedTracks,
            avoidTitles = playedTitles.toList(),
        )

    companion object {
        private const val TAG = "NanoDJ"
        const val QUEUE_TITLE = "Nano DJ"
        private const val MAX_PAGES = 40

        fun fromTaste(
            tasteSummary: String,
            seedArtists: List<String>,
            seedTracks: List<String>,
            enableNano: Boolean,
            seedMediaItems: List<MediaItem> = emptyList(),
        ): NanoDjQueue =
            NanoDjQueue(
                tasteSummary = tasteSummary,
                seedArtists = seedArtists,
                seedTracks = seedTracks,
                enableNano = enableNano,
                initialItems = seedMediaItems,
            )
    }
}
