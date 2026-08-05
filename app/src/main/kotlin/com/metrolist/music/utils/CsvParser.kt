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

    /**
     * Splits a CSV line, respecting double-quoted fields and escaped quotes (`""`).
     */
    fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
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
