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
 * Shared Nano DJ session state: active flag, latest host line, optional TTS (Spotify DJ-style talk).
 */
object NanoDjSession {
    private const val TAG = "NanoDJ"

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _commentary = MutableStateFlow<String?>(null)
    val commentary: StateFlow<String?> = _commentary.asStateFlow()

    private val _usedAi = MutableStateFlow(false)
    val usedAi: StateFlow<Boolean> = _usedAi.asStateFlow()

    @Volatile
    private var tts: TextToSpeech? = null

    private val ttsReady = AtomicBoolean(false)
    private val speakEnabled = AtomicBoolean(true)

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
                    } else {
                        ttsReady.set(false)
                        Timber.tag(TAG).w("TTS init failed status=%d", status)
                    }
                }
        }
    }

    fun start(openingLine: String?, usedAi: Boolean = false) {
        _active.value = true
        publish(openingLine, usedAi)
    }

    fun publish(line: String?, usedAi: Boolean = false) {
        if (line.isNullOrBlank()) return
        _commentary.value = line.trim()
        _usedAi.value = usedAi
        maybeSpeak(line.trim())
    }

    fun stop() {
        _active.value = false
        _commentary.value = null
        _usedAi.value = false
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        stop()
        runCatching {
            tts?.shutdown()
        }
        tts = null
        ttsReady.set(false)
    }

    private fun maybeSpeak(line: String) {
        if (!speakEnabled.get() || !ttsReady.get()) return
        val engine = tts ?: return
        runCatching {
            engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "nano_dj_${System.currentTimeMillis()}")
        }.onFailure {
            Timber.tag(TAG).w(it, "TTS speak failed")
        }
    }
}
