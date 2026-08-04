/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import android.content.Context
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.SpotifyClientIdKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.NanoDjQueue
import com.metrolist.music.spotify.SpotifyApi
import com.metrolist.music.spotify.SpotifyAuth
import com.metrolist.music.spotify.SpotifyTokenStore
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Starts Nano DJ (Gemini Nano–powered Spotify DJ replacement) from taste prefs / Spotify account.
 */
object NanoDjLauncher {
    private const val TAG = "NanoDJ"

    suspend fun start(
        context: Context,
        playerConnection: PlayerConnection,
        speak: Boolean = true,
    ): Result<Unit> =
        runCatching {
            NanoDjSession.ensureTts(context)
            NanoDjSession.setSpeakEnabled(speak)

            val prefs = context.dataStore
            val enableNano = prefs.get(EnableGeminiNanoKey, true)
            var tasteSummary = prefs.get(SpotifyTasteSummaryKey, "")
            var hints =
                prefs.get(SpotifyTasteHintsKey, "")
                    .split('\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

            var seedArtists =
                prefs.get(SpotifyTopArtistsKey, "")
                    .split('\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            var seedTracks =
                prefs.get(SpotifyTopTracksKey, "")
                    .split('\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

            val clientId = prefs.get(SpotifyClientIdKey, "")
            if (!SpotifyTokenStore.retrieve().isNullOrBlank() && clientId.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    val access = ensureAccessToken(clientId) ?: return@withContext
                    val api = SpotifyApi()
                    try {
                        val artists = api.getTopArtists(access, "medium_term", 15)
                        val tracks = api.getTopTracks(access, "medium_term", 20)
                        if (artists.isNotEmpty()) seedArtists = artists.map { it.name }
                        if (tracks.isNotEmpty()) {
                            seedTracks = tracks.map { "${it.name} - ${it.artistsJoined}" }
                        }
                        if (tasteSummary.isBlank()) {
                            val analysis =
                                analyzeSpotifyTaste(
                                    topArtists = seedArtists,
                                    topTracks = tracks.map { it.name to it.artistsJoined },
                                    enableNano = enableNano,
                                )
                            tasteSummary = analysis.summary
                            if (hints.isEmpty()) hints = analysis.searchHints
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Live Spotify taste fetch failed; using cached prefs")
                    } finally {
                        api.close()
                    }
                }
            }

            if (seedTracks.isEmpty() && hints.isNotEmpty()) {
                seedTracks = hints
            }
            if (seedArtists.isEmpty()) {
                seedArtists = extractArtistsFromHints(hints)
            }

            val seedItems =
                withContext(Dispatchers.IO) {
                    resolveSeedItems((seedTracks + hints).distinct().take(5))
                }

            if (
                seedItems.isEmpty() &&
                seedTracks.isEmpty() &&
                hints.isEmpty() &&
                seedArtists.isEmpty() &&
                tasteSummary.isBlank()
            ) {
                error(
                    "Nano DJ needs Spotify taste first. Connect Spotify and import taste, or connect so top tracks can load.",
                )
            }

            val queue =
                NanoDjQueue.fromTaste(
                    tasteSummary = tasteSummary,
                    seedArtists = seedArtists,
                    seedTracks = seedTracks.ifEmpty { hints },
                    enableNano = enableNano,
                    seedMediaItems = seedItems,
                )
            playerConnection.playQueue(queue)
        }

    private suspend fun ensureAccessToken(clientId: String): String? {
        val current = SpotifyTokenStore.retrieve()
        if (!current.isNullOrBlank() && !SpotifyTokenStore.isExpired()) return current
        val refresh = SpotifyTokenStore.getRefreshToken() ?: return current
        if (clientId.isBlank()) return current
        val auth = SpotifyAuth()
        return try {
            val result = auth.refresh(clientId, refresh)
            SpotifyTokenStore.storeFull(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken.ifBlank { refresh },
                expiresInSec = result.expiresInSec,
            )
            result.accessToken
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Token refresh failed")
            current
        } finally {
            auth.close()
        }
    }

    private fun extractArtistsFromHints(hints: List<String>): List<String> =
        hints.mapNotNull { line ->
            when {
                " - " in line -> line.substringAfter(" - ").trim().takeIf { it.isNotBlank() }
                " by " in line -> line.substringAfter(" by ").trim().takeIf { it.isNotBlank() }
                else -> null
            }
        }.distinct().take(8)

    private suspend fun resolveSeedItems(queries: List<String>): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        for (query in queries) {
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() ?: continue
            val song = result.items.filterIsInstance<SongItem>().firstOrNull() ?: continue
            out += song.toMediaItem()
            if (out.size >= 3) break
        }
        return out
    }
}
