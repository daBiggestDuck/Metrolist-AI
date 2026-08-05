/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteSummaryTest {
    @Test
    fun rejectsUndefinedAndBlank() {
        assertFalse(TasteSummary.isUsable(null))
        assertFalse(TasteSummary.isUsable(""))
        assertFalse(TasteSummary.isUsable("   "))
        assertFalse(TasteSummary.isUsable("undefined"))
        assertFalse(TasteSummary.isUsable("UNDEFINED"))
        assertFalse(TasteSummary.isUsable("null"))
        assertFalse(TasteSummary.isUsable("n/a"))
        assertNull(TasteSummary.sanitizeOrNull("undefined"))
    }

    @Test
    fun acceptsRealSummary() {
        val s = "Your Exportify CSV taste leans toward The Weeknd, Dua Lipa."
        assertTrue(TasteSummary.isUsable(s))
        assertEquals(s, TasteSummary.sanitizeOrNull(s))
    }

    @Test
    fun fromArtistsAndTracks_neverBlankOrUndefined() {
        val summary =
            TasteSummary.fromArtistsAndTracks(
                artists = listOf("The Weeknd", "Dua Lipa"),
                tracks = listOf("Blinding Lights" to "The Weeknd", "Levitating" to "Dua Lipa"),
                sourceLabel = "Exportify CSV",
            )
        assertTrue(TasteSummary.isUsable(summary))
        assertTrue(summary.contains("The Weeknd"))
        assertFalse(summary.equals("undefined", ignoreCase = true))
        assertTrue(summary.isNotBlank())
    }

    @Test
    fun fromArtistsAndTracks_tracksOnly() {
        val summary =
            TasteSummary.fromArtistsAndTracks(
                artists = emptyList(),
                tracks = listOf("Song A" to "Artist A"),
                sourceLabel = "imported playlist",
            )
        assertTrue(TasteSummary.isUsable(summary))
        assertTrue(summary.contains("Song A"))
    }

    @Test
    fun coalescePrefersFirstUsable() {
        assertEquals(
            "good summary here",
            TasteSummary.coalesce("undefined", "good summary here"),
        )
        assertEquals(
            "live taste text ok",
            TasteSummary.coalesce("live taste text ok", "spotify blurb ok"),
        )
        assertNull(TasteSummary.coalesce("undefined", "null"))
    }

    @Test
    fun parseTasteAnalysis_rejectsUndefinedSummary() {
        val parsed =
            parseTasteAnalysis(
                """
                SUMMARY: undefined
                HINTS:
                - real song query here
                """.trimIndent(),
                usedAi = true,
            )
        assertNotNull(parsed)
        assertTrue(parsed!!.searchHints.isNotEmpty())
        assertFalse(TasteSummary.isUsable(parsed.summary))
    }

    @Test
    fun encodeDecodeWeights_roundTrip() {
        val map = mapOf("The Weeknd" to 5f, "Dua Lipa" to 3.5f)
        val encoded = ListeningTasteTracker.encodeWeights(map)
        val decoded = ListeningTasteTracker.decodeWeights(encoded)
        assertEquals(2, decoded.size)
        assertTrue((decoded["The Weeknd"] ?: 0f) > 4f)
    }
}
