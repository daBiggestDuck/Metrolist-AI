/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoDjEngineTest {
    @Test
    fun parseDjPick_extractsTalkAndQueries() {
        val raw =
            """
            TALK: Keeping the night mellow with your favorites.
            NEXT:
            - Blinding Lights - The Weeknd
            - Levitating - Dua Lipa
            - Circles - Post Malone
            """.trimIndent()

        val pick = NanoDjEngine.parseDjPick(raw, batchSize = 3, usedAi = true)!!
        assertTrue(pick.usedAi)
        assertTrue(pick.commentary.contains("mellow"))
        assertEquals(3, pick.queries.size)
        assertEquals("Blinding Lights - The Weeknd", pick.queries.first())
    }

    @Test
    fun heuristicPick_usesSeedArtists() {
        val pick =
            NanoDjEngine.heuristicPick(
                NanoDjEngine.DjContext(
                    tasteSummary = "indie pop",
                    recentTitles = emptyList(),
                    seedArtists = listOf("Phoebe Bridgers"),
                    seedTracks = listOf("Motion Sickness - Phoebe Bridgers"),
                    lane = ListeningTasteTracker.DjLane.ARTIST_RADIO,
                ),
                batchSize = 3,
            )
        assertFalse(pick.usedAi)
        assertTrue(pick.queries.isNotEmpty())
        assertTrue(pick.commentary.contains("Phoebe Bridgers") || pick.commentary.contains("Metro DJ"))
    }

    @Test
    fun heuristicPick_referencesChillLane() {
        val pick =
            NanoDjEngine.heuristicPick(
                NanoDjEngine.DjContext(
                    tasteSummary = "late night calm",
                    recentTitles = emptyList(),
                    seedArtists = listOf("Billie Eilish"),
                    seedTracks = emptyList(),
                    categories = listOf("chill"),
                    lane = ListeningTasteTracker.DjLane.CHILL,
                ),
                batchSize = 3,
            )
        assertTrue(pick.commentary.contains("chill", ignoreCase = true))
        assertTrue(pick.queries.any { it.contains("chill", ignoreCase = true) || it.contains("lofi", ignoreCase = true) })
    }

    @Test
    fun interstitialOnly_keepsDrAbbreviation() {
        val line = "Keeping the Dr. Dre vibe going — Metro DJ's got more like this coming up."
        val spoken = NanoDjEngine.interstitialOnly(line)
        assertTrue(spoken.contains("Dr. Dre"))
        assertFalse(spoken.trim() == "Keeping the Dr.")
    }

    @Test
    fun listeningTaste_encodeDecodeRoundTrip() {
        val encoded =
            ListeningTasteTracker.encodeWeights(
                mapOf("Billie Eilish" to 3.5f, "chill" to 2f),
            )
        val decoded = ListeningTasteTracker.decodeWeights(encoded)
        assertEquals(3.5f, decoded["Billie Eilish"]!!, 0.01f)
        assertEquals(2f, decoded["chill"]!!, 0.01f)
    }

    @Test
    fun listeningTaste_inferCategoriesFromTitle() {
        val cats = ListeningTasteTracker.inferCategories("Soft Chill Lofi Study Mix", emptyList())
        assertTrue(cats.contains("chill") || cats.contains("focus"))
    }

    @Test
    fun listeningTaste_pickLanePrefersStrongCategory() {
        val lane =
            ListeningTasteTracker.pickLane(
                categories = mapOf("hype" to 5f, "chill" to 1f),
                artists = mapOf("Artist A" to 2f),
            )
        assertEquals(ListeningTasteTracker.DjLane.HYPE, lane)
    }
}
