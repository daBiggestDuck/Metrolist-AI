/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import timber.log.Timber

data class SpotifyAuthCodeResult(val code: String, val state: String)

class SpotifyOAuthActivity : Activity() {

    companion object {
        private const val TAG = "SpotifySvc"

        @Volatile
        private var deferred: CompletableDeferred<SpotifyAuthCodeResult>? = null

        fun newDeferred(): CompletableDeferred<SpotifyAuthCodeResult> {
            val d = CompletableDeferred<SpotifyAuthCodeResult>()
            deferred = d
            return d
        }

        suspend fun awaitCode(timeoutMs: Long = 120_000L): SpotifyAuthCodeResult {
            val d = deferred ?: throw CancellationException("No pending authorization")
            return withTimeout(timeoutMs) { d.await() }
        }

        fun cancelPending() {
            deferred?.let { d ->
                if (!d.isCompleted) {
                    d.completeExceptionally(CancellationException("Authorization cancelled by user"))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).i("OAuthActivity: onCreate with intent=%s", intent?.action)

        val uri = intent?.data ?: run {
            Timber.tag(TAG).w("OAuthActivity: no URI in intent")
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            Timber.tag(TAG).w("OAuthActivity: error=%s", error)
            deferred?.completeExceptionally(
                SpotifyAuthException.UserCancelled("Authorization denied: $error"),
            )
            finish()
            return
        }

        if (code == null) {
            Timber.tag(TAG).w("OAuthActivity: missing code")
            deferred?.completeExceptionally(
                SpotifyAuthException.InvalidGrant("Missing authorization code"),
            )
            finish()
            return
        }

        Timber.tag(TAG).i("OAuthActivity: received code (length=%d)", code.length)
        deferred?.complete(SpotifyAuthCodeResult(code = code, state = state ?: ""))
        finish()
    }
}
