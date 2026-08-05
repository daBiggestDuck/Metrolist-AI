/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import android.content.Context
import com.metrolist.music.constants.DjAiApiKey
import com.metrolist.music.constants.DjAiBaseUrlKey
import com.metrolist.music.constants.DjAiModelKey
import com.metrolist.music.constants.DjAiProviderKey
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.OpenRouterBaseUrlKey
import com.metrolist.music.constants.OpenRouterDefaultBaseUrl
import com.metrolist.music.constants.OpenRouterDefaultModel
import com.metrolist.music.constants.OpenRouterModelKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

/**
 * On-device Gemini Nano (via ML Kit GenAI Prompt / AICore) abstraction, also implemented by
 * [CloudDjLlmClient] for cloud DJ backends.
 * FOSS builds never include the ML Kit dependency; the Nano client uses reflection and reports
 * [GeminiNanoStatus.Unavailable] when classes are absent.
 */
interface GeminiNanoClient {
    suspend fun checkStatus(): GeminiNanoStatus

    /**
     * Downloads the on-device model when status is [GeminiNanoStatus.Downloadable].
     * [onProgress] receives total bytes downloaded so far.
     */
    suspend fun download(onProgress: (bytesDownloaded: Long) -> Unit = {})

    /**
     * Runs a text prompt. Returns null when the model is unavailable or generation fails.
     * Never throws for missing AICore / ML Kit.
     */
    suspend fun generateContent(prompt: String): String?

    companion object {
        @Volatile
        private var instance: GeminiNanoClient? = null

        @Volatile
        private var cachedFingerprint: String? = null

        fun get(context: Context): GeminiNanoClient {
            val app = context.applicationContext
            val prefs = app.dataStore
            val provider = DjAiProvider.fromId(prefs.get(DjAiProviderKey, DjAiProvider.NANO.id))
            var apiKey = prefs.get(DjAiApiKey, "")
            var model = prefs.get(DjAiModelKey, "")
            var baseUrl = prefs.get(DjAiBaseUrlKey, "").ifBlank { null }

            if (provider == DjAiProvider.OPENROUTER && apiKey.isBlank()) {
                apiKey = prefs.get(OpenRouterApiKey, "")
                if (model.isBlank()) {
                    model = prefs.get(OpenRouterModelKey, OpenRouterDefaultModel)
                }
                if (baseUrl.isNullOrBlank()) {
                    baseUrl = prefs.get(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl)
                }
            }

            if (model.isBlank()) {
                model = defaultModelFor(provider)
            }

            val fingerprint = "${provider.id}|$apiKey|$model|${baseUrl.orEmpty()}"
            instance?.let { cached ->
                if (cachedFingerprint == fingerprint) return cached
            }

            return synchronized(this) {
                instance?.let { cached ->
                    if (cachedFingerprint == fingerprint) return@synchronized cached
                }
                val created =
                    when (provider) {
                        DjAiProvider.NANO -> MlKitGeminiNanoClient()
                        else ->
                            CloudDjLlmClient(
                                provider = provider,
                                apiKey = apiKey,
                                model = model,
                                baseUrl = baseUrl,
                            )
                    }
                instance = created
                cachedFingerprint = fingerprint
                created
            }
        }

        /** Clear cached client so the next [get] rebuilds from current prefs. */
        fun invalidate() {
            synchronized(this) {
                instance = null
                cachedFingerprint = null
            }
        }

        /** Test-only: replace the shared client. */
        fun setForTests(client: GeminiNanoClient?) {
            synchronized(this) {
                instance = client
                cachedFingerprint = if (client == null) null else "test"
            }
        }

        fun defaultModelFor(provider: DjAiProvider): String =
            when (provider) {
                DjAiProvider.NANO -> ""
                DjAiProvider.OPENAI -> "gpt-4o-mini"
                DjAiProvider.ANTHROPIC -> "claude-haiku-4-5-20251001"
                DjAiProvider.HUGGINGFACE -> "meta-llama/Meta-Llama-3-8B-Instruct"
                DjAiProvider.OPENROUTER -> OpenRouterDefaultModel
                DjAiProvider.GROQ -> "llama-3.3-70b-versatile"
                DjAiProvider.HACKCLUB -> "qwen/qwen3-32b"
            }
    }
}

enum class GeminiNanoStatus {
    Unavailable,
    Downloadable,
    Downloading,
    Available,
    Error,
}

data class TasteAnalysisResult(
    val summary: String,
    val searchHints: List<String>,
    val usedAi: Boolean,
)

