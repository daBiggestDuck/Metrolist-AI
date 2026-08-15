/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import timber.log.Timber
import kotlin.random.Random

/**
 * On-device DJ brain powered by Gemini Nano — Metrolist's replacement for Spotify DJ.
 * Nano picks the next songs and writes short host commentary; YTM resolves playback.
 * Uses continuous listening taste + mood/category lanes (chill, hype, focus, …).
 */
object NanoDjEngine {
    private const val TAG = "NanoDJ"

    data class DjPick(
        val commentary: String,
        /** "Title - Artist" style search queries for YouTube Music */
        val queries: List<String>,
        val usedAi: Boolean,
        val lane: ListeningTasteTracker.DjLane = ListeningTasteTracker.DjLane.ARTIST_RADIO,
    )

    data class DjContext(
        val tasteSummary: String,
        val recentTitles: List<String>,
        val seedArtists: List<String>,
        val seedTracks: List<String>,
        val avoidTitles: List<String> = emptyList(),
        val categories: List<String> = emptyList(),
        val lane: ListeningTasteTracker.DjLane = ListeningTasteTracker.DjLane.ARTIST_RADIO,
        val skipPressure: Int = 0,
        /** Changes the order of a DJ session without changing the persisted taste profile. */
        val randomSeed: Long = 0L,
    )

