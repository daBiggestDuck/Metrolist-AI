/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

/**
 * Shared CSV parsing for playlist imports (Exportify, two-column exports, etc.).
 */
object CsvParser {
    fun stripBom(text: String): String = text.trimStart('\uFEFF')

    fun normalizeHeader(header: String): String = stripBom(header).trim().lowercase()

    /** Exportify / Spotify URI column values — never treat as a song title. */
    fun isSpotifyTrackUri(value: String): Boolean {
        val t = stripBom(value).trim()
        return t.startsWith("spotify:track:", ignoreCase = true) ||
            t.startsWith("https://open.spotify.com/track/", ignoreCase = true) ||
            t.startsWith("http://open.spotify.com/track/", ignoreCase = true)
    }

    /**
     * Splits a CSV line, respecting double-quoted fields and escaped quotes (`""`).
     * Strips a leading UTF-8 BOM from the first cell when present on the line.
     */
    fun parseLine(line: String): List<String> {
        val normalizedLine = stripBom(line)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < normalizedLine.length) {
            when (val c = normalizedLine[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < normalizedLine.length && normalizedLine[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) {
                        current.append(c)
                    } else {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    /**
     * Exportify and similar exports use comma-separated artist lists in one cell.
     * Keep the full string for YouTube search unless semicolons clearly separate artists.
     */
    fun splitArtistNames(artistStr: String): List<String> {
        val trimmed = artistStr.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (';' in trimmed) {
            trimmed.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf(trimmed)
        }
    }
}
