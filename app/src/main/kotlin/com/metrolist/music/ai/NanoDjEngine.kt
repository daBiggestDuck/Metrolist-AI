/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import timber.log.Timber

/**
 * On-device DJ brain powered by Gemini Nano — Metrolist's replacement for Spotify DJ.
 * Nano picks the next songs and writes short host commentary; YTM resolves playback.
 */
object NanoDjEngine {
    private const val TAG = "NanoDJ"

    data class DjPick(
        val commentary: String,
        /** "Title - Artist" style search queries for YouTube Music */
        val queries: List<String>,
        val usedAi: Boolean,
    )

    data class DjContext(
        val tasteSummary: String,
        val recentTitles: List<String>,
        val seedArtists: List<String>,
        val seedTracks: List<String>,
        val avoidTitles: List<String> = emptyList(),
    )

    suspend fun pickNext(
        context: DjContext,
        batchSize: Int = 4,
        enableNano: Boolean,
        client: GeminiNanoClient = GeminiNanoClient.get(),
    ): DjPick {
        val fallback = heuristicPick(context, batchSize)
        if (!enableNano) return fallback

        val status = runCatching { client.checkStatus() }.getOrDefault(GeminiNanoStatus.Unavailable)
        if (status != GeminiNanoStatus.Available) {
            Timber.tag(TAG).i("Nano unavailable (%s); using heuristic DJ picks", status)
            return fallback
        }

        val prompt =
            """
            You are Nano DJ, an on-device music radio host replacing Spotify DJ.
            Speak briefly like a friendly DJ (1-2 sentences), then suggest the next songs
            that fit the listener's taste but are not exact repeats of recent plays.

            Reply with EXACTLY this format (no markdown):
            TALK: <1-2 sentence DJ commentary>
            NEXT:
            - <Song Title> - <Artist>
            - <Song Title> - <Artist>
            (exactly $batchSize NEXT lines, real song names that likely exist)

            Listener taste: ${context.tasteSummary.ifBlank { "general popular music" }}
            Favorite artists: ${context.seedArtists.take(12).joinToString(", ").ifBlank { "(unknown)" }}
            Favorite tracks: ${context.seedTracks.take(12).joinToString("; ").ifBlank { "(unknown)" }}
            Recently played: ${context.recentTitles.take(10).joinToString("; ").ifBlank { "(none)" }}
            Avoid repeating: ${context.avoidTitles.take(20).joinToString("; ").ifBlank { "(none)" }}
            """.trimIndent()

        val raw = runCatching { client.generateContent(prompt) }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return fallback

        return parseDjPick(raw, batchSize, usedAi = true) ?: fallback.copy(
            commentary = raw.lines().firstOrNull { it.isNotBlank() }?.removePrefix("TALK:")?.trim()
                ?: fallback.commentary,
            usedAi = true,
        )
    }

    suspend fun openingLine(
        context: DjContext,
        enableNano: Boolean,
        client: GeminiNanoClient = GeminiNanoClient.get(),
    ): String {
        val fallback =
            when {
                context.seedArtists.isNotEmpty() ->
                    "Nano DJ on deck — locking into your ${context.seedArtists.take(2).joinToString(" & ")} energy."
                context.tasteSummary.isNotBlank() ->
                    "Nano DJ here. I read your Spotify taste — let's ride."
                else -> "Nano DJ online. Building a station just for you."
            }
        if (!enableNano) return fallback
        val status = runCatching { client.checkStatus() }.getOrDefault(GeminiNanoStatus.Unavailable)
        if (status != GeminiNanoStatus.Available) return fallback

        val prompt =
            """
            You are Nano DJ, replacing Spotify DJ with on-device Gemini Nano.
            Write ONE short spoken intro (max 25 words) for starting a personalized radio
            based on this taste. No quotes, no markdown, no bullet points.
            Taste: ${context.tasteSummary.ifBlank { "eclectic listening" }}
            Artists: ${context.seedArtists.take(8).joinToString(", ")}
            """.trimIndent()

        return runCatching { client.generateContent(prompt) }.getOrNull()?.trim()?.take(200)
            ?: fallback
    }

    internal fun parseDjPick(raw: String, batchSize: Int, usedAi: Boolean): DjPick? {
        val talk =
            Regex("""(?im)^TALK:\s*(.+)$""").find(raw)?.groupValues?.getOrNull(1)?.trim()
                ?: raw.lines().firstOrNull { it.isNotBlank() && !it.startsWith("NEXT", ignoreCase = true) }
                    ?.trim()
        val queries =
            Regex("""(?im)^[-*]\s*(.+)$""")
                .findAll(raw)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() && !it.equals("NEXT:", ignoreCase = true) }
                .distinct()
                .take(batchSize.coerceAtLeast(1))
                .toList()
        if (talk.isNullOrBlank() && queries.isEmpty()) return null
        return DjPick(
            commentary = talk ?: "Up next — staying in your lane.",
            queries = queries,
            usedAi = usedAi,
        )
    }

    internal fun heuristicPick(context: DjContext, batchSize: Int): DjPick {
        val queries =
            buildList {
                context.seedTracks.take(batchSize).forEach { add(it) }
                context.seedArtists.forEach { add("$it songs") }
                context.recentTitles.take(2).forEach { add("songs like $it") }
            }.map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(batchSize.coerceAtLeast(1))
                .ifEmpty {
                    listOf(
                        "popular songs",
                        "chill hits",
                        "indie favorites",
                        "feel good music",
                    ).take(batchSize)
                }

        val commentary =
            when {
                context.seedArtists.isNotEmpty() ->
                    "Keeping the ${context.seedArtists.first()} vibe going — here's what Nano DJ queued next."
                else -> "Nano DJ mixing your station — fresh picks coming up."
            }
        return DjPick(commentary = commentary, queries = queries, usedAi = false)
    }
}
