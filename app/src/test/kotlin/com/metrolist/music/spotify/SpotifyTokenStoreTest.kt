/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.spotify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SpotifyTokenStoreTest {

    private lateinit var context: Context
    private lateinit var testKey: SecretKey

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        SpotifyTokenStore.AesKeystore.setTestKey(testKey)
        context.getSharedPreferences("spotify_token", Context.MODE_PRIVATE).edit().clear().apply()
        SpotifyTokenStore.init(context)
    }

    @After
    fun tearDown() {
        SpotifyTokenStore.clear()
        SpotifyTokenStore.AesKeystore.setTestKey(null)
    }

    @Test
    fun storeAndRetrieveToken() {
        val token = "secret-spotify-access-token"
        SpotifyTokenStore.store(token)
        assertEquals(token, SpotifyTokenStore.retrieve())
    }

    @Test
    fun storeFull_retainsAccessAndRefresh() {
        SpotifyTokenStore.storeFull("access1", "refresh1", expiresInSec = 3600L)
        assertEquals("access1", SpotifyTokenStore.retrieve())
        assertEquals("refresh1", SpotifyTokenStore.getRefreshToken())
    }

    @Test
    fun clear_removesTokens() {
        SpotifyTokenStore.storeFull("access", "refresh", expiresInSec = 3600L)
        SpotifyTokenStore.clear()
        assertNull(SpotifyTokenStore.retrieve())
        assertNull(SpotifyTokenStore.getRefreshToken())
        assertEquals(0L, SpotifyTokenStore.getExpiresAt())
    }

    @Test
    fun aesRoundTrip() {
        val plaintext = "spotify-token-roundtrip"
        val encrypted = SpotifyTokenStore.AesKeystore.encrypt(plaintext)
        assertEquals(plaintext, SpotifyTokenStore.AesKeystore.decrypt(encrypted))
    }
}
