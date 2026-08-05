/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Cloud chat backends for Nano DJ (OpenAI-compatible including Groq / Hack Club / HF / OpenRouter,
 * plus Anthropic Messages). Implements [GeminiNanoClient] so DJ / taste call sites stay unchanged.
 */
class CloudDjLlmClient(
    private val provider: DjAiProvider,
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String? = null,
) : GeminiNanoClient {
    private val http =
        OkHttpClient
            .Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun checkStatus(): GeminiNanoStatus =
        if (apiKey.isBlank() && provider.requiresApiKey()) {
            GeminiNanoStatus.Unavailable
        } else {
            GeminiNanoStatus.Available
        }

    override suspend fun download(onProgress: (bytesDownloaded: Long) -> Unit) {
        // Cloud models are not downloaded on-device.
    }

    override suspend fun generateContent(prompt: String): String? =
        withContext(Dispatchers.IO) {
            if (prompt.isBlank()) return@withContext null
            if (apiKey.isBlank() && provider.requiresApiKey()) {
                throw DjAiException(
                    DjAiException.Kind.NO_API_KEY,
                    "No API key for ${provider.displayName}. Set it in Settings → Playback → Nano DJ.",
                )
            }
            try {
                when (provider) {
                    DjAiProvider.ANTHROPIC -> anthropicMessages(prompt)
                    else -> openAiCompatibleChat(prompt)
                }
            } catch (e: DjAiException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Cloud DJ generate failed (%s)", provider.id)
                throw DjAiException(
                    DjAiException.Kind.NETWORK,
                    "DJ AI network error (${provider.displayName}): ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
        }

    private fun openAiCompatibleChat(prompt: String): String? {
        val url = resolveChatCompletionsUrl()

        val body =
            JSONObject()
                .put("model", model.ifBlank { defaultModel() })
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put(
                                    "content",
                                    "You are Nano DJ, a concise music radio host. Follow the user's format exactly.",
                                ),
                        ).put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", prompt),
                        ),
                ).put("temperature", 0.8)

        val request =
            Request
                .Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .apply {
                    if (apiKey.isNotBlank()) {
                        header("Authorization", "Bearer $apiKey")
                    }
                    if (provider == DjAiProvider.OPENROUTER) {
                        header("HTTP-Referer", "https://github.com/daBiggestDuck/Metrolist-AI")
                        header("X-Title", "Metrolist AI Nano DJ")
                    }
                }.post(body.toString().toRequestBody(jsonMedia))
                .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Timber.tag(TAG).w("Cloud DJ HTTP %s: %s", response.code, raw.take(300))
                throw DjAiException(
                    DjAiException.Kind.HTTP,
                    "DJ AI HTTP ${response.code} (${provider.displayName}): ${raw.take(160).ifBlank { "no body" }}",
                )
            }
            val choices = JSONObject(raw).optJSONArray("choices")
                ?: throw DjAiException(
                    DjAiException.Kind.EMPTY,
                    "DJ AI returned no choices (${provider.displayName})",
                )
            val message = choices.optJSONObject(0)?.optJSONObject("message")
                ?: throw DjAiException(
                    DjAiException.Kind.EMPTY,
                    "DJ AI returned empty message (${provider.displayName})",
                )
            return message.optString("content").trim().takeIf { it.isNotBlank() }
                ?: throw DjAiException(
                    DjAiException.Kind.EMPTY,
                    "DJ AI returned empty content (${provider.displayName})",
                )
        }
    }

    private fun anthropicMessages(prompt: String): String? {
        val url = baseUrl?.takeIf { it.isNotBlank() } ?: "https://api.anthropic.com/v1/messages"
        val body =
            JSONObject()
                .put("model", model.ifBlank { "claude-haiku-4-5-20251001" })
                .put("max_tokens", 512)
                .put(
                    "system",
                    "You are Nano DJ, a concise music radio host. Follow the user's format exactly.",
                ).put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt),
                    ),
                )

        val request =
            Request
                .Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Timber.tag(TAG).w("Anthropic DJ HTTP %s: %s", response.code, raw.take(300))
                throw DjAiException(
                    DjAiException.Kind.HTTP,
                    "DJ AI HTTP ${response.code} (Anthropic): ${raw.take(160).ifBlank { "no body" }}",
                )
            }
            val content = JSONObject(raw).optJSONArray("content")
                ?: throw DjAiException(
                    DjAiException.Kind.EMPTY,
                    "DJ AI returned no content blocks (Anthropic)",
                )
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") {
                    return block.optString("text").trim().takeIf { it.isNotBlank() }
                        ?: throw DjAiException(
                            DjAiException.Kind.EMPTY,
                            "DJ AI returned empty text (Anthropic)",
                        )
                }
            }
            throw DjAiException(
                DjAiException.Kind.EMPTY,
                "DJ AI returned no text blocks (Anthropic)",
            )
        }
    }

    private fun resolveChatCompletionsUrl(): String {
        val override = baseUrl?.trim()?.takeIf { it.isNotBlank() } ?: return defaultChatCompletionsUrl()
        val trimmed = override.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun defaultChatCompletionsUrl(): String =
        when (provider) {
            DjAiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
            DjAiProvider.HUGGINGFACE -> "https://router.huggingface.co/v1/chat/completions"
            DjAiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            DjAiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
            DjAiProvider.HACKCLUB -> "https://ai.hackclub.com/proxy/v1/chat/completions"
            DjAiProvider.ANTHROPIC, DjAiProvider.NANO ->
                "https://api.openai.com/v1/chat/completions"
        }

    private fun defaultModel(): String =
        when (provider) {
            DjAiProvider.OPENAI -> "gpt-4o-mini"
            DjAiProvider.HUGGINGFACE -> "meta-llama/Meta-Llama-3-8B-Instruct"
            DjAiProvider.OPENROUTER -> "google/gemini-2.5-flash-lite"
            DjAiProvider.GROQ -> "llama-3.3-70b-versatile"
            DjAiProvider.HACKCLUB -> "qwen/qwen3-32b"
            DjAiProvider.ANTHROPIC -> "claude-haiku-4-5-20251001"
            DjAiProvider.NANO -> ""
        }

    companion object {
        private const val TAG = "CloudDjLlm"
    }
}
