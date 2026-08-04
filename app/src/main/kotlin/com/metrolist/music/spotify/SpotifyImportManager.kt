/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.ai.TasteAnalysisResult
import com.metrolist.music.ai.analyzeSpotifyTaste
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
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
)

class SpotifyImportManager(
    private val database: MusicDatabase,
    private val api: SpotifyApi = SpotifyApi(),
) {
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
            val analysis =
                analyzeSpotifyTaste(
                    topArtists = artists.map { it.name },
                    topTracks = tracks.map { it.name to it.artistsJoined },
                    enableNano = enableGeminiNano,
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

            matchResult.copy(tasteAnalysis = analysis)
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
    }
}
