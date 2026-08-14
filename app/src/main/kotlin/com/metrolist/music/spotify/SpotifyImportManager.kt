/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.ai.GeminiNanoClient
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.TasteAnalysisResult
import com.metrolist.music.ai.TasteImportAi
import com.metrolist.music.ai.TasteSummary
import com.metrolist.music.ai.analyzeSpotifyTaste
import com.metrolist.music.ai.heuristicTasteAnalysis
import com.metrolist.music.constants.ListeningTasteSummaryKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.R
import com.metrolist.music.utils.CsvTasteImportHelper
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

data class SpotifyImportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val matched: Int = 0,
    val failed: Int = 0,
    val currentTitle: String = "",
    val phase: String = "",
)

data class SpotifyImportResult(
    val playlistId: String,
    val playlistName: String,
    val matched: Int,
    val failed: Int,
    val tasteAnalysis: TasteAnalysisResult? = null,
    val topArtists: List<String> = emptyList(),
    val topTracks: List<Pair<String, String>> = emptyList(),
)

/**
 * Snapshot of a listener's Spotify taste: top artists/tracks plus the derived analysis.
 * Returned by [SpotifyImportManager.refreshTasteProfile] so the UI can persist and display it
 * without necessarily building a playlist.
 */
data class SpotifyTasteProfile(
    val topArtists: List<String>,
    val topTracks: List<Pair<String, String>>,
    val analysis: TasteAnalysisResult,
)

