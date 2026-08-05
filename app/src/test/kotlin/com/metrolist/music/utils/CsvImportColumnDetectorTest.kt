package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportColumnDetectorTest {
    @Test
    fun `detects exportify net headers`() {
        val preview =
            listOf(
                listOf(
                    "Track URI",
                    "Track Name",
                    "Album Name",
                    "Artist Name(s)",
                    "Release Date",
                ),
                listOf("spotify:track:1", "Song", "Album", "Artist A, Artist B", "2024"),
            )
        val state = CsvImportColumnDetector.detect(preview, hasHeader = true)
        assertEquals(1, state.titleColumnIndex)
        assertEquals(3, state.artistColumnIndex)
        assertTrue(state.hasHeader)
    }

    @Test
    fun `detects exportify app headers with BOM`() {
        val preview =
            listOf(
                listOf(
                    "\uFEFFTrack URI",
                    "Track Name",
                    "Artist Name(s)",
                    "Album Name",
                ),
                listOf("spotify:track:1", "Song", "Artist", "Album"),
            )
        val state = CsvImportColumnDetector.detect(preview, hasHeader = true)
        assertEquals(1, state.titleColumnIndex)
        assertEquals(2, state.artistColumnIndex)
    }

    @Test
    fun `looksLikeHeaderRow recognizes exportify headers`() {
        val row = listOf("Track URI", "Track Name", "Album Name", "Artist Name(s)")
        assertTrue(CsvImportColumnDetector.looksLikeHeaderRow(row))
    }

    @Test
    fun `isConfidentExportify recognizes Track URI exports`() {
        val preview =
            listOf(
                listOf(
                    "Track URI",
                    "Track Name",
                    "Album Name",
                    "Artist Name(s)",
                    "Release Date",
                ),
                listOf("spotify:track:1", "Song", "Album", "Artist", "2024"),
            )
        val state = CsvImportColumnDetector.detect(preview, hasHeader = true)
        assertTrue(CsvImportColumnDetector.isConfidentExportify(state))
    }
}
