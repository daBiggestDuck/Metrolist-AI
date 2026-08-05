/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.metrolist.music.utils.CsvImportColumnDetector
import com.metrolist.music.utils.CsvParser
import com.metrolist.music.utils.CsvPlaylistParser
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream

data class SpotifyFileParseResult(
    val tracks: List<Pair<String, String>>,
    val playlistName: String?,
)

/**
 * Parses a user-picked playlist / taste export (plain text, CSV, or JSON) into
 * title–artist pairs for Nano DJ without requiring Spotify Premium or OAuth.
 */
object SpotifyFileTasteImporter {
    private const val TAG = "SpotifyFileImport"
    private const val MAX_TRACKS = 500
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun parse(context: Context, uri: Uri): SpotifyFileParseResult {
        val displayName = queryDisplayName(context, uri)
        val text =
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) {
                        throw IllegalArgumentException(
                            "File is too large (max ${MAX_BYTES / (1024 * 1024)} MB)",
                        )
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray().toString(Charsets.UTF_8)
            } ?: throw IllegalArgumentException("Could not read selected file")

        val trimmed = text.trimStart('\uFEFF').trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("File is empty")
        }

        val tracks =
            when {
                looksLikeJson(trimmed) -> parseJson(trimmed)
                else -> parseDelimitedOrPlain(trimmed)
            }
                .asSequence()
                .map { (title, artist) -> title.trim() to artist.trim() }
                .filter { it.first.isNotBlank() }
                .distinctBy { "${it.first.lowercase()}|${it.second.lowercase()}" }
                .take(MAX_TRACKS)
                .toList()

        if (tracks.isEmpty()) {
            throw IllegalArgumentException(
                "No tracks found. Use lines like “Title - Artist”, CSV with Track Name / Artist Name, or JSON with title/artist fields.",
            )
        }

        val playlistName =
            displayName
                ?.substringBeforeLast('.')
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        Timber.tag(TAG).d("Parsed %d tracks from %s", tracks.size, displayName ?: uri)
        return SpotifyFileParseResult(tracks = tracks, playlistName = playlistName)
    }

    private fun looksLikeJson(text: String): Boolean {
        val first = text.firstOrNull { !it.isWhitespace() } ?: return false
        return first == '{' || first == '['
    }

    private fun parseJson(text: String): List<Pair<String, String>> {
        return try {
            when (val first = text.first { !it.isWhitespace() }) {
                '[' -> parseJsonArray(JSONArray(text))
                '{' -> parseJsonObject(JSONObject(text))
                else -> {
                    Timber.tag(TAG).w("Unexpected JSON start: %s", first)
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "JSON parse failed; falling back to line parse")
            parseDelimitedOrPlain(text)
        }
    }

    private fun parseJsonObject(obj: JSONObject): List<Pair<String, String>> {
        // Simple single track
        extractTrackPair(obj)?.let { return listOf(it) }

        // Common wrappers: playlists, tracks, items, songs
        for (key in listOf("tracks", "items", "songs", "playlist", "playlists")) {
            when (val child = obj.opt(key)) {
                is JSONArray -> {
                    val fromArray = parseJsonArray(child)
                    if (fromArray.isNotEmpty()) return fromArray
                }
                is JSONObject -> {
                    val nested = parseJsonObject(child)
                    if (nested.isNotEmpty()) return nested
                }
            }
        }

        // Spotify Account Data: walk for StreamingHistory-style arrays
        val collected = mutableListOf<Pair<String, String>>()
        collectTracksRecursive(obj, collected)
        return collected
    }

    private fun parseJsonArray(arr: JSONArray): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            when (val el = arr.opt(i)) {
                is JSONObject -> {
                    val pair =
                        extractTrackPair(el)
                            ?: (el.optJSONObject("track")
                                ?: el.optJSONObject("item")
                                ?: el.optJSONObject("trackMetadata"))
                                ?.let { extractTrackPair(it) }
                    if (pair != null) out += pair
                }
                is JSONArray -> out += parseJsonArray(el)
                is String -> parseLine(el)?.let { out += it }
            }
        }
        if (out.isEmpty()) {
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                collectTracksRecursive(el, out)
            }
        }
        return out
    }

    private fun collectTracksRecursive(
        node: Any?,
        out: MutableList<Pair<String, String>>,
        depth: Int = 0,
    ) {
        if (depth > 8 || out.size >= MAX_TRACKS) return
        when (node) {
            is JSONObject -> {
                extractTrackPair(node)?.let {
                    out += it
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectTracksRecursive(node.opt(keys.next()), out, depth + 1)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectTracksRecursive(node.opt(i), out, depth + 1)
                }
            }
        }
    }

    private fun extractTrackPair(obj: JSONObject): Pair<String, String>? {
        val title =
            firstNonBlank(
                obj,
                "trackName",
                "track_name",
                "Track Name",
                "title",
                "name",
                "song",
                "songName",
                "master_metadata_track_name",
            ) ?: return null

        // Skip non-track Spotify objects that have a "name" but aren't songs
        val type = obj.optString("type")
        if (type.isNotBlank() && type != "track") return null

        val artist =
            firstNonBlank(
                obj,
                "artistName",
                "artist_name",
                "Artist Name",
                "Artist Name(s)",
                "artist",
                "artists",
                "master_metadata_album_artist_name",
            ) ?: artistsFromArray(obj) ?: ""

        // Ignore playlist/album-only objects that only have a name
        if (artist.isBlank() && !obj.has("trackName") && !obj.has("title") &&
            !obj.has("song") && !obj.has("master_metadata_track_name")
        ) {
            // "name" alone on a playlist object — skip
            if (obj.has("tracks") || obj.has("items") || obj.has("owner") || obj.has("followers")) {
                return null
            }
        }

        return title to artist
    }

    private fun artistsFromArray(obj: JSONObject): String? {
        val arr = obj.optJSONArray("artists") ?: return null
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            when (val a = arr.opt(i)) {
                is JSONObject -> a.optString("name").takeIf { it.isNotBlank() }?.let { names += it }
                is String -> if (a.isNotBlank()) names += a
            }
        }
        return names.joinToString(", ").takeIf { it.isNotBlank() }
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.opt(key) ?: continue
            when (value) {
                is String -> if (value.isNotBlank()) return value.trim()
                is JSONArray -> {
                    val joined = artistsFromArray(JSONObject().put("artists", value))
                    if (!joined.isNullOrBlank()) return joined
                }
                is JSONObject -> {
                    val name = value.optString("name")
                    if (name.isNotBlank()) return name.trim()
                }
            }
        }
        return null
    }

    private fun parseDelimitedOrPlain(text: String): List<Pair<String, String>> {
        val lines =
            text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
        if (lines.isEmpty()) return emptyList()

        val previewRows = lines.take(6).map { CsvParser.parseLine(it) }
        val hasHeader =
            previewRows.isNotEmpty() &&
                CsvImportColumnDetector.looksLikeHeaderRow(previewRows.first())
        val mapping = CsvImportColumnDetector.detect(previewRows, hasHeader)

        if (hasHeader && mapping.titleColumnIndex >= 0 && mapping.artistColumnIndex >= 0) {
            val parsed = CsvPlaylistParser.parse(lines, mapping)
            if (parsed.tracks.isNotEmpty()) {
                Timber.tag(TAG).d("Parsed %d tracks via Exportify-style CSV headers", parsed.tracks.size)
                return parsed.tracks
            }
        }

        // Two-column CSV without known headers: Title,Artist
        val header = lines.first()
        val headerCols = CsvParser.parseLine(header).map { CsvParser.normalizeHeader(it) }
        if (headerCols.size >= 2 && !header.contains(" - ") && header.contains(',')) {
            val sample = lines.take(5).map { CsvParser.parseLine(it) }
            if (sample.all { it.size >= 2 }) {
                val looksLikeHeader =
                    headerCols[0] in setOf("title", "track", "song", "name") ||
                        headerCols[1] in setOf("artist", "artists")
                val dataLines = if (looksLikeHeader) lines.drop(1) else lines
                return dataLines.mapNotNull { line ->
                    val cols = CsvParser.parseLine(line)
                    val title = cols.getOrNull(0)?.trim().orEmpty()
                    val artist = cols.drop(1).joinToString(", ").trim()
                    if (title.isBlank()) null else title to artist
                }
            }
        }

        return lines.mapNotNull { parseLine(it) }
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val trimmed = line.trim().trimStart('\uFEFF')
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("#") || trimmed.startsWith("//")) return null
        // Skip obvious CSV header leftovers
        if (trimmed.equals("Track Name,Artist Name", ignoreCase = true)) return null
        // Never treat Exportify Track URI rows as "Title - Artist" when headers were missed.
        if (CsvParser.isSpotifyTrackUri(trimmed.substringBefore(','))) return null

        val dashSep =
            when {
                " — " in trimmed -> " — "
                " – " in trimmed -> " – "
                " - " in trimmed -> " - "
                else -> null
            }
        if (dashSep != null && !trimmed.contains(',')) {
            val idx = trimmed.indexOf(dashSep)
            val title = trimmed.substring(0, idx).trim()
            val artist = trimmed.substring(idx + dashSep.length).trim()
            if (title.isNotBlank() && !CsvParser.isSpotifyTrackUri(title)) return title to artist
        }

        if (',' in trimmed) {
            val cols = CsvParser.parseLine(trimmed)
            if (cols.size >= 2) {
                val title = cols[0].trim()
                val artist = cols.drop(1).joinToString(", ").trim()
                if (title.isNotBlank() && !CsvParser.isSpotifyTrackUri(title)) return title to artist
            }
        }

        // Title only
        if (CsvParser.isSpotifyTrackUri(trimmed)) return null
        return trimmed to ""
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "DISPLAY_NAME query failed")
            null
        }
    }
}
