package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Regression test against a sanitized Exportify.net CSV (UTF-8 BOM, Track URI column,
 * quoted fields, comma-in-genres, semicolon-separated multi-artists).
 */
class ExportifyRealFileParserTest {
    private fun loadFixtureLines(): List<String> {
        val stream =
            requireNotNull(
                javaClass.classLoader!!.getResourceAsStream("exportify_most_listened_sample.csv"),
            ) { "Missing test resource exportify_most_listened_sample.csv" }
        val text =
            stream.use { input ->
                CsvParser.stripBom(
                    BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText(),
                )
            }
        return text.lines().filter { it.isNotBlank() }
    }

    @Test
    fun `parses real Exportify Most listened past month headers and rows`() {
        val lines = loadFixtureLines()
        assertTrue("Expected header + data rows", lines.size >= 10)

        val headerCells = CsvParser.parseLine(lines.first())
        assertEquals("Track URI", headerCells[0].trimStart('\uFEFF'))
        assertEquals("Track Name", headerCells[1])
        assertEquals("Album Name", headerCells[2])
        assertEquals("Artist Name(s)", headerCells[3])
        assertTrue(headerCells.size >= 24)

        val previewRows = lines.take(6).map { CsvParser.parseLine(it) }
        assertTrue(CsvImportColumnDetector.looksLikeHeaderRow(previewRows.first()))
        val mapping = CsvImportColumnDetector.detect(previewRows, hasHeader = true)

        assertEquals(1, mapping.titleColumnIndex)
        assertEquals(3, mapping.artistColumnIndex)
        assertTrue(mapping.hasHeader)

        val result = CsvPlaylistParser.parse(lines, mapping)
        // fixture has header + ~20 data rows
        assertTrue("Expected many tracks, got ${result.tracks.size}", result.tracks.size >= 15)
        assertEquals(result.tracks.size, result.songs.size)

        val first = result.tracks.first()
        assertEquals("けっかおーらい - Kekka Orai", first.first)
        assertEquals("Kocchi no Kento", first.second)

        // Quoted genre commas must not shift artist column
        assertFalse(result.tracks.any { it.first.startsWith("spotify:track:") })
        assertTrue(result.tracks.any { it.second.contains("Yorushika") })

        // Semicolon-separated Exportify multi-artist cell
        val multi = result.tracks.find { it.first == "Face Myself Again" }
        assertEquals("Jfarrari;Hxvsfly", multi?.second)

        // Album name with embedded comma must not break the row
        val binary = result.tracks.find { it.first == "Binary Data IV" }
        assertEquals("Alfonso Peduto", binary?.second)
    }

    @Test
    fun `SpotifyFileTasteImporter delimited path matches playlist parser`() {
        val lines = loadFixtureLines()
        val text = lines.joinToString("\n")
        // Mirror SpotifyFileTasteImporter.parseDelimitedOrPlain entry
        val previewRows = lines.take(6).map { CsvParser.parseLine(it) }
        val hasHeader = CsvImportColumnDetector.looksLikeHeaderRow(previewRows.first())
        val mapping = CsvImportColumnDetector.detect(previewRows, hasHeader)
        val parsed = CsvPlaylistParser.parse(lines, mapping)
        assertTrue(parsed.tracks.isNotEmpty())
        assertEquals("けっかおーらい - Kekka Orai", parsed.tracks.first().first)
        // Ensure we never treat Track URI as the title
        assertTrue(parsed.tracks.none { it.first.startsWith("spotify:") })
        assertTrue(text.contains("Track URI"))
    }
}
