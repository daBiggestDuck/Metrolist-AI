package com.metrolist.music.utils

import com.metrolist.music.ai.TasteImportAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Parses the user's real Exportify CSV and proves the taste-import prompt paste path.
 */
class RealExportifyCsvTasteTest {
    private val realCsv =
        File(System.getProperty("user.home"), "Downloads/Most_listened_(past_month).csv")

    @Test
    fun `parses real Most_listened past_month Exportify CSV into Title by Artist prompt`() {
        assumeTrue("Real Exportify CSV not found at ${realCsv.absolutePath}", realCsv.isFile)

        val lines = realCsv.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertTrue("CSV should have header + rows", lines.size > 10)

        val previewRows = lines.take(5).map { CsvParser.parseLine(it) }
        val mapping = CsvImportColumnDetector.detect(previewRows, hasHeader = true)
        assertEquals("Track Name column", 1, mapping.titleColumnIndex)
        assertEquals("Artist Name(s) column", 3, mapping.artistColumnIndex)

        val parsed = CsvPlaylistParser.parse(lines, mapping)
        assertTrue("Expected many tracks from real CSV, got ${parsed.tracks.size}", parsed.tracks.size >= 40)

        // Spot-check first rows from the user's file.
        assertEquals("けっかおーらい - Kekka Orai", parsed.tracks[0].first)
        assertEquals("Kocchi no Kento", parsed.tracks[0].second)
        assertTrue(parsed.tracks.any { it.first == "Last Dance" && it.second == "Eve" })
        assertTrue(parsed.tracks.any { it.first == "Tek It" && it.second.contains("Cafuné") })

        val prompt = TasteImportAi.buildPrompt(parsed.tracks)
        assertTrue(prompt.contains("けっかおーらい - Kekka Orai by Kocchi no Kento"))
        assertTrue(prompt.contains("Last Dance by Eve"))
        assertTrue(prompt.contains("SUMMARY:"))
        assertTrue(prompt.contains("HINTS:"))
        assertTrue(
            "Prompt must paste song lines, not Spotify URIs",
            !prompt.contains("spotify:track:"),
        )
    }

    @Test
    fun `recommend prompt uses saved summary text only`() {
        val prompt =
            TasteImportAi.buildRecommendPrompt(
                "Energetic J-pop and darkwave — kakizaki yuta next to Jfarrari.",
            )
        assertTrue(prompt.contains("My music taste is: Energetic J-pop and darkwave"))
        assertTrue(prompt.contains("Suggest playable songs as HINTS:"))
        assertTrue(prompt.contains("- Title - Artist"))
        assertTrue(!prompt.contains("spotify:track:"))
    }

    @Test
    fun `column detector matches Exportify header without file IO`() {
        val header =
            "Track URI,Track Name,Album Name,Artist Name(s),Release Date,Duration (ms)"
        val row =
            "spotify:track:abc,Last Dance,Otogi,Eve,2019-02-06,240496"
        val preview = listOf(CsvParser.parseLine(header), CsvParser.parseLine(row))
        val mapping = CsvImportColumnDetector.detect(preview, hasHeader = true)
        val parsed = CsvPlaylistParser.parse(listOf(header, row), mapping)
        assertEquals(listOf("Last Dance" to "Eve"), parsed.tracks)
    }
}
