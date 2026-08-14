/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import android.content.Context
import com.metrolist.music.constants.ListeningTasteActiveLaneKey
import com.metrolist.music.constants.ListeningTasteArtistsKey
import com.metrolist.music.constants.ListeningTasteCategoriesKey
import com.metrolist.music.constants.ListeningTasteExcludedSongIdsKey
import com.metrolist.music.constants.ListeningTasteLastUpdatedKey
import com.metrolist.music.constants.ListeningTasteListenCountKey
import com.metrolist.music.constants.ListeningTasteSummaryKey
import com.metrolist.music.constants.ListeningTasteTracksKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import androidx.datastore.preferences.core.Preferences
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Continuous on-device music taste learned from eligible local listens (DataStore only —
 * no Room schema changes). Gemini Nano may refresh a short summary / category tags;
 * otherwise heuristic updates apply.
 */
object ListeningTasteTracker {
    private const val TAG = "ListeningTaste"
    private const val MAX_ARTISTS = 40
    private const val MAX_TRACKS = 50
    private const val MAX_CATEGORIES = 12
    private const val DECAY = 0.985f
    private const val LISTEN_BOOST = 1f
    /** Stronger weight for bulk playlist/taste imports (e.g. Exportify CSV). */
    private const val IMPORT_BOOST = 2.5f
    /** Weight demoted from artists/categories when the user dislikes a song. */
    private const val DISLIKE_PENALTY = 1.25f
    private const val NANO_REFRESH_EVERY = 8
    /** Persist at most every N listens (in-memory cache fills the gaps). */
    private const val PERSIST_EVERY = 4
    private const val WEIGHT_SEP = '\t'

    private val mutex = Mutex()

    @Volatile
    private var memoryProfile: Profile? = null

    /** Monotonic generation so slower Nano refreshes cannot overwrite newer taste. */
    private val nanoGeneration = AtomicInteger(0)

    private suspend fun prefsSnapshot(context: Context): Preferences =
        context.dataStore.data.first()

    private fun <T> Preferences.getOr(key: Preferences.Key<T>, default: T): T =
        this[key] ?: default

    enum class DjLane(
        val id: String,
        val displayName: String,
        val searchHints: List<String>,
    ) {
        CHILL("chill", "chill", listOf("chill vibes", "lofi beats", "soft indie")),
        HYPE("hype", "hype", listOf("upbeat hits", "party anthems", "dance energy")),
        FOCUS("focus", "focus", listOf("focus instrumental", "study beats", "ambient focus")),
        NOSTALGIA("nostalgia", "nostalgia", listOf("throwback hits", "classic favorites", "nostalgia playlist")),
        ARTIST_RADIO("artist_radio", "artist radio", listOf("similar artists", "artist radio")),
        ;

        companion object {
            fun fromId(id: String?): DjLane =
                entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ARTIST_RADIO
        }
    }

    data class Profile(
        val artists: Map<String, Float> = emptyMap(),
        val tracks: Map<String, Float> = emptyMap(),
        val categories: Map<String, Float> = emptyMap(),
        val summary: String = "",
        val lastUpdatedMs: Long = 0L,
        val listenCount: Int = 0,
        val activeLane: DjLane = DjLane.ARTIST_RADIO,
        val excludedSongIds: Set<String> = emptySet(),
    ) {
        fun topArtists(n: Int = 15): List<String> =
            artists.entries.sortedByDescending { it.value }.take(n).map { it.key }

        fun topTracks(n: Int = 20): List<Pair<String, String>> =
            tracks.entries
                .sortedByDescending { it.value }
                .take(n)
                .map { (line, _) ->
                    val sep = when {
                        " — " in line -> " — "
                        " - " in line -> " - "
                        else -> null
                    }
                    if (sep != null) {
                        val idx = line.indexOf(sep)
                        line.substring(0, idx).trim() to line.substring(idx + sep.length).trim()
                    } else {
                        line to ""
                    }
                }

        fun topCategories(n: Int = 6): List<String> =
            categories.entries.sortedByDescending { it.value }.take(n).map { it.key }
    }

