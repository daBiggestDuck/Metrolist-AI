/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.SpotifyImportArtistDerive
import com.metrolist.music.ai.TasteImportAi
import com.metrolist.music.ai.DjAiException
import com.metrolist.music.ai.TasteImportException
import com.metrolist.music.ai.TasteImportFailReason
import com.metrolist.music.ai.TasteSummary
import com.metrolist.music.ai.heuristicTasteAnalysis
import com.metrolist.music.constants.ListeningTasteSummaryKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class CsvTasteImportResult(
    val trackCount: Int,
    val artistCount: Int,
    val summary: String,
    val usedAi: Boolean,
)

/**
 * Applies parsed CSV / playlist tracks to Metro DJ taste (listening tracker + Spotify taste prefs).
 *
 * Dumb flow: paste songs into DJ AI → get SUMMARY → save permanently to both
 * [ListeningTasteSummaryKey] and [SpotifyTasteSummaryKey]. AI runs *before* seeding so a failed
 * AI call never leaves a heuristic "success" taste behind.
 */
object CsvTasteImportHelper {
    private const val TAG = "CsvTasteImport"

    suspend fun importTaste(
        context: Context,
        @Suppress("UNUSED_PARAMETER") database: MusicDatabase,
        tracks: List<Pair<String, String>>,
        @Suppress("UNUSED_PARAMETER") enableNano: Boolean? = null,
        /** File imports should still save a usable taste when an optional AI provider is unavailable. */
        allowHeuristicFallback: Boolean = false,
    ): CsvTasteImportResult =
        withContext(Dispatchers.IO) {
            val cleaned =
                tracks
                    .map { (title, artist) -> title.trim() to artist.trim() }
                    .filter { it.first.isNotBlank() }
                    .distinctBy { "${it.first.lowercase()}|${it.second.lowercase()}" }

            if (cleaned.isEmpty()) {
                Timber.tag(TAG).w("importTaste called with no valid tracks")
                throw TasteImportException(TasteImportFailReason.NO_TRACKS, "No tracks to import")
            }

            Timber.tag(TAG).i("Taste import starting for %d tracks", cleaned.size)

            // 1) Prefer the selected DJ AI. A picked Exportify file is still useful without an
            // AI provider, so that premium-free import path falls back to a deterministic profile
            // instead of discarding the entire import after parsing succeeds.
            val analysis =
                try {
                    TasteImportAi.analyzeTracks(context, cleaned)
                } catch (e: TasteImportException) {
                    if (!allowHeuristicFallback) throw e
                    Timber.tag(TAG).w(e, "DJ AI unavailable; using heuristic taste for file import")
                    heuristicTasteAnalysis(
                        SpotifyImportArtistDerive.derive(cleaned),
                        cleaned,
                    )
                } catch (e: DjAiException) {
                    if (!allowHeuristicFallback) throw e
                    Timber.tag(TAG).w(e, "DJ AI unavailable; using heuristic taste for file import")
                    heuristicTasteAnalysis(
                        SpotifyImportArtistDerive.derive(cleaned),
                        cleaned,
                    )
                } catch (e: IllegalStateException) {
                    if (!allowHeuristicFallback) throw e
                    Timber.tag(TAG).w(e, "DJ AI unavailable; using heuristic taste for file import")
                    heuristicTasteAnalysis(
                        SpotifyImportArtistDerive.derive(cleaned),
                        cleaned,
                    )
                }
            val summary =
                TasteSummary.sanitizeOrNull(analysis.summary)
                    ?: throw TasteImportException(
                        TasteImportFailReason.PARSE_FAILED,
                        "Taste summary was blank after analysis",
                    )

            val topArtists = SpotifyImportArtistDerive.derive(cleaned)
            val topTracks = cleaned.take(50)
            val hints =
                analysis.searchHints
                    .map { it.trim() }
                    .filter { it.isNotBlank() && TasteSummary.isUsable(it) }
                    .ifEmpty {
                        if (!analysis.usedAi) {
                            heuristicTasteAnalysis(topArtists, topTracks).searchHints
                        } else {
                            emptyList()
                        }
                    }

            if (topArtists.isEmpty() && topTracks.isEmpty()) {
                throw IllegalStateException("Taste import produced no artists or tracks")
            }

            // 2) Seed weighted listening artists/tracks (no nested AI).
            val seeded =
                ListeningTasteTracker.importFromTracks(
                    context,
                    cleaned,
                    enableNano = false,
                )
            if (seeded <= 0) {
                throw IllegalStateException("Failed to seed listening taste from CSV tracks")
            }

            // 3) Overwrite listening summary with the AI answer (importFromTracks wrote a heuristic).
            ListeningTasteTracker.forceSummary(context, summary)

            // 4) Persist Spotify taste prefs + listening summary key in one write (UI re-reads both).
            context.safeDataStoreEdit { prefs ->
                prefs[ListeningTasteSummaryKey] = summary
                prefs[SpotifyTasteSummaryKey] = summary
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
                "Taste updated: tracks=%d artists=%d usedAi=%s summaryLen=%d",
                cleaned.size,
                topArtists.size,
                analysis.usedAi,
                summary.length,
            )

            CsvTasteImportResult(
                trackCount = cleaned.size,
                artistCount = topArtists.size,
                summary = summary,
                usedAi = analysis.usedAi,
            )
        }
}