/**
 * Builds a taste summary (and optional YTM search hints) from Spotify top artists/tracks.
 * Uses the configured DJ AI backend when enabled and available; otherwise a simple heuristic.
 */
suspend fun analyzeSpotifyTaste(
    topArtists: List<String>,
    topTracks: List<Pair<String, String>>,
    enableNano: Boolean,
    client: GeminiNanoClient,
): TasteAnalysisResult {
    val heuristic = heuristicTasteAnalysis(topArtists, topTracks)
    if (!enableNano) return heuristic

    val status = runCatching { client.checkStatus() }.getOrDefault(GeminiNanoStatus.Unavailable)
    if (status != GeminiNanoStatus.Available) return heuristic

    val artistLine = topArtists.take(15).joinToString(", ").ifBlank { "(none)" }
    val trackLine =
        topTracks.take(20).joinToString("; ") { (title, artists) -> "$title by $artists" }
            .ifBlank { "(none)" }

    val prompt =
        """
        You are Nano DJ's taste analyst (on-device Gemini Nano replacing Spotify DJ).
        Summarize the listener's taste for a personalized radio host, and list song search
        queries Nano DJ can play on YouTube Music.
        Reply with EXACTLY this format (no markdown):
        SUMMARY: <2-3 sentence taste summary for a DJ>
        HINTS:
        - <Song Title> - <Artist>
        - <Song Title> - <Artist>
        (up to 8 HINTS lines of real playable songs)

        Top artists: $artistLine
        Top tracks: $trackLine
        """.trimIndent()

    val raw = runCatching { client.generateContent(prompt) }.getOrNull()?.trim().orEmpty()
    if (raw.isBlank()) return heuristic

    val parsed = parseTasteAnalysis(raw, usedAi = true)
    val usable = TasteSummary.sanitizeOrNull(parsed?.summary)
    return when {
        parsed != null && usable != null ->
            parsed.copy(summary = usable)
        usable == null && parsed != null && parsed.searchHints.isNotEmpty() ->
            heuristic.copy(searchHints = parsed.searchHints, usedAi = true)
        else ->
            heuristic.copy(
                summary = TasteSummary.sanitizeOrNull(raw.take(500)) ?: heuristic.summary,
                usedAi = TasteSummary.isUsable(raw.take(500)),
            )
    }
}

internal fun heuristicTasteAnalysis(
    topArtists: List<String>,
    topTracks: List<Pair<String, String>>,
): TasteAnalysisResult {
    val artists = topArtists.take(5)
    val summary =
        when {
            artists.isEmpty() && topTracks.isEmpty() ->
                "Not enough Spotify listening history to summarize taste yet."
            artists.isNotEmpty() ->
                "Your Spotify taste leans toward ${artists.joinToString(", ")}" +
                    if (topTracks.isNotEmpty()) {
                        ", with frequent listens like ${topTracks.take(3).joinToString(", ") { it.first }}."
                    } else {
                        "."
                    }
            else ->
                "Your recent Spotify favorites include ${topTracks.take(5).joinToString(", ") { it.first }}."
        }
    val hints =
        buildList {
            artists.take(5).forEach { add("$it songs") }
            topTracks.take(5).forEach { (title, artist) -> add("$title $artist") }
        }.distinct().take(10)
    return TasteAnalysisResult(summary = summary, searchHints = hints, usedAi = false)
}

internal fun parseTasteAnalysis(raw: String, usedAi: Boolean): TasteAnalysisResult? {
    val summaryMatch =
        Regex("""(?im)^SUMMARY:\s*(.+)$""").find(raw)?.groupValues?.getOrNull(1)?.trim()
    val hints =
        Regex("""(?im)^[-*]\s*(.+)$""")
            .findAll(raw)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() && !it.equals("HINTS:", ignoreCase = true) }
            .filter { TasteSummary.isUsable(it) || it.length >= 3 }
            .toList()
            .take(10)
    // Do not fall back to the raw "SUMMARY: …" line — that can keep "undefined" as text.
    val summary = TasteSummary.sanitizeOrNull(summaryMatch).orEmpty()
    if (!TasteSummary.isUsable(summary) && hints.isEmpty()) return null
    return TasteAnalysisResult(
        summary = summary,
        searchHints = hints,
        usedAi = usedAi,
    )
}

internal fun mapFeatureStatus(statusCode: Int): GeminiNanoStatus =
    when (statusCode) {
        0 -> GeminiNanoStatus.Unavailable
        1 -> GeminiNanoStatus.Downloadable
        2 -> GeminiNanoStatus.Downloading
        3 -> GeminiNanoStatus.Available
        else -> GeminiNanoStatus.Error
    }
