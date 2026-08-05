package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {
    @Test
    fun `stripBom removes UTF-8 BOM`() {
        assertEquals("Track Name", CsvParser.stripBom("\uFEFFTrack Name"))
    }

    @Test
    fun `parseLine handles quoted fields with commas`() {
        val fields = CsvParser.parseLine("\"Hello, World\",Artist")
        assertEquals(listOf("Hello, World", "Artist"), fields)
    }

    @Test
    fun `parseLine handles escaped quotes`() {
        val fields = CsvParser.parseLine("\"Say \"\"Hi\"\"\",Artist")
        assertEquals(listOf("Say \"Hi\"", "Artist"), fields)
    }

    @Test
    fun `parseLine handles exportify style row`() {
        val line =
            "spotify:track:abc123,Song Title,Album Name,\"Artist One, Artist Two\",2024-01-01"
        val fields = CsvParser.parseLine(line)
        assertEquals(5, fields.size)
        assertEquals("Song Title", fields[1])
        assertEquals("Artist One, Artist Two", fields[3])
    }

    @Test
    fun `splitArtistNames keeps comma separated exportify artists together`() {
        val artists = CsvParser.splitArtistNames("Artist One, Artist Two")
        assertEquals(1, artists.size)
        assertEquals("Artist One, Artist Two", artists[0])
    }

    @Test
    fun `splitArtistNames splits on semicolon`() {
        val artists = CsvParser.splitArtistNames("Artist One; Artist Two")
        assertEquals(listOf("Artist One", "Artist Two"), artists)
    }

    @Test
    fun `normalizeHeader strips BOM and lowercases`() {
        assertEquals("track name", CsvParser.normalizeHeader("\uFEFFTrack Name"))
    }
}