    /** Merged Spotify import + continuous listening signals for Metro DJ. */
    data class MergedTaste(
        val summary: String,
        val seedArtists: List<String>,
        val seedTracks: List<String>,
        val hints: List<String>,
        val categories: List<String>,
        val lane: DjLane,
    )

    /**
     * Bulk-seed continuous listening taste from an imported track list (Exportify CSV, etc.).
     * Returns the number of tracks applied. Persists immediately so Metro DJ sees new seeds.
     */
    suspend fun importFromTracks(
        context: Context,
        tracks: List<Pair<String, String>>,
        enableNano: Boolean = true,
        client: GeminiNanoClient = GeminiNanoClient.get(context),
    ): Int {
        val cleaned =
            tracks
                .map { (title, artist) -> title.trim() to artist.trim() }
                .filter { it.first.isNotBlank() }
        if (cleaned.isEmpty()) return 0

        if (memoryProfile == null) {
            loadProfile(context)
        }

        val updated =
            mutex.withLock {
                val profile = memoryProfile ?: Profile()

                var artistsNext = profile.artists
                var tracksNext = profile.tracks
                var categoriesNext = profile.categories
                var recentTitle = ""

                cleaned.forEach { (title, artistStr) ->
                    recentTitle = title
                    val artistNames =
                        artistStr
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                    artistsNext = decayAndBoost(artistsNext, artistNames, IMPORT_BOOST, MAX_ARTISTS)
                    val trackKey =
                        if (artistNames.isNotEmpty()) {
                            "$title — ${artistNames.joinToString(", ")}"
                        } else {
                            title
                        }
                    tracksNext = decayAndBoost(tracksNext, listOf(trackKey), IMPORT_BOOST, MAX_TRACKS)
                    categoriesNext =
                        decayAndBoost(
                            categoriesNext,
                            inferCategories(title, artistNames),
                            IMPORT_BOOST * 0.75f,
                            MAX_CATEGORIES,
                        )
                }

                val lane = pickLane(categoriesNext, artistsNext, recentTitle = recentTitle)
                // Always rebuild from imported seeds — never keep a stale/blank/"undefined" summary.
                val summary =
                    heuristicListeningSummary(artistsNext, tracksNext, categoriesNext, lane)

                Profile(
                    artists = artistsNext,
                    tracks = tracksNext,
                    categories = categoriesNext,
                    summary = summary,
                    lastUpdatedMs = System.currentTimeMillis(),
                    // Treat import as signal so merged taste uses the imported lane immediately.
                    listenCount = (profile.listenCount + cleaned.size).coerceAtLeast(1),
                    activeLane = lane,
                    excludedSongIds = profile.excludedSongIds,
                ).also { memoryProfile = it }
            }

        persistProfile(context, updated)

        Timber.tag(TAG).i(
            "importFromTracks applied %d tracks → %d artists, %d weighted tracks, lane=%s, summaryLen=%d",
            cleaned.size,
            updated.artists.size,
            updated.tracks.size,
            updated.activeLane.id,
            updated.summary.length,
        )

        if (enableNano) {
            val generation = nanoGeneration.incrementAndGet()
            val artistsForNano = updated.topArtists(12)
            val tracksForNano = updated.topTracks(15)
            val categoriesForNano = updated.topCategories(6)
            val refresh =
                refreshSummaryWithNano(
                    artists = artistsForNano,
                    tracks = tracksForNano,
                    categories = categoriesForNano,
                    lane = updated.activeLane,
                    client = client,
                )
            val usableNanoSummary = TasteSummary.sanitizeOrNull(refresh?.summary)
            if (refresh != null && usableNanoSummary != null && generation == nanoGeneration.get()) {
                mutex.withLock {
                    if (generation != nanoGeneration.get()) return@withLock
                    val base = memoryProfile ?: updated
                    val categoriesForStore =
                        if (refresh.categories.isNotEmpty()) {
                            mergeCategoryHints(base.categories, refresh.categories)
                        } else {
                            base.categories
                        }
                    val refreshed =
                        base.copy(
                            summary = usableNanoSummary,
                            categories = categoriesForStore,
                            lastUpdatedMs = System.currentTimeMillis(),
                        )
                    memoryProfile = refreshed
                    persistProfile(context, refreshed)
                }
            }
        }

        // Guarantee a usable summary landed in prefs (AI may have been skipped/rejected).
        val finalProfile = memoryProfile ?: updated
        if (!TasteSummary.isUsable(finalProfile.summary)) {
            val repaired =
                finalProfile.copy(
                    summary =
                        TasteSummary.fromArtistsAndTracks(
                            artists = finalProfile.topArtists(5),
                            tracks = finalProfile.topTracks(5),
                            sourceLabel = "imported playlist",
                        ),
                    lastUpdatedMs = System.currentTimeMillis(),
                )
            memoryProfile = repaired
            persistProfile(context, repaired)
        }

        return cleaned.size
    }