    suspend fun pickNext(
        context: DjContext,
        batchSize: Int = 4,
        enableNano: Boolean,
        client: GeminiNanoClient,
    ): DjPick {
        val fallback = heuristicPick(context, batchSize)
        if (!enableNano) return fallback

        val status = runCatching { client.checkStatus() }.getOrDefault(GeminiNanoStatus.Unavailable)
        if (status != GeminiNanoStatus.Available) {
            Timber.tag(TAG).i("Nano unavailable (%s); using heuristic DJ picks", status)
            return fallback
        }

        val lane = context.lane
        val prompt =
            """
            You are Metro DJ, an on-device AI radio host modeled directly on Spotify's AI DJ.
            You are NOT a corporate assistant — you're a laid-back music nerd hyped to be hanging
            out with the listener between songs. Talk like a real person: casual, warm, first
            person ("I", "I'm loving", "let's..."), with personality and a bit of energy that
            matches the active lane (${lane.displayName}). In ONE natural spoken sentence,
            react to what just played (or the listener's taste if nothing has played yet) and
            tease what's coming up next — the way a DJ teases a transition, not a track listing.
            Explicitly lean into the "${lane.displayName}" lane/category set.
            Never sound like a press release, never use emoji, never use markdown.

            Reply with EXACTLY this format (no markdown):
            TALK: <ONE short spoken sentence referencing the ${lane.displayName} lane, Spotify-DJ style>
            NEXT:
            - <Song Title> - <Artist>
            - <Song Title> - <Artist>
            (exactly $batchSize NEXT lines, real song names that fit the ${lane.displayName} lane)
            Every NEXT line must be a specific, playable song in the form Title - Artist. Never
            output a playlist, radio, mix, genre, artist-only, or "songs like" query. Favor a
            coherent arc: one familiar anchor at most, then adjacent artists, deep cuts, or
            rediscoveries with a clear reason they belong in this set.

            Listener taste: ${context.tasteSummary.ifBlank { "general popular music" }}
            Active lane: ${lane.id} (${lane.displayName})
            Mood categories: ${context.categories.take(6).joinToString(", ").ifBlank { lane.displayName }}
            Favorite artists: ${context.seedArtists.take(12).joinToString(", ").ifBlank { "(unknown)" }}
            Favorite tracks: ${context.seedTracks.take(12).joinToString("; ").ifBlank { "(unknown)" }}
            Recently played: ${context.recentTitles.take(10).joinToString("; ").ifBlank { "(none)" }}
            Avoid repeating: ${context.avoidTitles.take(20).joinToString("; ").ifBlank { "(none)" }}
            Recent skip pressure: ${context.skipPressure} (if this is high, deliberately change
            the angle/category instead of continuing the same sound)
            Do not simply replay the listener's favorites in their existing order. Prefer adjacent
            artists, deep cuts, rediscoveries, and a little discovery within this lane; use at most
            one exact item from Favorite tracks unless the listener explicitly requests favorites.
            """.trimIndent()

        val raw = runCatching { client.generateContent(prompt) }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return fallback

        val parsed = parseDjPick(raw, batchSize, usedAi = true, lane = lane)
        return if (parsed != null && parsed.queries.isNotEmpty()) {
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
        client: GeminiNanoClient,
    ): String {
        val lane = context.lane
        val fallback =
            when {
                context.seedArtists.isNotEmpty() && lane == ListeningTasteTracker.DjLane.ARTIST_RADIO ->
                    "Hey — it's Metro DJ, locking into your " +
                        "${context.seedArtists.take(2).joinToString(" & ")} lane, let's get into it."
                context.seedArtists.isNotEmpty() ->
                    "Hey — it's Metro DJ, sliding into a ${lane.displayName} set with your " +
                        "${context.seedArtists.take(2).joinToString(" & ")} energy."
                context.tasteSummary.isNotBlank() ->
                    "Hey — it's Metro DJ. I read your live taste and I'm running a ${lane.displayName} station for you."
                else -> "Hey — it's Metro DJ, on-device and ready to build you a ${lane.displayName} station."
            }
        if (!enableNano) return fallback
        val status = runCatching { client.checkStatus() }.getOrDefault(GeminiNanoStatus.Unavailable)
        if (status != GeminiNanoStatus.Available) return fallback

        val prompt =
            """
            You are Metro DJ, an on-device AI radio host modeled directly on Spotify's AI DJ.
            Write ONE short, warm, casual spoken intro (max 25 words) opening a personalized
            radio session in the "${lane.displayName}" lane, in the style of "Hey — it's Metro DJ...".
            Sound like a real music-nerd friend, first person, excited but relaxed, referencing
            the listener's taste and the active category/lane.
            No quotes, no markdown, no bullet points, no emoji.
            Taste: ${context.tasteSummary.ifBlank { "eclectic listening" }}
            Lane: ${lane.displayName}
            Categories: ${context.categories.take(4).joinToString(", ")}
            Artists: ${context.seedArtists.take(8).joinToString(", ")}
            """.trimIndent()

        return runCatching { client.generateContent(prompt) }.getOrNull()?.trim()?.let(::interstitialOnly)?.take(200)
            ?.ifBlank { null }
            ?: fallback
    }

    internal fun parseDjPick(
        raw: String,
        batchSize: Int,
        usedAi: Boolean,
        lane: ListeningTasteTracker.DjLane = ListeningTasteTracker.DjLane.ARTIST_RADIO,
    ): DjPick? {
        val talk =
            Regex("""(?im)^TALK:\s*(.+)$""").find(raw)?.groupValues?.getOrNull(1)?.trim()
                ?: raw.lines().firstOrNull { it.isNotBlank() && !it.startsWith("NEXT", ignoreCase = true) }
                    ?.trim()
        val queries =
            Regex("""(?im)^[-*]\s*(.+)$""")
                .findAll(raw)
                .map { it.groupValues[1].trim() }
                .filter {
                    it.isNotBlank() &&
                        !it.equals("NEXT:", ignoreCase = true) &&
                        (" - " in it || " — " in it)
                }
                .distinct()
                .take(batchSize.coerceAtLeast(1))
                .toList()
        if (talk.isNullOrBlank() && queries.isEmpty()) return null
        return DjPick(
            commentary = talk ?: "Staying in your ${lane.displayName} lane — here's what's coming up next.",
            queries = queries,
            usedAi = usedAi,
            lane = lane,
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
        val abbrev = Regex("""\b(?:Dr|Mr|Mrs|Ms|Jr|Sr|vs|etc|feat|ft)\.""", RegexOption.IGNORE_CASE)
        val protected = abbrev.replace(cleaned) { m -> m.value.replace('.', '\u0001') }
        val firstSentence =
            Regex("""^(.*?[.!?])(\s|$)""").find(protected)?.groupValues?.getOrNull(1)?.trim()
                ?.replace('\u0001', '.')
        val result = firstSentence.takeUnless { it.isNullOrBlank() } ?: cleaned.replace('\u0001', '.')
        return result.take(180)
    }

    internal fun heuristicPick(context: DjContext, batchSize: Int): DjPick {
        val lane = context.lane
        val laneQueries =
            when (lane) {
                ListeningTasteTracker.DjLane.ARTIST_RADIO ->
                    context.seedArtists.take(6).flatMap {
                        listOf(
                            "$it radio",
                            "songs like $it",
                            "$it deep cuts",
                            "new artists like $it",
                        )
                    } +
                        listOf(
                            "new releases for my taste",
                            "rediscoveries from my taste",
                            "deep cuts and hidden gems",
                            "throwback songs I may have forgotten",
                            "similar artists discovery",
                        )
                else ->
                    lane.searchHints +
                        context.categories.take(4).map { "$it ${lane.displayName} playlist" } +
                        context.seedArtists.take(4).map { "$it ${lane.displayName} mix" }
            }

        val feedbackQueries =
            if (context.skipPressure >= 2) {
                listOf("different ${lane.displayName} discoveries", "new music outside my usual favorites")
            } else {
                emptyList()
            }
        val random = Random(context.randomSeed + context.recentTitles.size * 31L)
        val queries =
            buildList {
                addAll(laneQueries)
                addAll(feedbackQueries)
                context.recentTitles.takeLast(3).forEach { add("songs similar to $it") }
                // Keep only a small seed sample; putting every favorite first made every
                // session resolve the same playlist in the same order.
                context.seedTracks.shuffled(random).take(2).forEach { add(it) }
                addAll(lane.searchHints)
            }.map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .shuffled(random)
                .take(batchSize.coerceAtLeast(1))
                .ifEmpty {
                    (lane.searchHints + listOf("new music discovery", "songs you may like", "deep cuts"))
                        .shuffled(random)
                        .take(batchSize)
                }

        val commentary =
            when {
                context.skipPressure >= 2 ->
                    "I caught the skips — changing the angle while staying in your ${lane.displayName} lane."
                lane != ListeningTasteTracker.DjLane.ARTIST_RADIO ->
                    "Keeping this ${lane.displayName} set rolling — Metro DJ's got more in that lane coming up."
                context.seedArtists.isNotEmpty() ->
                    "Keeping the ${context.seedArtists.first()} vibe going — Metro DJ's got more like this coming up."
                else -> "Metro DJ mixing your station live — fresh picks coming up right after this."
            }
        return DjPick(
            commentary = interstitialOnly(commentary),
            queries = queries,
            usedAi = false,
            lane = lane,
        )
    }
}
