/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

/**
 * Shared helpers so taste prefs / UI never surface blank or JS-style junk like "undefined".
 */
object TasteSummary {
    private val UNUSABLE =
        setOf(
            "undefined",
            "null",
            "none",
            "n/a",
            "na",
            "(none)",
            "(unknown)",
            "unknown",
            "empty",
            "nil",
            "n.a.",
            "not available",
        )

    /** True when [raw] is a real human-readable taste blurb. */
    fun isUsable(raw: String?): Boolean {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return false
        if (t.length < 3) return false
        return t.lowercase() !in UNUSABLE
    }

    /** Returns trimmed summary or null when unusable. */
    fun sanitizeOrNull(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        return t.takeIf { isUsable(it) }
    }

    /** Prefer [preferred], else [fallback], else null. */
    fun coalesce(preferred: String?, fallback: String?): String? =
        sanitizeOrNull(preferred) ?: sanitizeOrNull(fallback)

    /**
     * Builds a guaranteed non-empty summary from artists/tracks (CSV / playlist import).
     * Never returns blank or "undefined".
     */
    fun fromArtistsAndTracks(
        artists: List<String>,
        tracks: List<Pair<String, String>>,
        sourceLabel: String = "imported playlist",
    ): String {
        val topArtists = artists.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
        val topTracks =
            tracks
                .map { (t, a) -> t.trim() to a.trim() }
                .filter { it.first.isNotBlank() }
                .distinctBy { it.first.lowercase() }
                .take(3)
        return when {
            topArtists.isNotEmpty() && topTracks.isNotEmpty() ->
                "Your $sourceLabel taste leans toward ${topArtists.joinToString(", ")}, " +
                    "with favorites like ${topTracks.joinToString(", ") { it.first }}."
            topArtists.isNotEmpty() ->
                "Your $sourceLabel taste leans toward ${topArtists.joinToString(", ")}."
            topTracks.isNotEmpty() ->
                "Your $sourceLabel favorites include ${
                    topTracks.joinToString(", ") { (t, a) ->
                        if (a.isNotBlank()) "$t by $a" else t
                    }
                }."
            else ->
                "Taste seeded from your $sourceLabel — keep listening so Metro DJ can refine it."
        }
    }
}
