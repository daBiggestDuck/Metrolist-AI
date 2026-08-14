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
        val effectiveHasHeader = hasHeader && trackIdx >= 0 && artistIdx >= 0

        return if (effectiveHasHeader) {
            CsvImportState(
                previewRows = previewRows,
                artistColumnIndex = artistIdx,
                titleColumnIndex = trackIdx,
                urlColumnIndex = urlIdx,
                hasHeader = true,
            )
        } else {
            val firstDataRow = previewRows.firstOrNull()
            val uriIndex = firstDataRow?.indexOfFirst(CsvParser::isSpotifyTrackUri) ?: -1
            val (titleColumn, artistColumn) =
                when {
                    firstDataRow == null -> 1 to 0
                    firstDataRow.size == 2 -> 0 to 1
                    uriIndex >= 0 && uriIndex + 3 < firstDataRow.size -> (uriIndex + 1) to (uriIndex + 3)
                    else -> 1 to 0
                }
            CsvImportState(
                previewRows = previewRows,
                artistColumnIndex = artistColumn,
                titleColumnIndex = titleColumn,
                urlColumnIndex = -1,
                hasHeader = effectiveHasHeader,
            )
        }
    }

    fun looksLikeHeaderRow(row: List<String>): Boolean {
        val normalized = row.map { CsvParser.normalizeHeader(it) }
        val hasTrack = normalized.any { it in trackHeaders }
        val hasArtist = normalized.any { it in artistHeaders }
        return hasTrack && hasArtist
    }

    /**
     * True when headers clearly match Exportify.net (Track Name + Artist Name(s)),
     * so Backup & restore can skip the column-mapping dialog.
     */
    fun isConfidentExportify(state: CsvImportState): Boolean {
        if (!state.hasHeader || state.previewRows.isEmpty()) return false
        if (state.titleColumnIndex < 0 || state.artistColumnIndex < 0) return false
        val headers = state.previewRows.first().map { CsvParser.normalizeHeader(it) }
        val titleHeader = headers.getOrNull(state.titleColumnIndex).orEmpty()
        val artistHeader = headers.getOrNull(state.artistColumnIndex).orEmpty()
        val titleOk = titleHeader in trackHeaders && titleHeader != "name"
        val artistOk = artistHeader in artistHeaders
        val looksExportify =
            headers.any { it == "track uri" || it == "track name" } &&
                headers.any { it == "artist name(s)" || it == "artist name" }
        return titleOk && artistOk && looksExportify
    }
}
