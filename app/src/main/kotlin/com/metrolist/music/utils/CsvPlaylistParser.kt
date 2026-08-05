/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.viewmodels.CsvImportState
import timber.log.Timber

/**
 * Shared CSV row parsing for playlist / taste imports (Exportify, two-column exports, etc.).
 */
object CsvPlaylistParser {
    private const val TAG = "CsvPlaylistParser"

    data class ParseResult(
        val tracks: List<Pair<String, String>>,
        val songs: List<Song>,
    )

    fun parse(
        lines: List<String>,
        columnMapping: CsvImportState,
    ): ParseResult {
        val startIndex = if (columnMapping.hasHeader) 1 else 0
        val dataLines = lines.drop(startIndex).filter { it.isNotBlank() }
        val tracks = arrayListOf<Pair<String, String>>()
        val songs = arrayListOf<Song>()

        dataLines.forEach { line ->
            val parts = CsvParser.parseLine(line)
            if (parts.isEmpty() ||
                columnMapping.artistColumnIndex >= parts.size ||
                columnMapping.titleColumnIndex >= parts.size
            ) {
                return@forEach
            }

            val title = parts[columnMapping.titleColumnIndex].trim()
            val artistStr = parts[columnMapping.artistColumnIndex].trim()
            if (title.isEmpty()) return@forEach

            val artistNames = CsvParser.splitArtistNames(artistStr)
            tracks += title to artistStr
            songs +=
                Song(
                    song = SongEntity(id = "", title = title),
                    artists = artistNames.map { ArtistEntity(id = "", name = it) },
                )
        }

        Timber.tag(TAG).d(
            "Parsed %d tracks from %d data rows (header=%s)",
            tracks.size,
            dataLines.size,
            columnMapping.hasHeader,
        )
        return ParseResult(tracks = tracks, songs = songs)
    }
}
