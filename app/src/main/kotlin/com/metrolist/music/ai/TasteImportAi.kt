/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import android.content.Context
import com.metrolist.music.constants.DjAiApiKey
import com.metrolist.music.constants.DjAiProviderKey
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Shared taste-import AI: paste song lines into a prompt, call the selected DJ provider,
 * parse SUMMARY/HINTS. Throws [TasteImportException] when AI is enabled but cannot run —
 * never silently pretends AI succeeded.
 */
object TasteImportAi {
    private const val TAG = "TasteImportAi"
    private const val MAX_PROMPT_TRACKS = 80

    fun buildPrompt(tracks: List<Pair<String, String>>, maxTracks: Int = MAX_PROMPT_TRACKS): String {
        val lines =
            tracks
                .asSequence()
                .map { (title, artist) -> title.trim() to artist.trim() }
                .filter { it.first.isNotBlank() }
                .distinctBy { "${it.first.lowercase()}|${it.second.lowercase()}" }
                .take(maxTracks)
                .joinToString("\n") { (title, artist) ->
                    if (artist.isNotBlank()) "$title by $artist" else title
                }

        return """
            You are analyzing a listener's music taste for Nano DJ.
            Given these songs:
            $lines

            Reply EXACTLY:
            SUMMARY: <2-3 sentence taste summary>
            HINTS:
            - <Song> - <Artist>
            - <Song> - <Artist>
            (up to 8 HINTS of real playable songs matching this taste)
            """.trimIndent()
    }

    fun parseResponse(raw: String): TasteAnalysisResult? = parseTasteAnalysis(raw, usedAi = true)

