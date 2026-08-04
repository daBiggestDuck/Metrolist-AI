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
                ),
                batchSize = 3,
            )
        assertFalse(pick.usedAi)
        assertTrue(pick.queries.isNotEmpty())
        assertTrue(pick.commentary.contains("Phoebe Bridgers") || pick.commentary.contains("Nano DJ"))
    }
}