    suspend fun loadProfile(context: Context): Profile {
        memoryProfile?.let { return it }
        val prefs = prefsSnapshot(context)
        return Profile(
            artists = decodeWeights(prefs.getOr(ListeningTasteArtistsKey, "")),
            tracks = decodeWeights(prefs.getOr(ListeningTasteTracksKey, "")),
            categories = decodeWeights(prefs.getOr(ListeningTasteCategoriesKey, "")),
            summary = prefs.getOr(ListeningTasteSummaryKey, ""),
            lastUpdatedMs = prefs.getOr(ListeningTasteLastUpdatedKey, 0L),
            listenCount = prefs.getOr(ListeningTasteListenCountKey, 0),
            activeLane = DjLane.fromId(prefs.getOr(ListeningTasteActiveLaneKey, "")),
            excludedSongIds = prefs.getOr(ListeningTasteExcludedSongIdsKey, emptySet()),
        ).also { memoryProfile = it }
    }

    suspend fun isExcluded(context: Context, songId: String): Boolean {
        if (songId.isBlank()) return false
        memoryProfile?.let { return songId in it.excludedSongIds }
        val prefs = prefsSnapshot(context)
        return songId in prefs.getOr(ListeningTasteExcludedSongIdsKey, emptySet())
    }

    /** In-memory excluded IDs for DJ queue filtering (empty until loadProfile/setExcluded). */
    fun snapshotExcludedSongIds(): Set<String> = memoryProfile?.excludedSongIds.orEmpty()

    /**
     * Mark a song as disliked / excluded from Metro DJ taste and recommendations.
     * Persists under [ListeningTasteExcludedSongIdsKey] (same pref as "Don't use for taste").
     * When [excluded] is true and metadata is provided, demotes matching artists/categories
     * and drops the track from listening seeds so DJ treats it as bad taste.
     */
    suspend fun setExcluded(
        context: Context,
        songId: String,
        excluded: Boolean,
        title: String = "",
        artists: List<String> = emptyList(),
    ) {
        if (songId.isBlank()) return
        context.safeDataStoreEdit { prefs ->
            val current = prefs[ListeningTasteExcludedSongIdsKey]?.toMutableSet() ?: mutableSetOf()
            if (excluded) current += songId else current -= songId
            prefs[ListeningTasteExcludedSongIdsKey] = current
        }

        if (excluded && (title.isNotBlank() || artists.isNotEmpty())) {
            if (memoryProfile == null) {
                loadProfile(context)
            }
            val demoted =
                mutex.withLock {
                    val cached = memoryProfile ?: return@withLock null
                    val artistNames = artists.map { it.trim() }.filter { it.isNotBlank() }
                    val trackKey =
                        if (artistNames.isNotEmpty()) {
                            "$title — ${artistNames.joinToString(", ")}"
                        } else {
                            title
                        }
                    val tracksNext =
                        cached.tracks.filterKeys { key ->
                            key != trackKey &&
                                key != title &&
                                !key.startsWith("$title —") &&
                                !key.startsWith("$title -")
                        }
                    val artistsNext = demoteKeys(cached.artists, artistNames, DISLIKE_PENALTY)
                    val categoriesNext =
                        demoteKeys(
                            cached.categories,
                            inferCategories(title, artistNames),
                            DISLIKE_PENALTY * 0.5f,
                        )
                    cached
                        .copy(
                            artists = artistsNext,
                            tracks = tracksNext,
                            categories = categoriesNext,
                            excludedSongIds = cached.excludedSongIds + songId,
                            lastUpdatedMs = System.currentTimeMillis(),
                        ).also { memoryProfile = it }
                }
            if (demoted != null) {
                persistProfile(context, demoted)
            }
            return
        }

        mutex.withLock {
            val cached = memoryProfile ?: return@withLock
            memoryProfile =
                cached.copy(
                    excludedSongIds =
                        if (excluded) cached.excludedSongIds + songId
                        else cached.excludedSongIds - songId,
                )
        }
    }

