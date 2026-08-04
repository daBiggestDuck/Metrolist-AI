/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SpotifyAuthTest {

    @Test
    fun generatePkcePair_producesUrlSafeVerifierAndChallenge() {
        val pair = SpotifyAuth.generatePkcePair()
        assertTrue(pair.verifier.isNotBlank())
        assertTrue(pair.challenge.isNotBlank())
        assertFalse(pair.verifier.contains("+"))
        assertFalse(pair.verifier.contains("/"))
        assertFalse(pair.challenge.contains("+"))
        assertFalse(pair.challenge.contains("/"))
        assertEquals(43, pair.challenge.length) // SHA-256 digest base64url without padding
    }

    @Test
    fun buildAuthorizeUrl_includesRequiredPkceParams() {
        val url =
            SpotifyAuth.buildAuthorizeUrl(
                clientId = "test-client-id",
                redirectUri = SpotifyAuth.REDIRECT_URI,
                state = "abc123",
                challenge = "challenge-value",
            )
        assertTrue(url.startsWith(SpotifyAuth.SPOTIFY_OAUTH_AUTHORIZE))
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=challenge-value"))
        assertTrue(url.contains("state=abc123"))
        assertTrue(url.contains("redirect_uri="))
        assertTrue(url.contains("metrolistspotify"))
        assertEquals("metrolistspotify://callback", SpotifyAuth.REDIRECT_URI)
    }
}