    /**
     * When DJ AI is disabled ([EnableGeminiNanoKey] false), returns a heuristic profile
     * with [TasteAnalysisResult.usedAi] = false.
     * When enabled, requires a real [GeminiNanoClient.generateContent] result or throws.
     */
    suspend fun analyzeTracks(
        context: Context,
        tracks: List<Pair<String, String>>,
        client: GeminiNanoClient = GeminiNanoClient.get(context),
    ): TasteAnalysisResult {
        val cleaned =
            tracks
                .map { (t, a) -> t.trim() to a.trim() }
                .filter { it.first.isNotBlank() }
                .distinctBy { "${it.first.lowercase()}|${it.second.lowercase()}" }

        if (cleaned.isEmpty()) {
            throw TasteImportException(
                TasteImportFailReason.NO_TRACKS,
                "No tracks to analyze",
            )
        }

        val prefs = context.dataStore.data.first()
        val aiEnabled = prefs[EnableGeminiNanoKey] ?: true
        val provider = DjAiProvider.fromId(prefs[DjAiProviderKey])
        val apiKey =
            prefs[DjAiApiKey]
                ?.takeIf { it.isNotBlank() }
                ?: prefs[OpenRouterApiKey]?.takeIf { it.isNotBlank() }.orEmpty()

        if (!aiEnabled) {
            Timber.tag(TAG).i(
                "DJ AI disabled — heuristic taste from %d tracks (provider=%s)",
                cleaned.size,
                provider.id,
            )
            val artists = SpotifyImportArtistDerive.derive(cleaned)
            return heuristicTasteAnalysis(artists, cleaned).copy(usedAi = false)
        }

        Timber.tag(TAG).i(
            "DJ AI taste import: %d tracks → provider=%s",
            cleaned.size,
            provider.id,
        )

        if (provider != DjAiProvider.NANO && provider.requiresApiKey() && apiKey.isBlank()) {
            throw TasteImportException(
                TasteImportFailReason.NO_API_KEY,
                "No API key for ${provider.displayName}. Set it in Settings → Playback → Nano DJ.",
            )
        }

        val status =
            runCatching { client.checkStatus() }
                .onFailure { Timber.tag(TAG).w(it, "checkStatus failed") }
                .getOrDefault(GeminiNanoStatus.Unavailable)

        when (status) {
            GeminiNanoStatus.Available -> Unit
            GeminiNanoStatus.Downloadable ->
                throw TasteImportException(
                    TasteImportFailReason.NANO_DOWNLOADABLE,
                    "Gemini Nano model is not downloaded. Download it in Settings → Playback → Nano DJ, or switch DJ provider.",
                )
            GeminiNanoStatus.Downloading ->
                throw TasteImportException(
                    TasteImportFailReason.NANO_UNAVAILABLE,
                    "Gemini Nano is still downloading. Wait, or switch DJ provider in Settings → Playback → Nano DJ.",
                )
            GeminiNanoStatus.Unavailable, GeminiNanoStatus.Error -> {
                val detail =
                    if (provider == DjAiProvider.NANO) {
                        "Gemini Nano unavailable (status=$status). Switch DJ provider or enable AICore."
                    } else if (provider.requiresApiKey() && apiKey.isBlank()) {
                        "No API key for ${provider.displayName}. Set it in Settings → Playback → Nano DJ."
                    } else {
                        "${provider.displayName} is not ready (status=$status)."
                    }
                throw TasteImportException(
                    if (provider.requiresApiKey() && apiKey.isBlank()) {
                        TasteImportFailReason.NO_API_KEY
                    } else {
                        TasteImportFailReason.NANO_UNAVAILABLE
                    },
                    detail,
                )
            }
        }

        val prompt = buildPrompt(cleaned)
        Timber.tag(TAG).d("Calling generateContent (%d chars, %d songs)", prompt.length, cleaned.size)

        val raw =
            try {
                client.generateContent(prompt)?.trim().orEmpty()
            } catch (e: DjAiException) {
                Timber.tag(TAG).e(e, "generateContent DjAiException")
                throw TasteImportException(
                    when (e.kind) {
                        DjAiException.Kind.NO_API_KEY -> TasteImportFailReason.NO_API_KEY
                        DjAiException.Kind.HTTP -> TasteImportFailReason.HTTP_ERROR
                        DjAiException.Kind.NETWORK -> TasteImportFailReason.HTTP_ERROR
                        else -> TasteImportFailReason.EMPTY_RESPONSE
                    },
                    e.message ?: "DJ AI request failed",
                    e,
                )
            } catch (e: TasteImportException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "generateContent failed")
                throw TasteImportException(
                    TasteImportFailReason.HTTP_ERROR,
                    "DJ AI request failed: ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }

        if (raw.isBlank()) {
            throw TasteImportException(
                TasteImportFailReason.EMPTY_RESPONSE,
                "DJ AI returned empty — check API key/provider in Settings → Playback → Nano DJ.",
            )
        }

        Timber.tag(TAG).i("generateContent returned %d chars", raw.length)

        val parsed = parseResponse(raw)
        val usable = TasteSummary.sanitizeOrNull(parsed?.summary)
        if (parsed == null || usable == null) {
            throw TasteImportException(
                TasteImportFailReason.PARSE_FAILED,
                "DJ AI response could not be parsed into a taste summary. Try again or switch provider.",
            )
        }

        return parsed.copy(summary = usable, usedAi = true)
    }
}

enum class TasteImportFailReason {
    NO_TRACKS,
    NO_API_KEY,
    NANO_UNAVAILABLE,
    NANO_DOWNLOADABLE,
    EMPTY_RESPONSE,
    HTTP_ERROR,
    PARSE_FAILED,
}

class TasteImportException(
    val reason: TasteImportFailReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class DjAiException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        NO_API_KEY,
        HTTP,
        NETWORK,
        EMPTY,
        OTHER,
    }
}

/** Lightweight top-artist derive for heuristic path without depending on SpotifyImportManager. */
internal object SpotifyImportArtistDerive {
    fun derive(tracks: List<Pair<String, String>>, limit: Int = 20): List<String> {
        val counts = linkedMapOf<String, Int>()
        tracks.forEach { (_, artistStr) ->
            artistStr
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { name ->
                    counts[name] = (counts[name] ?: 0) + 1
                }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
    }
}

fun DjAiProvider.requiresApiKey(): Boolean =
    when (this) {
        DjAiProvider.NANO -> false
        DjAiProvider.HACKCLUB -> false
        else -> true
    }