    /**
     * Upsert taste after an eligible listen (≥ HistoryDuration, not pause-history, not excluded).
     * Call from [com.metrolist.music.playback.MusicService.onPlaybackStatsReady] on IO.
     *
     * Persists every [PERSIST_EVERY] listens (or on Nano refresh). Mutex is not held across
     * DataStore or Nano I/O. Stale Nano refreshes are dropped via [nanoGeneration].
     */
    suspend fun recordListen(
        context: Context,
        songId: String,
        title: String,
        artists: List<String>,
        enableNano: Boolean = true,
        /** Forces the expensive summary refresh after the listening-time interval elapses. */
        forceNanoRefresh: Boolean = false,
        client: GeminiNanoClient = GeminiNanoClient.get(context),
    ) {
        if (songId.isBlank() || title.isBlank()) return

        data class Pending(
            val profile: Profile,
            val artistsForNano: List<String>,
            val tracksForNano: List<Pair<String, String>>,
            val categoriesForNano: List<String>,
            val lane: DjLane,
            val generation: Int,
        )

        if (memoryProfile == null) {
            loadProfile(context)
        }

        val (pending, toPersist) =
            mutex.withLock {
                val profile = memoryProfile ?: return
                if (songId in profile.excludedSongIds) {
                    Timber.tag(TAG).d("Skipping excluded song %s", songId)
                    return
                }

                val artistNames = artists.map { it.trim() }.filter { it.isNotBlank() }
                val trackKey =
                    if (artistNames.isNotEmpty()) {
                        "$title — ${artistNames.joinToString(", ")}"
                    } else {
                        title
                    }

                val artistsNext = decayAndBoost(profile.artists, artistNames, LISTEN_BOOST, MAX_ARTISTS)
                val tracksNext = decayAndBoost(profile.tracks, listOf(trackKey), LISTEN_BOOST, MAX_TRACKS)
                val categoryHits = inferCategories(title, artistNames)
                val categoriesNext =
                    decayAndBoost(profile.categories, categoryHits, LISTEN_BOOST * 0.75f, MAX_CATEGORIES)

                val listenCount = profile.listenCount + 1
                val lane = pickLane(categoriesNext, artistsNext, recentTitle = title)

                var summary = TasteSummary.sanitizeOrNull(profile.summary).orEmpty()
                if (!TasteSummary.isUsable(summary)) {
                    summary = heuristicListeningSummary(artistsNext, tracksNext, categoriesNext, lane)
                }

                val shouldRefreshNano =
                    enableNano &&
                        (forceNanoRefresh || !TasteSummary.isUsable(profile.summary))

                val now = System.currentTimeMillis()
                val updated =
                    Profile(
                        artists = artistsNext,
                        tracks = tracksNext,
                        categories = categoriesNext,
                        summary = summary,
                        lastUpdatedMs = now,
                        listenCount = listenCount,
                        activeLane = lane,
                        excludedSongIds = profile.excludedSongIds,
                    )
                memoryProfile = updated

                val shouldPersist = shouldRefreshNano || listenCount % PERSIST_EVERY == 0

                Timber.tag(TAG).i(
                    "taste updated listens=%d lane=%s artists=%d tracks=%d persist=%s",
                    listenCount,
                    lane.id,
                    artistsNext.size,
                    tracksNext.size,
                    shouldPersist,
                )

                val pendingInner: Pending? =
                    if (!shouldRefreshNano) {
                        null
                    } else {
                        Pending(
                            profile = updated,
                            artistsForNano =
                                artistsNext.entries.sortedByDescending { it.value }.take(12).map { it.key },
                            tracksForNano =
                                tracksNext.entries.sortedByDescending { it.value }.take(15).map { (k, _) ->
                                    val sep = if (" — " in k) " — " else " - "
                                    if (sep in k) {
                                        k.substringBefore(sep).trim() to k.substringAfter(sep).trim()
                                    } else {
                                        k to ""
                                    }
                                },
                            categoriesForNano =
                                categoriesNext.entries.sortedByDescending { it.value }.take(6).map { it.key },
                            lane = lane,
                            generation = nanoGeneration.incrementAndGet(),
                        )
                    }
                pendingInner to (if (shouldPersist) updated else null)
            }

        toPersist?.let { persistProfile(context, it) }

        if (pending == null) return

        val refresh =
            refreshSummaryWithNano(
                artists = pending.artistsForNano,
                tracks = pending.tracksForNano,
                categories = pending.categoriesForNano,
                lane = pending.lane,
                client = client,
            ) ?: return

        if (pending.generation != nanoGeneration.get()) {
            Timber.tag(TAG).d("Dropping stale Nano taste refresh gen=%d", pending.generation)
            return
        }

        val refreshed: Profile
        mutex.withLock {
            if (pending.generation != nanoGeneration.get()) return
            val base = memoryProfile ?: pending.profile
            val categoriesForStore =
                if (refresh.categories.isNotEmpty()) {
                    mergeCategoryHints(base.categories, refresh.categories)
                } else {
                    base.categories
                }
            val usableSummary =
                TasteSummary.sanitizeOrNull(refresh.summary)
                    ?: TasteSummary.sanitizeOrNull(base.summary)
                    ?: heuristicListeningSummary(
                        base.artists,
                        base.tracks,
                        categoriesForStore,
                        base.activeLane,
                    )
            refreshed =
                base.copy(
                    summary = usableSummary,
                    categories = categoriesForStore,
                    lastUpdatedMs = System.currentTimeMillis(),
                )
            memoryProfile = refreshed
        }
        persistProfile(context, refreshed)
    }

