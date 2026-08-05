/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.ai.DjAiProvider
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.TasteSummary
import com.metrolist.music.ai.heuristicTasteAnalysis
import com.metrolist.music.constants.DjAiProviderKey
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

data class CsvTasteImportResult(
    val trackCount: Int,
    val artistCount: Int,
    val summary: String,
)

/**
 * Applies parsed CSV tracks to Nano DJ taste (listening tracker + Spotify taste prefs).
 * Guarantees a non-empty, non-"undefined" summary and weighted artists/tracks.
 */
object CsvTasteImportHelper {
    private const val TAG = "CsvTasteImport"

    suspend fun importTaste(
        context: Context,
        database: MusicDatabase,
        tracks: List<Pair<String, String>>,
        enableNano: Boolean? = null,
    ): CsvTasteImportResult =
        withContext(Dispatchers.IO) {
            val cleaned =
                tracks
                    .map { (title, artist) -> title.trim() to artist.trim() }
                    .filter { it.first.isNotBlank() }
                    .distinctBy { "${it.first.lowercase()}|${it.second.lowercase()}" }

            if (cleaned.isEmpty()) {
                Timber.tag(TAG).w("importTaste called with no valid tracks")
                throw IllegalArgumentException("No tracks to import")
            }

            // Prefer caller flag; otherwise use DJ prefs — cloud providers always try AI;
            // on-device Nano follows EnableGeminiNanoKey. Heuristic coalesce still guarantees a summary.
            val nanoEnabled =
                enableNano
                    ?: run {
                        val prefs = context.dataStore.data.first()
                        val provider = DjAiProvider.fromId(prefs[DjAiProviderKey])
                        if (provider != DjAiProvider.NANO) {
                            true
                        } else {
                            prefs[EnableGeminiNanoKey] ?: true
                        }
                    }

            Timber.tag(TAG).i("Updating taste from %d CSV tracks (ai=%s)", cleaned.size, nanoEnabled)

            val seeded =
                ListeningTasteTracker.importFromTracks(
                    context,
                    cleaned,
                    enableNano = nanoEnabled,
                )
            if (seeded <= 0) {
                throw IllegalStateException("Failed to seed listening taste from CSV tracks")
            }

            val manager = SpotifyImportManager(database, context)
            val profile =
                try {
                    manager.buildTasteFromTracks(cleaned, enableNano = nanoEnabled)
                } finally {
                    manager.close()
                }

            val topArtists =
                profile.topArtists.ifEmpty {
                    SpotifyImportManager.deriveTopArtists(cleaned)
                }
            val topTracks = profile.topTracks.ifEmpty { cleaned.take(50) }

            val guaranteedSummary =
                TasteSummary.coalesce(
                    profile.analysis.summary,
                    TasteSummary.fromArtistsAndTracks(
                        artists = topArtists,
                        tracks = topTracks,
                        sourceLabel = "Exportify CSV",
                    ),
                ) ?: TasteSummary.fromArtistsAndTracks(
                    artists = topArtists,
                    tracks = topTracks,
                    sourceLabel = "Exportify CSV",
                )

            val hints =
                profile.analysis.searchHints
                    .map { it.trim() }
                    .filter { it.isNotBlank() && TasteSummary.isUsable(it) }
                    .ifEmpty {
                        heuristicTasteAnalysis(topArtists, topTracks).searchHints
                    }

            if (topArtists.isEmpty() && topTracks.isEmpty()) {
                throw IllegalStateException("Taste import produced no artists or tracks")
            }
            if (!TasteSummary.isUsable(guaranteedSummary)) {
                throw IllegalStateException("Taste import failed to write a usable summary")
            }

            ListeningTasteTracker.forceSummary(context, guaranteedSummary)

            context.safeDataStoreEdit { prefs ->
                prefs[SpotifyTasteSummaryKey] = guaranteedSummary
                if (hints.isNotEmpty()) {
                    prefs[SpotifyTasteHintsKey] = hints.joinToString("\n")
                }
                if (topArtists.isNotEmpty()) {
                    prefs[SpotifyTopArtistsKey] = topArtists.joinToString("\n")
                }
                if (topTracks.isNotEmpty()) {
                    prefs[SpotifyTopTracksKey] =
                        topTracks.joinToString("\n") { (title, artist) ->
                            if (artist.isNotBlank()) "$title — $artist" else title
                        }
                }
            }

            Timber.tag(TAG).i(
                "Taste updated: tracks=%d artists=%d listeningSeeded=%d summaryLen=%d",
                cleaned.size,
                topArtists.size,
                seeded,
                guaranteedSummary.length,
            )

            CsvTasteImportResult(
                trackCount = cleaned.size,
                artistCount = topArtists.size,
                summary = guaranteedSummary,
            )
        }
}
