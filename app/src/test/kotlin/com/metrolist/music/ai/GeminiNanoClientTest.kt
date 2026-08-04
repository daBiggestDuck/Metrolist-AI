/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNanoClientTest {

    @Test
    fun mapFeatureStatus_mapsKnownCodes() {
        assertEquals(GeminiNanoStatus.Unavailable, mapFeatureStatus(0))
        assertEquals(GeminiNanoStatus.Downloadable, mapFeatureStatus(1))
        assertEquals(GeminiNanoStatus.Downloading, mapFeatureStatus(2))
        assertEquals(GeminiNanoStatus.Available, mapFeatureStatus(3))
        assertEquals(GeminiNanoStatus.Error, mapFeatureStatus(99))
    }

    @Test
    fun mlKitClient_checkStatus_safeWhenClassesMissing() = runBlocking {
        val client = MlKitGeminiNanoClient()
        val status = client.checkStatus()
        // On JVM unit tests without the AAR, reflection fails → Unavailable
        assertTrue(
            status == GeminiNanoStatus.Unavailable || status == GeminiNanoStatus.Error,
        )
        assertEquals(null, client.generateContent("hello"))
    }

    @Test
    fun heuristicTasteAnalysis_includesArtists() {
        val result =
            heuristicTasteAnalysis(
                topArtists = listOf("Artist A", "Artist B"),
                topTracks = listOf("Song 1" to "Artist A"),
            )
        assertFalse(result.usedAi)
        assertTrue(result.summary.contains("Artist A"))
        assertTrue(result.searchHints.isNotEmpty())
    }

    @Test
    fun parseTasteAnalysis_readsSummaryAndHints() {
        val raw =
            """
            SUMMARY: Indie rock with electronic edges.
            HINTS:
            - indie rock playlist
            - electronic indie mix
            """.trimIndent()
        val parsed = parseTasteAnalysis(raw, usedAi = true)!!
        assertTrue(parsed.usedAi)
        assertTrue(parsed.summary.contains("Indie rock"))
        assertEquals(2, parsed.searchHints.size)
    }
}
