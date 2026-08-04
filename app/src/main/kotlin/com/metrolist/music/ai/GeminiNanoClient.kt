/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

/**
 * On-device Gemini Nano (via ML Kit GenAI Prompt / AICore) abstraction.
 * FOSS builds never include the ML Kit dependency; the default client uses
 * reflection and reports [GeminiNanoStatus.Unavailable] when classes are absent.
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

        fun get(): GeminiNanoClient =
            instance ?: synchronized(this) {
                instance ?: MlKitGeminiNanoClient().also { instance = it }
            }

        /** Test-only: replace the shared client. */
        fun setForTests(client: GeminiNanoClient?) {
            instance = client
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
 * Uses Gemini Nano when enabled and available; otherwise a simple heuristic.
 */
suspend fun analyzeSpotifyTaste(
    topArtists: List<String>,
    topTracks: List<Pair<String, String>>,
    enableNano: Boolean,
    client: GeminiNanoClient = GeminiNanoClient.get(),
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

    return parseTasteAnalysis(raw, usedAi = true) ?: heuristic.copy(
        summary = raw.take(500),
        usedAi = true,
    )
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
            .toList()
            .take(10)
    if (summaryMatch.isNullOrBlank() && hints.isEmpty()) return null
    return TasteAnalysisResult(
        summary = summaryMatch ?: raw.lines().firstOrNull { it.isNotBlank() }.orEmpty(),
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
