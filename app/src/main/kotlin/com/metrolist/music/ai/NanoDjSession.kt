/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared Metro DJ session state: active flag, latest host line, optional TTS (Spotify DJ-style talk).
 */
object NanoDjSession {
    private const val TAG = "NanoDJ"

    /**
     * Spotify-style cadence: the host speaks a short intro once per block of songs, then
     * stays silent for the rest of the block. Speech is throttled here so it can never fire
     * on every track, regardless of how often the queue prefetches new batches.
     */
    private const val DJ_BLOCK_SIZE = 4

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** True while a radio session is being composed (seed resolve / AI queue build). */
    private val _starting = MutableStateFlow(false)
    val starting: StateFlow<Boolean> = _starting.asStateFlow()

    fun setStarting(starting: Boolean) {
        _starting.value = starting
    }

    private val _sessionId = MutableStateFlow(0L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    private val _commentary = MutableStateFlow<String?>(null)
    val commentary: StateFlow<String?> = _commentary.asStateFlow()

    private val _usedAi = MutableStateFlow(false)
    val usedAi: StateFlow<Boolean> = _usedAi.asStateFlow()

    @Volatile
    private var tts: TextToSpeech? = null

    private val ttsReady = AtomicBoolean(false)
    private val speakEnabled = AtomicBoolean(true)
    private var lastAnnouncedLine: String? = null
    private var lastTransitionMediaId: String? = null
    /** Latest block intro, held until the next block boundary. */
    private var pendingBlockLine: String? = null
    private var songsInBlock = 0
    @Volatile
    private var pendingAnnouncement: String? = null

    fun setSpeakEnabled(enabled: Boolean) {
        speakEnabled.set(enabled)
    }

    fun isSpeakEnabled(): Boolean = speakEnabled.get()

    fun ensureTts(context: Context) {
        if (tts != null) return
        synchronized(this) {
            if (tts != null) return
            tts =
                TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.getDefault())
                        ttsReady.set(
                            result != TextToSpeech.LANG_MISSING_DATA &&
                                result != TextToSpeech.LANG_NOT_SUPPORTED,
                        )
                        Timber.tag(TAG).i("TTS ready=%s", ttsReady.get())
                        synchronized(this@NanoDjSession) {
                            pendingAnnouncement?.also {
                                pendingAnnouncement = null
                                speakOnce(it, force = true)
                            }
                        }
                    } else {
                        ttsReady.set(false)
                        Timber.tag(TAG).w("TTS init failed status=%d", status)
                    }
                }
        }
    }

    @Synchronized
    fun start(openingLine: String?, usedAi: Boolean = false) {
        _sessionId.value++
        _active.value = true
        songsInBlock = 0
        pendingBlockLine = null
        lastTransitionMediaId = null
        publish(openingLine, usedAi)
        announceCurrent()
        // The opening line is spoken now; do not repeat it at the next block boundary.
        pendingBlockLine = null
    }

    /** Updates the visible host line without speaking during queue composition/prefetch. */
    @Synchronized
    fun publish(
        line: String?,
        usedAi: Boolean = false,
    ) {
        if (line.isNullOrBlank()) return
        _commentary.value = line.trim()
        _usedAi.value = usedAi
        // Hold the freshest intro for the next block boundary instead of speaking per song.
        pendingBlockLine = line.trim()
    }

    /** Speaks a user-requested change immediately, bypassing block-boundary gating. */
    @Synchronized
    fun announce(line: String?, usedAi: Boolean = false) {
        if (line.isNullOrBlank()) return
        _commentary.value = line.trim()
        _usedAi.value = usedAi
        speakOnce(line.trim(), force = true)
    }

    /**
     * Called on every real playback transition, but only speaks once a full block of songs
     * has elapsed — the Spotify-style "talk once, then let the block play" cadence.
     */
    @Synchronized
    fun announceTransition(mediaId: String?) {
        if (!_active.value || mediaId.isNullOrBlank() || mediaId == lastTransitionMediaId) return
        lastTransitionMediaId = mediaId
        songsInBlock++
        if (songsInBlock >= DJ_BLOCK_SIZE) {
            songsInBlock = 0
            pendingBlockLine?.let { line ->
                pendingBlockLine = null
                speakOnce(line)
            }
        }
    }

    private fun announceCurrent() {
        _commentary.value?.trim()?.takeIf { it.isNotBlank() }?.let { speakOnce(it) }
    }

    @Synchronized
    fun stop() {
        _active.value = false
        _starting.value = false
        _commentary.value = null
        _usedAi.value = false
        lastAnnouncedLine = null
        lastTransitionMediaId = null
        pendingBlockLine = null
        songsInBlock = 0
        pendingAnnouncement = null
        runCatching { tts?.stop() }
    }

    @Synchronized
    fun shutdown() {
        stop()
        runCatching {
            tts?.shutdown()
        }
        tts = null
        ttsReady.set(false)
    }

    @Synchronized
    private fun speakOnce(line: String, force: Boolean = false) {
        if (!force && lastAnnouncedLine == line) return
        if (!speakEnabled.get()) return
        if (!ttsReady.get()) {
            pendingAnnouncement = line
            return
        }
        val engine = tts ?: return
        lastAnnouncedLine = line
        runCatching {
            engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "nano_dj_${System.currentTimeMillis()}")
        }.onFailure {
            Timber.tag(TAG).w(it, "TTS speak failed")
        }
    }
}
