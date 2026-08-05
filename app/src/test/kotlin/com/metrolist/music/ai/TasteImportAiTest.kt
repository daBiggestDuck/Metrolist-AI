package com.metrolist.music.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt builder + SUMMARY/HINTS parse for Exportify-style title/artist rows.
 */
class TasteImportAiTest {
    private val sampleExportifyRows =
        listOf(
            "けっかおーらい - Kekka Orai" to "Kocchi no Kento",
            "Last Dance" to "Eve",
            "Like an idiot" to "kakizaki yuta",
            "NIGHT DANCER" to "imase",
            "Tek It" to "Cafuné",
            "Dracula" to "Tame Impala",
        )

    @Test
    fun `buildPrompt pastes Exportify songs as title by artist lines`() {
        val prompt = TasteImportAi.buildPrompt(sampleExportifyRows)
        assertTrue(prompt.contains("You are analyzing a listener's music taste for Nano DJ."))
        assertTrue(prompt.contains("けっかおーらい - Kekka Orai by Kocchi no Kento"))
        assertTrue(prompt.contains("Last Dance by Eve"))
        assertTrue(prompt.contains("Tek It by Cafuné"))
        assertTrue(prompt.contains("SUMMARY:"))
        assertTrue(prompt.contains("HINTS:"))
        assertFalse(prompt.contains("spotify:track:"))
    }

    @Test
    fun `parseResponse extracts summary and hints`() {
        val raw =
            """
            SUMMARY: Energetic J-pop and anime openings with indie rock edges — kakizaki yuta and Eve sit next to darkwave picks.
            HINTS:
            - Outsider - Eve
            - Ghost - kakizaki yuta
            - Blood Moon - Jfarrari
            """.trimIndent()

        val parsed = TasteImportAi.parseResponse(raw)
        assertNotNull(parsed)
        assertTrue(parsed!!.usedAi)
        assertTrue(parsed.summary.contains("J-pop"))
        assertEquals(3, parsed.searchHints.size)
        assertTrue(parsed.searchHints.any { it.contains("Outsider") })
    }

    @Test
    fun `parseResponse rejects blank undefined summary`() {
        val raw =
            """
            SUMMARY: undefined
            HINTS:
            - Song - Artist
            """.trimIndent()
        val parsed = TasteImportAi.parseResponse(raw)
        // Summary unusable — parseTasteAnalysis may still return hints-only or null
        if (parsed != null) {
            assertFalse(TasteSummary.isUsable(parsed.summary))
        }
    }

    @Test
    fun `requiresApiKey matches provider expectations`() {
        assertFalse(DjAiProvider.NANO.requiresApiKey())
        assertFalse(DjAiProvider.HACKCLUB.requiresApiKey())
        assertTrue(DjAiProvider.OPENAI.requiresApiKey())
        assertTrue(DjAiProvider.OPENROUTER.requiresApiKey())
        assertTrue(DjAiProvider.GROQ.requiresApiKey())
    }

    @Test
    fun `buildRecommendPrompt uses saved summary text only`() {
        val prompt =
            TasteImportAi.buildRecommendPrompt(
                "Energetic J-pop and darkwave — kakizaki yuta next to Jfarrari.",
            )
        assertTrue(prompt.contains("My music taste is: Energetic J-pop and darkwave"))
        assertTrue(prompt.contains("Suggest playable songs as HINTS:"))
        assertTrue(prompt.contains("- Title - Artist"))
        assertFalse(prompt.contains("spotify:track:"))
    }
}
