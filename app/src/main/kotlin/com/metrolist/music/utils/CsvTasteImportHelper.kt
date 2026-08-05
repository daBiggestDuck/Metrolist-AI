/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
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

            val nanoEnabled =
                enableNano
                    ?: context.dataStore.data.first()[EnableGeminiNanoKey]
                    ?: true

            Timber.tag(TAG).i("Updating taste from %d CSV tracks (nano=%s)", cleaned.size, nanoEnabled)

            val seeded = ListeningTasteTracker.importFromTracks(context, cleaned, enableNano = nanoEnabled)

            val manager = SpotifyImportManager(database, context)
            val profile =
                try {
                    manager.buildTasteFromTracks(cleaned, enableNano = nanoEnabled)
                } finally {
                    manager.close()
                }

            val topArtists = profile.topArtists
            val topTracks = profile.topTracks
            val analysis = profile.analysis

            context.safeDataStoreEdit { prefs ->
                if (analysis.summary.isNotBlank()) {
                    prefs[SpotifyTasteSummaryKey] = analysis.summary
                }
                if (analysis.searchHints.isNotEmpty()) {
                    prefs[SpotifyTasteHintsKey] = analysis.searchHints.joinToString("\n")
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
                "Taste updated: tracks=%d artists=%d listeningSeeded=%d",
                cleaned.size,
                topArtists.size,
                seeded,
            )

            CsvTasteImportResult(
                trackCount = cleaned.size,
                artistCount = topArtists.size,
                summary = analysis.summary,
            )
        }
}
