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
import com.metrolist.music.constants.ListeningTasteSummaryKey
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
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Starts Nano DJ from continuous listening taste (merged with Spotify import when present).
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
            val djClient = GeminiNanoClient.get(context)

            // Do NOT overwrite imported CSV / taste prefs with live Spotify tops.
            // If the user already saved a taste summary, keep it as the DJ source of truth.
            val hasSavedTaste =
                TasteSummary.isUsable(prefs.get(SpotifyTasteSummaryKey, "")) ||
                    TasteSummary.isUsable(prefs.get(ListeningTasteSummaryKey, ""))
            val clientId = prefs.get(SpotifyClientIdKey, "")
            if (
                !hasSavedTaste &&
                !SpotifyTokenStore.retrieve().isNullOrBlank() &&
                clientId.isNotBlank()
            ) {
                withContext(Dispatchers.IO) {
                    val access = ensureAccessToken(clientId) ?: return@withContext
                    val api = SpotifyApi()
                    try {
                        val artists = api.getTopArtists(access, "medium_term", 15)
                        val tracks = api.getTopTracks(access, "medium_term", 20)
                        if (artists.isNotEmpty() || tracks.isNotEmpty()) {
                            context.safePersistSpotifyTops(
                                artists = artists.map { it.name },
                                tracks = tracks.map { "${it.name} - ${it.artistsJoined}" },
                                enableNano = enableNano,
                                client = djClient,
                            )
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Live Spotify taste fetch failed; using cached prefs")
                    } finally {
                        api.close()
                    }
                }
            }

            val merged = ListeningTasteTracker.loadMergedTaste(context)

            if (
                merged.seedTracks.isEmpty() &&
                merged.hints.isEmpty() &&
                merged.seedArtists.isEmpty() &&
                merged.summary.isBlank()
            ) {
                error(
                    "Nano DJ needs listening history or Spotify taste first. Play some songs " +
                        "(or import Spotify taste), then try again.",
                )
            }

            val seedItems =
                withContext(Dispatchers.IO) {
                    resolveSeedItems(
                        (merged.seedTracks + merged.hints).distinct().take(5),
                    )
                }

            val queue =
                NanoDjQueue.fromTaste(
                    tasteSummary = merged.summary,
                    seedArtists = merged.seedArtists,
                    seedTracks = merged.seedTracks.ifEmpty { merged.hints },
                    enableNano = enableNano,
                    client = djClient,
                    seedMediaItems = seedItems,
                    categories = merged.categories,
                    lane = merged.lane,
                )
            playerConnection.playQueue(queue)
        }

    private suspend fun Context.safePersistSpotifyTops(
        artists: List<String>,
        tracks: List<String>,
        enableNano: Boolean,
        client: GeminiNanoClient,
    ) {
        val prefs = dataStore
        var tasteSummary = prefs.get(SpotifyTasteSummaryKey, "")
        var hints =
            prefs.get(SpotifyTasteHintsKey, "")
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }

        if (tasteSummary.isBlank() && (artists.isNotEmpty() || tracks.isNotEmpty())) {
            val trackPairs =
                tracks.map { line ->
                    when {
                        " - " in line -> line.substringBefore(" - ").trim() to line.substringAfter(" - ").trim()
                        else -> line to ""
                    }
                }
            val analysis =
                runCatching {
                    analyzeSpotifyTaste(
                        context = this,
                        topArtists = artists,
                        topTracks = trackPairs,
                        enableNano = enableNano,
                        client = client,
                    )
                }.getOrElse {
                    heuristicTasteAnalysis(artists, trackPairs)
                }
            tasteSummary = analysis.summary
            if (hints.isEmpty()) hints = analysis.searchHints
        }

        safeDataStoreEdit { edit ->
            if (artists.isNotEmpty()) edit[SpotifyTopArtistsKey] = artists.joinToString("\n")
            if (tracks.isNotEmpty()) edit[SpotifyTopTracksKey] = tracks.joinToString("\n")
            if (tasteSummary.isNotBlank()) edit[SpotifyTasteSummaryKey] = tasteSummary
            if (hints.isNotEmpty()) edit[SpotifyTasteHintsKey] = hints.joinToString("\n")
        }
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