class SpotifyImportManager(
    private val database: MusicDatabase,
    private val appContext: Context,
    private val api: SpotifyApi = SpotifyApi(),
) {
    private fun djClient(): GeminiNanoClient = GeminiNanoClient.get(appContext)
    suspend fun ensureValidToken(clientId: String): String {
        val current = SpotifyTokenStore.retrieve()
        if (!current.isNullOrBlank() && !SpotifyTokenStore.isExpired()) {
            return current
        }
        val refresh = SpotifyTokenStore.getRefreshToken()
        if (refresh.isNullOrBlank() || clientId.isBlank()) {
            throw SpotifyAuthException.InvalidGrant("Not connected to Spotify")
        }
        val auth = SpotifyAuth()
        try {
            val result = auth.refresh(clientId, refresh)
            SpotifyTokenStore.storeFull(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken.ifBlank { refresh },
                expiresInSec = result.expiresInSec,
            )
            return result.accessToken
        } finally {
            auth.close()
        }
    }

    suspend fun importTaste(
        clientId: String,
        enableGeminiNano: Boolean,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyImportResult =
        withContext(Dispatchers.IO) {
            val token = ensureValidToken(clientId)
            onProgress(SpotifyImportProgress(phase = "Fetching top artists & tracks"))
            val artists = api.getTopArtists(token, timeRange = "medium_term", limit = 20)
            val tracks = api.getTopTracks(token, timeRange = "medium_term", limit = 50)

            onProgress(SpotifyImportProgress(phase = "Analyzing taste", total = tracks.size))
            val trackPairs = tracks.map { it.name to it.artistsJoined }
            val artistNames = artists.map { it.name }
            // Persist listening + Spotify taste prefs via the same AI path as CSV imports.
            val taste =
                CsvTasteImportHelper.importTaste(
                    context = appContext,
                    database = database,
                    tracks = trackPairs,
                    enableNano = enableGeminiNano,
                )
            val analysis =
                TasteAnalysisResult(
                    summary = taste.summary,
                    searchHints =
                        appContext.dataStore.data.first()[SpotifyTasteHintsKey]
                            ?.lines()
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            .orEmpty()
                            .ifEmpty { heuristicTasteAnalysis(artistNames, trackPairs).searchHints },
                    usedAi = taste.usedAi,
                )

            val matchResult =
                matchAndCreatePlaylist(
                    playlistName = TASTE_PLAYLIST_NAME,
                    tracks = tracks,
                    onProgress = onProgress,
                )

            val hintQueries = analysis.searchHints.take(10)
            if (hintQueries.isNotEmpty()) {
                matchQueriesIntoPlaylist(
                    playlistId = matchResult.playlistId,
                    startingSongCount = matchResult.matched,
                    queries = hintQueries,
                    onProgress = onProgress,
                )
            }

            matchResult.copy(
                tasteAnalysis = analysis,
                topArtists = artistNames,
                topTracks = trackPairs,
            )
        }

    /**
     * Analyze title/artist pairs into a [SpotifyTasteProfile] without matching or creating a
     * playlist. Used when the tracks already live in Metrolist (local playlist → taste).
     */
    suspend fun buildTasteFromTracks(
        tracks: List<Pair<String, String>>,
        enableNano: Boolean,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyTasteProfile =
        withContext(Dispatchers.IO) {
            val cleaned = cleanTracks(tracks)
            if (cleaned.isEmpty()) {
                throw IllegalArgumentException("No tracks to analyze")
            }
            onProgress(SpotifyImportProgress(phase = "Analyzing taste with DJ AI", total = cleaned.size))
            val taste =
                CsvTasteImportHelper.importTaste(
                    context = appContext,
                    database = database,
                    tracks = cleaned,
                    enableNano = enableNano,
                )
            onProgress(
                SpotifyImportProgress(
                    current = cleaned.size,
                    total = cleaned.size,
                    phase = appContext.getString(R.string.spotify_taste_profile_saved),
                ),
            )

            val topArtists = deriveTopArtists(cleaned)
            val topTracks = cleaned.take(50)
            val hints =
                appContext.dataStore.data.first()[SpotifyTasteHintsKey]
                    ?.lines()
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            onProgress(
                SpotifyImportProgress(
                    phase = "Done",
                    current = cleaned.size,
                    total = cleaned.size,
                ),
            )
            SpotifyTasteProfile(
                topArtists = topArtists,
                topTracks = topTracks,
                analysis =
                    TasteAnalysisResult(
                        summary = taste.summary,
                        searchHints = hints,
                        usedAi = taste.usedAi,
                    ),
            )
        }

    /**
     * Builds Metro DJ taste from an in-app Metrolist playlist (local or bookmarked YTM).
     * Does not rematch or clone the playlist — songs are already in the library.
     */
    suspend fun buildTasteFromLocalPlaylist(
        playlistId: String,
        enableNano: Boolean,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyTasteProfile =
        withContext(Dispatchers.IO) {
            onProgress(SpotifyImportProgress(phase = "Reading playlist"))
            val songs = database.playlistSongsBlocking(playlistId)
            val tracks =
                songs.map { ps ->
                    val title = ps.song.title
                    val artist =
                        ps.song.orderedArtists.joinToString(", ") { it.name }
                    title to artist
                }
            if (tracks.isEmpty()) {
                throw IllegalArgumentException("Playlist has no songs")
            }
            buildTasteFromTracks(tracks, enableNano, onProgress)
        }

    /**
     * Fetches a Spotify playlist’s tracks (OAuth) and analyzes taste without requiring a rematch
     * pass first. Still useful when connected; Premium/allowlist may apply.
     */
    suspend fun buildTasteFromSpotifyPlaylist(
        clientId: String,
        playlist: SpotifyPlaylistSummary,
        enableNano: Boolean,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyTasteProfile =
        withContext(Dispatchers.IO) {
            val token = ensureValidToken(clientId)
            onProgress(SpotifyImportProgress(phase = "Fetching playlist tracks"))
            val tracks =
                api.getPlaylistTracks(token, playlist.id)
                    .map { it.name to it.artistsJoined }
            buildTasteFromTracks(tracks, enableNano, onProgress)
        }

    /**
     * Premium-free path: analyze pasted/picked track list for Metro DJ taste, then build a local
     * YouTube Music–matched playlist. Does not call the Spotify Web API.
     */
    suspend fun importTasteFromTracks(
        tracks: List<Pair<String, String>>,
        playlistName: String = TASTE_PLAYLIST_NAME,
        enableNano: Boolean,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyImportResult =
        withContext(Dispatchers.IO) {
            val cleaned = cleanTracks(tracks)
            if (cleaned.isEmpty()) {
                throw IllegalArgumentException("No tracks to import")
            }

            // Persist listening + Spotify taste prefs FIRST so Exportify imports succeed even
            // when YouTube matching later fails or times out. A file import remains useful when
            // the optional DJ provider is unavailable, so the helper may use a deterministic
            // artist/track summary for this path.
            onProgress(SpotifyImportProgress(phase = "Analyzing taste with DJ AI", total = cleaned.size))
            val taste =
                CsvTasteImportHelper.importTaste(
                    context = appContext,
                    database = database,
                    tracks = cleaned,
                    enableNano = enableNano,
                    allowHeuristicFallback = true,
                )

            onProgress(
                SpotifyImportProgress(
                    current = cleaned.size,
                    total = cleaned.size,
                    phase = appContext.getString(R.string.spotify_taste_profile_saved),
                ),
            )

            val topArtists = deriveTopArtists(cleaned)
            val topTracks = cleaned.take(50)
            val hints =
                appContext.dataStore.data.first()[SpotifyTasteHintsKey]
                    ?.lines()
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                    .ifEmpty { heuristicTasteAnalysis(topArtists, topTracks).searchHints }
            val analysis =
                TasteAnalysisResult(
                    summary = taste.summary,
                    searchHints = hints,
                    usedAi = taste.usedAi,
                )

            val spotifyTracks =
                cleaned.mapIndexed { index, (title, artist) ->
                    SpotifyTrack(
                        id = "file-$index",
                        name = title,
                        artists = splitArtistNames(artist),
                    )
                }

            val name = playlistName.trim().ifBlank { TASTE_PLAYLIST_NAME }
            val matchResult =
                runCatching {
                    matchAndCreatePlaylist(
                        playlistName = name,
                        tracks = spotifyTracks,
                        onProgress = onProgress,
                    )
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "YTM match failed after taste save; taste still imported")
                }.getOrElse {
                    SpotifyImportResult(
                        playlistId = "",
                        playlistName = name,
                        matched = 0,
                        failed = cleaned.size,
                        tasteAnalysis = analysis,
                        topArtists = topArtists,
                        topTracks = topTracks,
                    )
                }

            if (matchResult.playlistId.isNotBlank()) {
                val hintQueries = analysis.searchHints.take(10)
                if (hintQueries.isNotEmpty()) {
                    runCatching {
                        matchQueriesIntoPlaylist(
                            playlistId = matchResult.playlistId,
                            startingSongCount = matchResult.matched,
                            queries = hintQueries,
                            onProgress = onProgress,
                        )
                    }
                }
            }

            matchResult.copy(
                tasteAnalysis = analysis,
                topArtists = topArtists,
                topTracks = topTracks,
            )
        }

    private fun cleanTracks(tracks: List<Pair<String, String>>): List<Pair<String, String>> =
        tracks
            .map { (t, a) -> t.trim() to a.trim() }
            .filter { it.first.isNotBlank() }
            .distinctBy { "${it.first.lowercase()}|${it.second.lowercase()}" }

    /**
     * Fetches top artists/tracks and analyzes taste (Gemini Nano or heuristic) without creating
     * any playlist. Lets the taste screen refresh its analysis on demand.
     */
    suspend fun refreshTasteProfile(
        clientId: String,
        enableGeminiNano: Boolean,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyTasteProfile =
        withContext(Dispatchers.IO) {
            val token = ensureValidToken(clientId)
            onProgress(SpotifyImportProgress(phase = "Fetching top artists & tracks"))
            val artists = api.getTopArtists(token, timeRange = "medium_term", limit = 20)
            val tracks = api.getTopTracks(token, timeRange = "medium_term", limit = 50)

            onProgress(SpotifyImportProgress(phase = "Analyzing taste", total = tracks.size))
            val analysis =
                analyzeSpotifyTaste(
                    context = appContext,
                    topArtists = artists.map { it.name },
                    topTracks = tracks.map { it.name to it.artistsJoined },
                    enableNano = enableGeminiNano,
                    client = djClient(),
                )
            onProgress(
                SpotifyImportProgress(
                    phase = "Done",
                    current = tracks.size,
                    total = tracks.size,
                ),
            )
            SpotifyTasteProfile(
                topArtists = artists.map { it.name },
                topTracks = tracks.map { it.name to it.artistsJoined },
                analysis = analysis,
            )
        }

    /**
     * Builds a local “Metro DJ Recommendations” playlist from the **saved taste summary**.
     *
     * Dumb path: load saved SUMMARY → ask DJ AI for HINTS → match on YTM.
     * Does **not** prefer live Spotify tops over imported taste.
     */
    suspend fun generateRecommendations(
        clientId: String,
        enableGeminiNano: Boolean,
        cachedSummary: String = "",
        cachedHints: List<String> = emptyList(),
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyImportResult =
        withContext(Dispatchers.IO) {
            onProgress(SpotifyImportProgress(phase = "Loading saved taste"))

            val prefs = appContext.dataStore.data.first()
            val merged = ListeningTasteTracker.loadMergedTaste(appContext)
            val tasteSummary =
                TasteSummary.sanitizeOrNull(cachedSummary)
                    ?: TasteSummary.sanitizeOrNull(prefs[SpotifyTasteSummaryKey])
                    ?: TasteSummary.sanitizeOrNull(prefs[ListeningTasteSummaryKey])
                    ?: TasteSummary.sanitizeOrNull(merged.summary)
                    ?: throw IllegalStateException(
                        "No saved taste summary. Import an Exportify CSV or build taste first.",
                    )

            val seedArtists =
                merged.seedArtists.ifEmpty {
                    prefs[SpotifyTopArtistsKey]
                        ?.split('\n')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }
            val seedTracks =
                merged.seedTracks.ifEmpty {
                    prefs[SpotifyTopTracksKey]
                        ?.split('\n')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }

            // Keep clientId referenced so call sites stay compatible (OAuth optional).
            @Suppress("UNUSED_VARIABLE")
            val ignoredClientId = clientId

            onProgress(SpotifyImportProgress(phase = "Asking DJ AI from your saved taste"))
            val aiHints =
                if (enableGeminiNano) {
                    TasteImportAi.recommendFromSavedTaste(
                        context = appContext,
                        savedSummary = tasteSummary,
                        client = djClient(),
                    )
                } else {
                    emptyList()
                }

            val fallbackHints =
                (
                    cachedHints +
                        merged.hints +
                        (
                            prefs[SpotifyTasteHintsKey]
                                ?.split('\n')
                                ?.map { it.trim() }
                                .orEmpty()
                        ) +
                        seedTracks.take(8) +
                        seedArtists.take(5).map { "$it songs" }
                ).map { it.trim() }
                    .filter { it.isNotBlank() && TasteSummary.isUsable(it) }
                    .distinct()

            val queries =
                (aiHints + fallbackHints)
                    .map { it.trim() }
                    .filter { it.isNotBlank() && TasteSummary.isUsable(it) }
                    .distinct()
                    .take(20)

            if (queries.isEmpty()) {
                throw IllegalStateException(
                    "No recommendation queries available. Import an Exportify CSV or build taste first.",
                )
            }

            // Persist fresh AI hints so the taste screen / next run re-reads them.
            if (aiHints.isNotEmpty()) {
                appContext.safeDataStoreEdit { edit ->
                    edit[SpotifyTasteHintsKey] = aiHints.joinToString("\n")
                }
            }

            val matchResult =
                matchQueriesAsPlaylist(
                    playlistName = RECOMMENDATIONS_PLAYLIST_NAME,
                    queries = queries,
                    onProgress = onProgress,
                )

            val trackPairs =
                seedTracks.map { line ->
                    when {
                        " — " in line ->
                            line.substringBefore(" — ").trim() to line.substringAfter(" — ").trim()
                        " - " in line ->
                            line.substringBefore(" - ").trim() to line.substringAfter(" - ").trim()
                        else -> line to ""
                    }
                }

            matchResult.copy(
                tasteAnalysis =
                    TasteAnalysisResult(
                        summary = tasteSummary,
                        searchHints = queries,
                        usedAi = aiHints.isNotEmpty(),
                    ),
                topArtists = seedArtists,
                topTracks = trackPairs,
            )
        }

    private suspend fun matchQueriesAsPlaylist(
        playlistName: String,
        queries: List<String>,
        onProgress: (SpotifyImportProgress) -> Unit,
    ): SpotifyImportResult {
        // Reuse a single local recommendations playlist instead of spawning duplicates.
        val localPlaylists =
            database.playlistEntitiesByNameAsc().filter { it.isEditable && it.isLocal }
        // Prefer the new name if both names exist; this avoids creating or mutating an
        // unexpected user playlist after a partially completed migration.
        val existing =
            localPlaylists.firstOrNull { it.name.equals(playlistName, ignoreCase = true) }
                ?: localPlaylists.firstOrNull {
                    it.name.equals(LEGACY_RECOMMENDATIONS_PLAYLIST_NAME, ignoreCase = true)
                }
        val entity =
            if (existing != null) {
                // Migrate the old visible name in place so existing installs do not gain a
                // duplicate playlist when the Metro DJ branding changes.
                val migrated =
                    if (existing.name.equals(LEGACY_RECOMMENDATIONS_PLAYLIST_NAME, ignoreCase = true)) {
                        existing.copy(name = playlistName)
                    } else {
                        existing
                    }
                database.transaction {
                    if (migrated !== existing) update(migrated)
                    clearPlaylist(migrated.id)
                    updatePlaylistLastUpdated(migrated.id)
                }
                migrated
            } else {
                PlaylistEntity(
                    name = playlistName,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                    isLocal = true,
                ).also { database.insert(it) }
            }
        var matched = 0
        var failed = 0
        queries.forEachIndexed { index, query ->
            onProgress(
                SpotifyImportProgress(
                    current = index + 1,
                    total = queries.size,
                    matched = matched,
                    failed = failed,
                    currentTitle = query,
                    phase = "Matching recommendations",
                ),
            )
            val playlist =
                Playlist(
                    playlist = entity,
                    songCount = matched,
                    songThumbnails = emptyList(),
                )
            if (matchAndInsert(query, playlist)) matched++ else failed++
        }
        onProgress(
            SpotifyImportProgress(
                current = queries.size,
                total = queries.size,
                matched = matched,
                failed = failed,
                phase = "Done",
            ),
        )
        return SpotifyImportResult(
            playlistId = entity.id,
            playlistName = playlistName,
            matched = matched,
            failed = failed,
        )
    }

    suspend fun importPlaylist(
        clientId: String,
        playlist: SpotifyPlaylistSummary,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): SpotifyImportResult =
        withContext(Dispatchers.IO) {
            val token = ensureValidToken(clientId)
            onProgress(SpotifyImportProgress(phase = "Fetching playlist tracks"))
            val tracks = api.getPlaylistTracks(token, playlist.id)
            matchAndCreatePlaylist(
                playlistName = playlist.name,
                tracks = tracks,
                onProgress = onProgress,
            )
        }

    private suspend fun matchAndCreatePlaylist(
        playlistName: String,
        tracks: List<SpotifyTrack>,
        onProgress: (SpotifyImportProgress) -> Unit,
    ): SpotifyImportResult {
        val entity =
            PlaylistEntity(
                name = playlistName,
                bookmarkedAt = LocalDateTime.now(),
                isEditable = true,
                isLocal = true,
            )
        database.insert(entity)

        var songCount = 0
        var matched = 0
        var failed = 0
        val total = tracks.size

        tracks.forEachIndexed { index, track ->
            val query =
                if (track.artists.isEmpty()) {
                    track.name
                } else {
                    "${track.name} - ${track.artistsJoined}"
                }
            onProgress(
                SpotifyImportProgress(
                    current = index,
                    total = total,
                    matched = matched,
                    failed = failed,
                    currentTitle = track.name,
                    phase = "Matching",
                ),
            )
            val playlist =
                Playlist(
                    playlist = entity,
                    songCount = songCount,
                    songThumbnails = emptyList(),
                )
            if (matchAndInsert(query, playlist)) {
                matched++
                songCount++
            } else {
                failed++
            }
            onProgress(
                SpotifyImportProgress(
                    current = index + 1,
                    total = total,
                    matched = matched,
                    failed = failed,
                    currentTitle = track.name,
                    phase = "Matching",
                ),
            )
        }

        return SpotifyImportResult(
            playlistId = entity.id,
            playlistName = playlistName,
            matched = matched,
            failed = failed,
        )
    }

    private suspend fun matchQueriesIntoPlaylist(
        playlistId: String,
        startingSongCount: Int,
        queries: List<String>,
        onProgress: (SpotifyImportProgress) -> Unit,
    ) {
        val entity =
            PlaylistEntity(
                id = playlistId,
                name = "",
                isEditable = true,
                isLocal = true,
            )
        var songCount = startingSongCount
        var matched = 0
        var failed = 0
        val total = queries.size

        queries.forEachIndexed { index, query ->
            onProgress(
                SpotifyImportProgress(
                    current = index,
                    total = total,
                    matched = matched,
                    failed = failed,
                    currentTitle = query,
                    phase = "Matching hints",
                ),
            )
            val playlist =
                Playlist(
                    playlist = entity,
                    songCount = songCount,
                    songThumbnails = emptyList(),
                )
            if (matchAndInsert(query, playlist)) {
                matched++
                songCount++
            } else {
                failed++
            }
        }
    }

    private suspend fun matchAndInsert(
        query: String,
        playlist: Playlist,
    ): Boolean {
        return try {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).fold(
                onSuccess = { page ->
                    val firstSong = page.items.distinctBy { it.id }.firstOrNull() as? SongItem
                    if (firstSong == null) {
                        Timber.tag(TAG).d("No match for query=%s", query)
                        return false
                    }
                    val media = firstSong.toMediaMetadata()
                    try {
                        database.insert(media)
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "insert failed for %s", firstSong.id)
                    }
                    val exists = database.getSongByIdBlocking(firstSong.id) != null
                    if (!exists) {
                        Timber.tag(TAG).w("Song not in DB after insert: %s", firstSong.id)
                        return false
                    }
                    database.addSongsToPlaylist(playlist, listOf(firstSong.id to null))
                    true
                },
                onFailure = {
                    Timber.tag(TAG).w(it, "search failed for query=%s", query)
                    false
                },
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "matchAndInsert error for query=%s", query)
            false
        }
    }

    fun close() {
        api.close()
    }

    companion object {
        private const val TAG = "SpotifyImport"
        const val TASTE_PLAYLIST_NAME = "Spotify Taste Import"
        const val RECOMMENDATIONS_PLAYLIST_NAME = "Metro DJ Recommendations"
        private const val LEGACY_RECOMMENDATIONS_PLAYLIST_NAME = "Nano Recommendations"

        fun deriveTopArtists(tracks: List<Pair<String, String>>, limit: Int = 20): List<String> =
            tracks
                .flatMap { (_, artist) -> splitArtistNames(artist) }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
                .take(limit)

        fun splitArtistNames(artist: String): List<String> {
            if (artist.isBlank()) return emptyList()
            // Only list separators — keep AC/DC and Simon & Garfunkel intact.
            return artist
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(artist.trim()) }
        }
    }
}