    private suspend fun persistProfile(context: Context, profile: Profile) {
        val summary =
            TasteSummary.sanitizeOrNull(profile.summary)
                ?: TasteSummary.fromArtistsAndTracks(
                    artists = profile.topArtists(5),
                    tracks = profile.topTracks(5),
                    sourceLabel = "listening",
                ).takeIf { profile.artists.isNotEmpty() || profile.tracks.isNotEmpty() }
                ?: ""
        context.safeDataStoreEdit { prefs ->
            prefs[ListeningTasteArtistsKey] = encodeWeights(profile.artists)
            prefs[ListeningTasteTracksKey] = encodeWeights(profile.tracks)
            prefs[ListeningTasteCategoriesKey] = encodeWeights(profile.categories)
            prefs[ListeningTasteSummaryKey] = summary
            prefs[ListeningTasteLastUpdatedKey] = profile.lastUpdatedMs
            prefs[ListeningTasteListenCountKey] = profile.listenCount
            prefs[ListeningTasteActiveLaneKey] = profile.activeLane.id
        }
    }


    fun pickLane(
        categories: Map<String, Float>,
        artists: Map<String, Float>,
        recentTitle: String = "",
    ): DjLane {
        val topCat =
            categories.entries
                .filter { it.key != DjLane.ARTIST_RADIO.id }
                .maxByOrNull { it.value }
        val topArtistWeight = artists.values.maxOrNull() ?: 0f
        val secondArtist = artists.values.sortedDescending().getOrNull(1) ?: 0f

        // Strong single-artist lean → artist radio
        if (topArtistWeight >= 4f && topArtistWeight >= secondArtist * 1.8f && (topCat?.value ?: 0f) < topArtistWeight) {
            return DjLane.ARTIST_RADIO
        }

        val fromTitle = inferCategories(recentTitle, emptyList()).firstOrNull()
        if (fromTitle != null && (categories[fromTitle] ?: 0f) >= 1f) {
            return DjLane.fromId(fromTitle)
        }

        return when {
            topCat != null && topCat.value >= 1.5f -> DjLane.fromId(topCat.key)
            else -> DjLane.ARTIST_RADIO
        }
    }

