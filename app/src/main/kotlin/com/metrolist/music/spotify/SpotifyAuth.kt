/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class SpotifyAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSec: Long,
    val scope: String,
)

data class SpotifyPkcePair(val verifier: String, val challenge: String)

sealed class SpotifyAuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class UserCancelled(message: String = "User cancelled authorization") : SpotifyAuthException(message)
    class NetworkFailure(cause: Throwable) : SpotifyAuthException("Network failure: ${cause.message}", cause)
    class InvalidGrant(message: String = "Invalid or expired grant") : SpotifyAuthException(message)
    class StateMismatch : SpotifyAuthException("OAuth state mismatch")
    class NoBrowser(message: String = "No browser available") : SpotifyAuthException(message)
    class MissingClientId(message: String = "Spotify Client ID is not set") : SpotifyAuthException(message)
}

class SpotifyAuth(
    private val httpClient: HttpClient = defaultClient(),
) {

    suspend fun authorize(activity: Activity, clientId: String): SpotifyAuthResult {
        if (clientId.isBlank()) throw SpotifyAuthException.MissingClientId()

        val pkce = generatePkcePair()
        val state = generateState()
        SpotifyOAuthActivity.newDeferred()

        try {
            val authUrl = buildAuthorizeUrl(
                clientId = clientId,
                redirectUri = REDIRECT_URI,
                state = state,
                challenge = pkce.challenge,
            )

            Timber.tag(TAG).i("authorize: launching URL (clientId prefix=%s)", clientId.take(8))

            val intent = CustomTabsIntent.Builder().build().intent
            intent.data = authUrl.toUri()
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Timber.tag(TAG).w(e, "authorize: no browser available to launch URL")
                throw SpotifyAuthException.NoBrowser()
            }

            val callback = try {
                SpotifyOAuthActivity.awaitCode(timeoutMs = 120_000L)
            } catch (e: TimeoutCancellationException) {
                throw SpotifyAuthException.UserCancelled()
            } catch (e: CancellationException) {
                Timber.tag(TAG).i("authorize: cancelled")
                throw SpotifyAuthException.UserCancelled()
            }

            if (callback.state != state) {
                Timber.tag(TAG).w(
                    "authorize: state mismatch (expected=%s, got=%s)",
                    state.take(8),
                    callback.state.take(8),
                )
                throw SpotifyAuthException.StateMismatch()
            }

            return exchangeAuthorizationCode(
                clientId = clientId,
                code = callback.code,
                verifier = pkce.verifier,
                redirectUri = REDIRECT_URI,
            )
        } finally {
            // no cleanup needed
        }
    }

    fun cancel() {
        SpotifyOAuthActivity.cancelPending()
    }

    fun close() {
        runCatching { httpClient.close() }
    }

    suspend fun refresh(clientId: String, refreshToken: String): SpotifyAuthResult =
        performTokenExchange(
            clientId = clientId,
            grantType = "refresh_token",
            extraParams = parameters {
                append("refresh_token", refreshToken)
            },
        )

    private suspend fun exchangeAuthorizationCode(
        clientId: String,
        code: String,
        verifier: String,
        redirectUri: String,
    ): SpotifyAuthResult = performTokenExchange(
        clientId = clientId,
        grantType = "authorization_code",
        extraParams = parameters {
            append("code", code)
            append("redirect_uri", redirectUri)
            append("code_verifier", verifier)
        },
    )

    private suspend fun performTokenExchange(
        clientId: String,
        grantType: String,
        extraParams: io.ktor.http.Parameters,
    ): SpotifyAuthResult {
        val response: HttpResponse = httpClient.submitForm(
            url = SPOTIFY_OAUTH_TOKEN,
            formParameters = parameters {
                append("client_id", clientId)
                append("grant_type", grantType)
                extraParams.forEach { name, values ->
                    values.forEach { value -> append(name, value) }
                }
            },
        )

        val status = response.status
        val body = response.bodyAsText()

        if (status.value in 200..299) {
            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.optLong("expires_in", 0L)
            val scope = json.optString("scope", SPOTIFY_SCOPES)
            Timber.tag(TAG).i(
                "token exchange: success (accessToken length=%d, refreshToken present=%s, expiresIn=%d)",
                accessToken.length,
                refreshToken.isNotEmpty(),
                expiresIn,
            )
            return SpotifyAuthResult(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresInSec = expiresIn,
                scope = scope,
            )
        }

        val errorCode = runCatching { JSONObject(body).optString("error", "") }
            .getOrDefault("")
        if (status == HttpStatusCode.BadRequest && errorCode == "invalid_grant") {
            Timber.tag(TAG).w("token exchange: invalid_grant on %s", grantType)
            throw SpotifyAuthException.InvalidGrant()
        }
        Timber.tag(TAG).w(
            "token exchange: HTTP %d (grantType=%s, error=%s, body=%s)",
            status.value,
            grantType,
            errorCode,
            body.take(200),
        )
        throw SpotifyAuthException.NetworkFailure(IOException("HTTP ${status.value}: $body"))
    }

    companion object {
        private const val TAG = "SpotifySvc"
        const val REDIRECT_URI = "metrolistspotify://callback"
        const val SPOTIFY_OAUTH_AUTHORIZE = "https://accounts.spotify.com/authorize"
        const val SPOTIFY_OAUTH_TOKEN = "https://accounts.spotify.com/api/token"
        const val SPOTIFY_SCOPES =
            "user-read-private user-top-read playlist-read-private playlist-read-collaborative"

        private fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 15_000L
            }
            install(HttpResponseValidator) {
                expectSuccess = false
            }
        }

        fun generatePkcePair(): SpotifyPkcePair {
            val bytes = ByteArray(64)
            SecureRandom().nextBytes(bytes)
            val verifier = Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            val challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.UTF_8)),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            return SpotifyPkcePair(verifier = verifier, challenge = challenge)
        }

        fun buildAuthorizeUrl(
            clientId: String,
            redirectUri: String,
            state: String,
            challenge: String,
            scopes: String = SPOTIFY_SCOPES,
        ): String {
            val encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.name())
            val encodedScope = URLEncoder.encode(scopes, StandardCharsets.UTF_8.name())
            return buildString {
                append(SPOTIFY_OAUTH_AUTHORIZE)
                append("?client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8.name()))
                append("&response_type=code")
                append("&redirect_uri=").append(encodedRedirect)
                append("&scope=").append(encodedScope)
                append("&state=").append(state)
                append("&code_challenge_method=S256")
                append("&code_challenge=").append(challenge)
            }
        }

        fun generateState(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
        }
    }
}
