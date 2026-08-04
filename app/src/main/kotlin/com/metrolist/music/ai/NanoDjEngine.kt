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
            You are Nano DJ, an on-device AI radio host modeled directly on Spotify's AI DJ.
            You are NOT a corporate assistant — you're a laid-back music nerd hyped to be hanging
            out with the listener between songs. Talk like a real person: casual, warm, first
            person ("I", "I'm loving", "let's..."), with personality and a bit of energy that
            varies from chill to hyped depending on the vibe. In ONE natural spoken sentence,
            react to what just played (or the listener's taste if nothing has played yet) and
            tease what's coming up next — the way a DJ teases a transition, not a track listing.
            Never sound like a press release, never use emoji, never use markdown.

            Reply with EXACTLY this format (no markdown):
            TALK: <ONE short spoken sentence, Spotify-DJ style>
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

        val parsed = parseDjPick(raw, batchSize, usedAi = true)
        return if (parsed != null) {
            parsed.copy(commentary = interstitialOnly(parsed.commentary))
        } else {
            fallback.copy(
                commentary =
                    interstitialOnly(
                        raw.lines().firstOrNull { it.isNotBlank() }?.removePrefix("TALK:")?.trim()
                            ?: fallback.commentary,
                    ),
                usedAi = true,
            )
        }
    }

    suspend fun openingLine(
        context: DjContext,
        enableNano: Boolean,
        client: GeminiNanoClient = GeminiNanoClient.get(),
    ): String {
        val fallback =
            when {
                context.seedArtists.isNotEmpty() ->
                    "Hey — it's Nano DJ, and I'm already locking into your " +
                        "${context.seedArtists.take(2).joinToString(" & ")} energy, let's get into it."
                context.tasteSummary.isNotBlank() ->
                    "Hey — it's Nano DJ. I read up on your taste, and I've got a station just for you."
                else -> "Hey — it's Nano DJ, on-device and ready to build you a station from scratch."
            }
        if (!enableNano) return fallback
        val status = runCatching { client.checkStatus() }.getOrDefault(GeminiNanoStatus.Unavailable)
        if (status != GeminiNanoStatus.Available) return fallback

        val prompt =
            """
            You are Nano DJ, an on-device AI radio host modeled directly on Spotify's AI DJ.
            Write ONE short, warm, casual spoken intro (max 25 words) opening a personalized
            radio session, in the style of "Hey — it's Nano DJ...". Sound like a real music-nerd
            friend, first person, excited but relaxed, referencing the listener's taste.
            No quotes, no markdown, no bullet points, no emoji.
            Taste: ${context.tasteSummary.ifBlank { "eclectic listening" }}
            Artists: ${context.seedArtists.take(8).joinToString(", ")}
            """.trimIndent()

        return runCatching { client.generateContent(prompt) }.getOrNull()?.trim()?.let(::interstitialOnly)?.take(200)
            ?.ifBlank { null }
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
            commentary = talk ?: "Staying right in your lane — here's what's coming up next.",
            queries = queries,
            usedAi = usedAi,
        )
    }

    /**
     * Condenses DJ commentary down to a single TTS-friendly spoken sentence, matching how
     * Spotify's AI DJ speaks in short bursts between songs rather than long paragraphs.
     * Avoids truncating on common abbreviations (Dr., Mr., vs., etc.).
     */
    internal fun interstitialOnly(text: String): String {
        val cleaned = text.replace(Regex("""\s+"""), " ").trim()
        if (cleaned.isBlank()) return cleaned
        // Split on .!? only when not part of a short honorific / initialism before a capital letter
        // Protect honorifics mid-sentence (Dr. Dre, Mr. Brightside, etc.)
        val abbrev = Regex("""\b(?:Dr|Mr|Mrs|Ms|Jr|Sr|vs|etc|feat|ft)\.""", RegexOption.IGNORE_CASE)
        val protected = abbrev.replace(cleaned) { m -> m.value.replace('.', '\u0001') }
        val firstSentence =
            Regex("""^(.*?[.!?])(\s|$)""").find(protected)?.groupValues?.getOrNull(1)?.trim()
                ?.replace('\u0001', '.')
        val result = firstSentence.takeUnless { it.isNullOrBlank() } ?: cleaned.replace('\u0001', '.')
        return result.take(180)
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
                    "Keeping the ${context.seedArtists.first()} vibe going — Nano DJ's got more like this coming up."
                else -> "Nano DJ mixing your station live — fresh picks coming up right after this."
            }
        return DjPick(commentary = interstitialOnly(commentary), queries = queries, usedAi = false)
    }
}