    /** Change the active DJ lane without changing the Room schema or imported taste. */
    suspend fun setActiveLane(context: Context, lane: DjLane) {
        if (memoryProfile == null) loadProfile(context)
        val updated =
            mutex.withLock {
                val next = (memoryProfile ?: Profile()).copy(
                    activeLane = lane,
                    lastUpdatedMs = System.currentTimeMillis(),
                )
                memoryProfile = next
                next
            }
        persistProfile(context, updated)
    }

    /**
     * Merge continuous listening taste with optional Spotify import prefs for Metro DJ.
     */
    suspend fun loadMergedTaste(context: Context): MergedTaste {
        val profile = loadProfile(context)
        val prefs = prefsSnapshot(context)

        val spotifySummary = prefs.getOr(SpotifyTasteSummaryKey, "")
        val spotifyArtists =
            prefs.getOr(SpotifyTopArtistsKey, "")
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        val spotifyTracks =
            prefs.getOr(SpotifyTopTracksKey, "")
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        val spotifyHints =
            prefs.getOr(SpotifyTasteHintsKey, "")
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }

        val listeningArtists = profile.topArtists(15)
        val listeningTracks =
            profile.topTracks(20).map { (t, a) ->
                if (a.isNotBlank()) "$t - $a" else t
            }

        val seedArtists = (listeningArtists + spotifyArtists).distinct().take(20)
        val seedTracks = (listeningTracks + spotifyTracks).distinct().take(25)
        val categories = profile.topCategories(6)
        val hasTasteSignal =
            seedArtists.isNotEmpty() ||
                seedTracks.isNotEmpty() ||
                profile.artists.isNotEmpty() ||
                profile.tracks.isNotEmpty()
        val lane =
            when {
                hasTasteSignal &&
                    (profile.listenCount > 0 || profile.artists.isNotEmpty() || profile.categories.isNotEmpty()) ->
                    profile.activeLane.takeIf { profile.listenCount > 0 || profile.categories.isNotEmpty() }
                        ?: pickLane(profile.categories, profile.artists)
                else -> DjLane.ARTIST_RADIO
            }

        val live = TasteSummary.sanitizeOrNull(profile.summary)
        val spotify = TasteSummary.sanitizeOrNull(spotifySummary)
        val summary =
            when {
                // Prefer explicit import (CSV / Spotify taste) over listening heuristic when both exist.
                spotify != null && live != null && spotify != live ->
                    "$spotify Also refined by listening: ${live.take(180)}"
                spotify != null -> spotify
                live != null -> live
                seedArtists.isNotEmpty() ->
                    TasteSummary.fromArtistsAndTracks(
                        artists = seedArtists,
                        tracks =
                            seedTracks.map { line ->
                                when {
                                    " — " in line ->
                                        line.substringBefore(" — ").trim() to
                                            line.substringAfter(" — ").trim()
                                    " - " in line ->
                                        line.substringBefore(" - ").trim() to
                                            line.substringAfter(" - ").trim()
                                    else -> line to ""
                                }
                            },
                        sourceLabel = "listening",
                    )
                else -> ""
            }

        val laneHints =
            when (lane) {
                DjLane.ARTIST_RADIO ->
                    seedArtists.take(3).map { "$it radio" } + lane.searchHints
                else -> lane.searchHints
            }

        val hints =
            (categories.map { "$it mix" } + laneHints + spotifyHints + listeningTracks.take(5))
                .map { it.trim() }
                .filter { it.isNotBlank() && TasteSummary.isUsable(it) }
                .distinct()
                .take(12)

