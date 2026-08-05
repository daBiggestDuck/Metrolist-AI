/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.viewmodels.CsvImportState

/**
 * Auto-detects track/artist/url columns from CSV header rows, including Exportify
 * (Track Name, Artist Name(s), Album Name, …) and other common playlist export formats.
 */
object CsvImportColumnDetector {
    private val trackHeaders =
        setOf(
            "track name",
            "trackname",
            "track",
            "title",
            "song",
            "song name",
            "name",
        )

    private val artistHeaders =
        setOf(
            "artist name",
            "artist name(s)",
            "artistname",
            "artist",
            "artists",
            "artist names",
            "album artist name(s)",
            "album artist name",
        )

    private val urlHeaders =
        setOf(
            "youtube url",
            "youtube link",
            "video url",
            "url",
            "link",
        )

    fun detect(
        previewRows: List<List<String>>,
        hasHeader: Boolean = previewRows.isNotEmpty() && looksLikeHeaderRow(previewRows.first()),
    ): CsvImportState {
        if (previewRows.isEmpty()) {
            return CsvImportState()
        }

        val headerCols = previewRows.first().map { CsvParser.normalizeHeader(it) }
        val trackIdx = headerCols.indexOfFirst { it in trackHeaders }
        val artistIdx = headerCols.indexOfFirst { it in artistHeaders }
        val urlIdx = headerCols.indexOfFirst { it in urlHeaders }

        return if (hasHeader && trackIdx >= 0 && artistIdx >= 0) {
            CsvImportState(
                previewRows = previewRows,
                artistColumnIndex = artistIdx,
                titleColumnIndex = trackIdx,
                urlColumnIndex = urlIdx,
                hasHeader = true,
            )
        } else {
            CsvImportState(
                previewRows = previewRows,
                artistColumnIndex = 0,
                titleColumnIndex = 1,
                urlColumnIndex = -1,
                hasHeader = hasHeader,
            )
        }
    }

    fun looksLikeHeaderRow(row: List<String>): Boolean {
        val normalized = row.map { CsvParser.normalizeHeader(it) }
        val hasTrack = normalized.any { it in trackHeaders }
        val hasArtist = normalized.any { it in artistHeaders }
        if (hasTrack && hasArtist) return true
        // Two-column CSV without recognizable headers — treat first row as data.
        return normalized.any { header ->
            header.contains("name") || header.contains("artist") || header.contains("title")
        }
    }
}
