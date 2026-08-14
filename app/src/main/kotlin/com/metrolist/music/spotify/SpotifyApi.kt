/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

data class SpotifyUser(
    val id: String,
    val displayName: String,
)

data class SpotifyArtist(
    val id: String,
    val name: String,
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<String>,
) {
    val artistsJoined: String get() = artists.joinToString(", ")
}

class SpotifyApiException(
    val status: Int,
    message: String,
) : IOException(message)

data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val trackCount: Int,
    val ownerName: String?,
    val isCollaborative: Boolean,
)

class SpotifyApi(
    private val httpClient: HttpClient = defaultClient(),
) {
    suspend fun getMe(accessToken: String): SpotifyUser {
        val json = getJson("https://api.spotify.com/v1/me", accessToken)
        return SpotifyUser(
            id = json.getString("id"),
            displayName = json.optString("display_name").ifBlank { json.getString("id") },
        )
    }

    suspend fun getTopArtists(
        accessToken: String,
        timeRange: String = "medium_term",
        limit: Int = 20,
    ): List<SpotifyArtist> {
        val json =
            getJson("https://api.spotify.com/v1/me/top/artists", accessToken) {
                parameter("time_range", timeRange)
                parameter("limit", limit.coerceIn(1, 50))
            }
        return json.optJSONArray("items").toArtistList()
    }

    suspend fun getTopTracks(
        accessToken: String,
        timeRange: String = "medium_term",
        limit: Int = 50,
    ): List<SpotifyTrack> {
        val json =
            getJson("https://api.spotify.com/v1/me/top/tracks", accessToken) {
                parameter("time_range", timeRange)
                parameter("limit", limit.coerceIn(1, 50))
            }
        return json.optJSONArray("items").toTrackList()
    }

    suspend fun getPlaylists(
        accessToken: String,
        limit: Int = 50,
    ): List<SpotifyPlaylistSummary> {
        val results = mutableListOf<SpotifyPlaylistSummary>()
        var offset = 0
        while (true) {
            val json =
                getJson("https://api.spotify.com/v1/me/playlists", accessToken) {
                    parameter("limit", limit.coerceIn(1, 50))
                    parameter("offset", offset)
                }
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                results +=
                    SpotifyPlaylistSummary(
                        id = item.getString("id"),
                        name = item.optString("name", "Playlist"),
                        trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
                        ownerName = item.optJSONObject("owner")?.optString("display_name"),
                        isCollaborative = item.optBoolean("collaborative", false),
                    )
            }
            val next = json.optString("next", "")
            if (next.isBlank() || items.length() == 0) break
            offset += items.length()
            if (offset > 500) break
        }
        return results
    }

    suspend fun getPlaylistTracks(
        accessToken: String,
        playlistId: String,
        limit: Int = 100,
    ): List<SpotifyTrack> {
        val results = mutableListOf<SpotifyTrack>()
        var offset = 0
        // Prefer /items (Spotify Feb 2026 Dev Mode); fall back to legacy /tracks.
        val paths =
            listOf(
                "https://api.spotify.com/v1/playlists/$playlistId/items",
                "https://api.spotify.com/v1/playlists/$playlistId/tracks",
            )
        var pathIndex = 0
        while (true) {
            val baseUrl = paths[pathIndex]
            val json =
                try {
                    getJson(baseUrl, accessToken) {
                        parameter("limit", limit.coerceIn(1, 100))
                        parameter("offset", offset)
                        if (baseUrl.endsWith("/tracks")) {
                            parameter("fields", "items(track(id,name,artists(name),type)),next")
                        }
                    }
                } catch (e: SpotifyApiException) {
                    if (e.status == 403 && pathIndex == 0) {
                        Timber.tag(TAG).w("Playlist /items returned 403; trying legacy /tracks")
                        pathIndex = 1
                        offset = 0
                        results.clear()
                        continue
                    }
                    throw e
                }
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val row = items.optJSONObject(i) ?: continue
                val trackObj =
                    row.optJSONObject("item")
                        ?: row.optJSONObject("track")
                        ?: continue
                val type = trackObj.optString("type")
                if (type.isNotBlank() && type != "track") continue
                val id = trackObj.optString("id")
                if (id.isBlank()) continue
                val artists = mutableListOf<String>()
                val artistArr = trackObj.optJSONArray("artists")
                if (artistArr != null) {
                    for (a in 0 until artistArr.length()) {
                        artists += artistArr.optJSONObject(a)?.optString("name").orEmpty()
                    }
                }
                results +=
                    SpotifyTrack(
                        id = id,
                        name = trackObj.optString("name"),
                        artists = artists.filter { it.isNotBlank() },
                    )
            }
            val next = json.optString("next", "")
            if (next.isBlank() || items.length() == 0) break
            offset += items.length()
            if (offset > 2000) break
        }
        return results
    }

    fun close() {
        runCatching { httpClient.close() }
    }

    private suspend fun getJson(
        url: String,
        accessToken: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): JSONObject {
        val response =
            httpClient.get(url) {
                bearerAuth(accessToken)
                block()
            }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            Timber.tag(TAG).w("API %s → HTTP %d: %s", url, response.status.value, body.take(200))
            val hint =
                when (response.status.value) {
                    403 ->
                        " Forbidden — Development Mode apps need the account allowlisted under " +
                            "Developer Dashboard → User Management, and as of 2026 the app owner " +
                            "generally needs Spotify Premium. Prefer Import from file (no Premium), " +
                            "or fix dashboard access then Disconnect/Connect."
                    401 -> " Unauthorized — Disconnect and Connect Spotify again."
                    else -> ""
                }
            throw SpotifyApiException(
                status = response.status.value,
                message = "Spotify API HTTP ${response.status.value}.$hint",
            )
        }
        return JSONObject(body)
    }

    private fun JSONArray?.toArtistList(): List<SpotifyArtist> {
        if (this == null) return emptyList()
        val list = mutableListOf<SpotifyArtist>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            list +=
                SpotifyArtist(
                    id = item.getString("id"),
                    name = item.optString("name"),
                )
        }
        return list
    }

    private fun JSONArray?.toTrackList(): List<SpotifyTrack> {
        if (this == null) return emptyList()
        val list = mutableListOf<SpotifyTrack>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val artists = mutableListOf<String>()
            val artistArr = item.optJSONArray("artists")
            if (artistArr != null) {
                for (a in 0 until artistArr.length()) {
                    artists += artistArr.optJSONObject(a)?.optString("name").orEmpty()
                }
            }
            list +=
                SpotifyTrack(
                    id = item.getString("id"),
                    name = item.optString("name"),
                    artists = artists.filter { it.isNotBlank() },
                )
        }
        return list
    }

    companion object {
        private const val TAG = "SpotifySvc"

        private fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000L
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 30_000L
            }
            expectSuccess = false
        }
    }
}
