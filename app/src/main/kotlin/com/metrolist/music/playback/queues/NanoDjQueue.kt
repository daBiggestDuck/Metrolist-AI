/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.ai.GeminiNanoClient
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.NanoDjEngine
import com.metrolist.music.ai.NanoDjSession
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber
import kotlin.random.Random

/**
 * Continuous radio queue curated by Metro DJ (Spotify DJ replacement).
 * Each page asks the configured DJ AI backend for the next batch of song queries in the
 * active mood/category lane, then resolves them on YouTube Music.
 */
class NanoDjQueue(
    private val tasteSummary: String,
    private val seedArtists: List<String>,
    private val seedTracks: List<String>,
    private val enableNano: Boolean,
    private val client: GeminiNanoClient,
    private val initialItems: List<MediaItem> = emptyList(),
    private val categories: List<String> = emptyList(),
    private val lane: ListeningTasteTracker.DjLane = ListeningTasteTracker.DjLane.ARTIST_RADIO,
    private val excludedSongIds: Set<String> = emptySet(),
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private val excludedIds = ConcurrentHashMap.newKeySet<String>().apply { addAll(excludedSongIds) }
    private val playedIds = LinkedHashSet<String>()
    private val playedTitles = ArrayDeque<String>()
    private val titleById = HashMap<String, String>()
    private val skippedTitles = ArrayDeque<String>()
    private var skipPressure = 0
    private var exhausted = false
    private var pagesLoaded = 0
    private val sessionSeed = System.nanoTime()
    private val random = Random(sessionSeed)

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            // A new queue is a new DJ session: clear stale commentary and pending speech before
            // composing the opening line, otherwise replacing a queue can replay the old block.
            NanoDjSession.stop()
            val opening =
                NanoDjEngine.openingLine(
                    context = currentContext(),
                    enableNano = enableNano,
                    client = client,
                )
            NanoDjSession.start(opening, usedAi = enableNano)

            // Use at most one taste seed and fill the rest from a newly shuffled category
            // block. Returning the same imported favorites first made every DJ session identical.
            val seeded =
                initialItems
                    .filterNot { it.mediaId in playedIds || it.mediaId in excludedIds }
                    .shuffled(random)
                    .take(1)
            seeded.forEach { remember(it) }
            val generated = fetchBatch(batchSize = 5 - seeded.size)
            val items = (seeded + generated).distinctBy { it.mediaId }

            if (items.isEmpty()) {
                exhausted = true
                NanoDjSession.shutdown()
                throw IllegalStateException(
                    "Metro DJ could not resolve any playable tracks from your taste profile.",
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
            val batch = fetchBatch(batchSize = 6)
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
                client = client,
            )

        val resolved = ArrayList<MediaItem>(batchSize)
        for (query in pick.queries) {
            if (resolved.size >= batchSize) break
            val item = searchFirstSong(query) ?: continue
            if (item.mediaId in playedIds || item.mediaId in excludedIds) continue
            remember(item)
            resolved += item
        }

        if (resolved.isEmpty() && playedIds.isNotEmpty()) {
            val seedId = playedIds.last()
            runCatching {
                val next = YouTube.next(com.metrolist.innertube.models.WatchEndpoint(videoId = seedId)).getOrNull()
                next?.items?.filterIsInstance<SongItem>()?.forEach { song ->
                    if (
                        song.id !in playedIds &&
                        song.id !in excludedIds &&
                        resolved.size < batchSize
                    ) {
                        val media = song.toMediaItem()
                        remember(media)
                        resolved += media
                    }
                }
            }.onFailure {
                Timber.tag(TAG).w(it, "Radio fallback failed")
            }
        }

        NanoDjSession.publish(
            pick.commentary,
            usedAi = pick.usedAi,
            transitionMediaId = resolved.firstOrNull()?.mediaId,
        )
        if (skipPressure >= 2 && resolved.isNotEmpty()) {
            // The changed block has acknowledged the feedback; future skips can build pressure
            // again instead of permanently forcing discovery mode.
            skipPressure = 0
        }

        Timber.tag(TAG).i(
            "page=%d lane=%s resolved=%d ai=%s",
            pagesLoaded,
            lane.id,
            resolved.size,
            pick.usedAi,
        )
        return resolved
    }

    /** Exclude a song after the listener presses dislike during this DJ session. */
    fun excludeSong(songId: String) {
        if (songId.isNotBlank()) excludedIds += songId
    }

    /** Records an explicit skip so the next block can deliberately change its angle. */
    fun recordSkip(songId: String?) {
        if (songId.isNullOrBlank()) return
        skipPressure++
        titleById[songId]?.let {
            skippedTitles.addLast(it)
            while (skippedTitles.size > 12) skippedTitles.removeFirst()
        }
    }

    private suspend fun searchFirstSong(query: String): MediaItem? {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() ?: return null
        val song = result.items.filterIsInstance<SongItem>().firstOrNull() ?: return null
        return song.toMediaItem()
    }

    private fun remember(item: MediaItem) {
        playedIds += item.mediaId
        val title = item.mediaMetadata.title?.toString().orEmpty()
        titleById[item.mediaId] = title
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
            avoidTitles = (playedTitles + skippedTitles).toList(),
            categories = categories,
            lane = lane,
            skipPressure = skipPressure,
            randomSeed = sessionSeed,
        )

    companion object {
        private const val TAG = "NanoDJ"
        const val QUEUE_TITLE = "Metro DJ"
        private const val MAX_PAGES = 40

        fun fromTaste(
            tasteSummary: String,
            seedArtists: List<String>,
            seedTracks: List<String>,
            enableNano: Boolean,
            client: GeminiNanoClient,
            seedMediaItems: List<MediaItem> = emptyList(),
            categories: List<String> = emptyList(),
            lane: ListeningTasteTracker.DjLane = ListeningTasteTracker.DjLane.ARTIST_RADIO,
            excludedSongIds: Set<String> = emptySet(),
        ): NanoDjQueue =
            NanoDjQueue(
                tasteSummary = tasteSummary,
                seedArtists = seedArtists,
                seedTracks = seedTracks,
                enableNano = enableNano,
                client = client,
                initialItems = seedMediaItems,
                categories = categories,
                lane = lane,
                excludedSongIds = excludedSongIds,
            )
    }
}