        return MergedTaste(
            summary = summary,
            seedArtists = seedArtists,
            seedTracks = seedTracks.ifEmpty { hints },
            hints = hints,
            categories = categories,
            lane = lane,
        )
    }

    internal fun inferCategories(title: String, artists: List<String>): List<String> {
        val hay = (title + " " + artists.joinToString(" ")).lowercase()
        val hits = linkedSetOf<String>()
        fun has(vararg words: String) = words.any { hay.contains(it) }

        if (has("chill", "lofi", "lo-fi", "ambient", "soft", "acoustic", "rain", "sleep", "calm")) {
            hits += DjLane.CHILL.id
        }
        if (has("hype", "party", "dance", "club", "remix", "banger", "trap", "bass", "energy", "workout")) {
            hits += DjLane.HYPE.id
        }
        if (has("focus", "study", "instrumental", "classical", "piano", "coding", "concentration")) {
            hits += DjLane.FOCUS.id
        }
        if (has("nostalgia", "throwback", "classic", "retro", "90s", "80s", "70s", "oldies", "vinyl")) {
            hits += DjLane.NOSTALGIA.id
        }
        return hits.toList()
    }

    internal fun encodeWeights(map: Map<String, Float>): String =
        map.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (k, v) ->
                "${k.replace('\n', ' ').replace(WEIGHT_SEP, ' ')}$WEIGHT_SEP${"%.2f".format(v)}"
            }

    internal fun decodeWeights(raw: String): Map<String, Float> {
        if (raw.isBlank()) return emptyMap()
        val out = linkedMapOf<String, Float>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val sep = trimmed.lastIndexOf(WEIGHT_SEP)
            if (sep <= 0) {
                out[trimmed] = (out[trimmed] ?: 0f) + 1f
                return@forEach
            }
            val key = trimmed.substring(0, sep).trim()
            val weight = trimmed.substring(sep + 1).trim().toFloatOrNull() ?: 1f
            if (key.isNotBlank()) out[key] = weight
        }
        return out
    }

    private fun decayAndBoost(
        current: Map<String, Float>,
        keys: List<String>,
        boost: Float,
        maxEntries: Int,
    ): Map<String, Float> {
        val next = current.mapValues { it.value * DECAY }.toMutableMap()
        keys.forEach { key ->
            if (key.isBlank()) return@forEach
            next[key] = (next[key] ?: 0f) + boost
        }
        return next.entries
            .sortedByDescending { it.value }
            .take(maxEntries)
            .associate { it.key to it.value }
    }

    private fun demoteKeys(
        current: Map<String, Float>,
        keys: List<String>,
        penalty: Float,
    ): Map<String, Float> {
        if (keys.isEmpty()) return current
        val next = current.toMutableMap()
        keys.forEach { key ->
            if (key.isBlank()) return@forEach
            val nextWeight = (next[key] ?: 0f) - penalty
            if (nextWeight <= 0.05f) next.remove(key) else next[key] = nextWeight
        }
        return next.entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
    }

    private fun mergeCategoryHints(
        current: Map<String, Float>,
        hints: List<String>,
    ): Map<String, Float> {
        val next = current.toMutableMap()
        hints.forEach { raw ->
            val id = normalizeCategory(raw) ?: return@forEach
            next[id] = (next[id] ?: 0f) + 0.5f
        }
        return next.entries
            .sortedByDescending { it.value }
            .take(MAX_CATEGORIES)
            .associate { it.key to it.value }
    }

    private fun normalizeCategory(raw: String): String? {
        val t = raw.trim().lowercase()
        if (t.isBlank()) return null
        DjLane.entries.forEach { lane ->
            if (t == lane.id || t.contains(lane.displayName)) return lane.id
        }
        return when {
            t.contains("chill") || t.contains("mellow") || t.contains("relax") -> DjLane.CHILL.id
            t.contains("hype") || t.contains("upbeat") || t.contains("party") -> DjLane.HYPE.id
            t.contains("focus") || t.contains("study") || t.contains("work") -> DjLane.FOCUS.id
            t.contains("nostalgia") || t.contains("throwback") || t.contains("classic") -> DjLane.NOSTALGIA.id
            t.contains("artist") -> DjLane.ARTIST_RADIO.id
            else -> t.take(24).replace(' ', '_')
        }
    }

    private fun heuristicListeningSummary(
        artists: Map<String, Float>,
        tracks: Map<String, Float>,
        categories: Map<String, Float>,
        lane: DjLane,
    ): String {
        val topA = artists.entries.sortedByDescending { it.value }.take(4).map { it.key }
        val topC = categories.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val topT = tracks.entries.sortedByDescending { it.value }.take(3).map { it.key.substringBefore(" — ") }
        return buildString {
            append("Live listening taste")
            if (topA.isNotEmpty()) append(" leans toward ${topA.joinToString(", ")}")
            if (topC.isNotEmpty()) append(", with ${topC.joinToString("/")} energy")
            append(". Current Metro DJ lane: ${lane.displayName}")
            if (topT.isNotEmpty()) append(" — recent favorites include ${topT.joinToString(", ")}")
            append(".")
        }
    }

    private data class NanoRefresh(val summary: String, val categories: List<String>)

    private suspend fun refreshSummaryWithNano(
        artists: List<String>,
        tracks: List<Pair<String, String>>,
        categories: List<String>,
        lane: DjLane,
        client: GeminiNanoClient,
    ): NanoRefresh? {
        val status = runCatching { client.checkStatus() }.getOrDefault(GeminiNanoStatus.Unavailable)
        if (status != GeminiNanoStatus.Available) {
            return NanoRefresh(
                summary = heuristicListeningSummary(
                    artists.associateWith { 1f },
                    tracks.associate { (t, a) -> (if (a.isNotBlank()) "$t — $a" else t) to 1f },
                    categories.associateWith { 1f },
                    lane,
                ),
                categories = categories,
            ).takeIf { artists.isNotEmpty() || tracks.isNotEmpty() }
        }

        val prompt =
            """
            You are Metro DJ's on-device taste tracker (Gemini Nano). Update the listener's live
            music taste from recent plays. Reply with EXACTLY this format (no markdown):
            SUMMARY: <2 short sentences for a DJ host>
            CATEGORIES:
            - <mood tag>
            - <mood tag>
            (2-5 CATEGORIES from: chill, hype, focus, nostalgia, artist_radio)

            Top artists: ${artists.take(12).joinToString(", ").ifBlank { "(none)" }}
            Top tracks: ${tracks.take(15).joinToString("; ") { (t, a) -> if (a.isNotBlank()) "$t by $a" else t }.ifBlank { "(none)" }}
            Current category weights: ${categories.joinToString(", ").ifBlank { "(none)" }}
            Active lane hint: ${lane.id}
            """.trimIndent()

        val raw = runCatching { client.generateContent(prompt) }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null

        val summaryRaw =
            Regex("""(?im)^SUMMARY:\s*(.+)$""").find(raw)?.groupValues?.getOrNull(1)?.trim()
                ?: raw.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val summary = TasteSummary.sanitizeOrNull(summaryRaw)?.take(500) ?: return null
        val cats =
            Regex("""(?im)^[-*]\s*(.+)$""")
                .findAll(raw)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() && !it.equals("CATEGORIES:", ignoreCase = true) }
                .mapNotNull { normalizeCategory(it) }
                .distinct()
                .take(5)
                .toList()

        return NanoRefresh(summary = summary, categories = cats)
    }

    /** Overwrite the live listening summary (e.g. after CSV import guaranteed a blurb). */
    suspend fun forceSummary(context: Context, summary: String) {
        val usable =
            TasteSummary.sanitizeOrNull(summary)
                ?: return
        if (memoryProfile == null) loadProfile(context)
        mutex.withLock {
            val base = memoryProfile ?: Profile()
            val next =
                base.copy(
                    summary = usable,
                    lastUpdatedMs = System.currentTimeMillis(),
                )
            memoryProfile = next
            // Persist outside lock below
        }
        val toPersist = memoryProfile ?: return
        persistProfile(context, toPersist.copy(summary = usable))
    }

    /** Clears listening-derived taste signals. Keeps exclusions and imported Spotify taste prefs. */
    suspend fun reset(context: Context) {
        memoryProfile = null
        nanoGeneration.set(0)
        context.safeDataStoreEdit { prefs ->
            prefs[ListeningTasteArtistsKey] = ""
            prefs[ListeningTasteTracksKey] = ""
            prefs[ListeningTasteCategoriesKey] = ""
            prefs[ListeningTasteSummaryKey] = ""
            prefs[ListeningTasteLastUpdatedKey] = 0L
            prefs[ListeningTasteListenCountKey] = 0
            prefs[ListeningTasteActiveLaneKey] = ""
        }
    }

    /** Test helper: drop in-memory cache so the next load reads DataStore. */
    fun clearMemoryCacheForTests() {
        memoryProfile = null
        nanoGeneration.set(0)
    }
}
