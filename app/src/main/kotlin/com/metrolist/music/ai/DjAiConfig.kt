/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import org.json.JSONObject

/**
 * DJ AI provider helpers: recommended models and per-provider API key / model maps
 * persisted as JSON objects in DataStore (provider id → value).
 */
object DjAiConfig {
    const val CUSTOM_MODEL_ID = "custom_input"

    fun recommendedModels(provider: DjAiProvider): List<String> =
        when (provider) {
            DjAiProvider.NANO -> emptyList()
            DjAiProvider.OPENAI ->
                listOf(
                    "gpt-4o-mini",
                    "gpt-4o",
                    "gpt-4.1-mini",
                    "gpt-4.1",
                    "o4-mini",
                )
            DjAiProvider.ANTHROPIC ->
                listOf(
                    "claude-haiku-4-5-20251001",
                    "claude-sonnet-4-5-20250929",
                    "claude-sonnet-4-6",
                    "claude-opus-4-6",
                )
            DjAiProvider.HUGGINGFACE ->
                listOf(
                    "meta-llama/Meta-Llama-3-8B-Instruct",
                    "meta-llama/Llama-3.3-70B-Instruct",
                    "Qwen/Qwen2.5-72B-Instruct",
                    "mistralai/Mistral-7B-Instruct-v0.3",
                )
            DjAiProvider.OPENROUTER ->
                listOf(
                    "google/gemini-2.5-flash-lite",
                    "google/gemini-2.5-flash",
                    "openai/gpt-4o-mini",
                    "anthropic/claude-haiku-4.5",
                    "meta-llama/llama-4-scout",
                    "deepseek/deepseek-chat-v3.1",
                    "x-ai/grok-4.1-fast",
                )
            DjAiProvider.GROQ ->
                listOf(
                    "llama-3.3-70b-versatile",
                    "llama-3.1-8b-instant",
                    "openai/gpt-oss-120b",
                    "qwen/qwen3-32b",
                    "moonshotai/kimi-k2-instruct",
                )
            DjAiProvider.HACKCLUB ->
                listOf(
                    "qwen/qwen3-32b",
                    "openai/gpt-oss-120b",
                    "moonshotai/kimi-k2-instruct-0905",
                )
        }

    fun defaultModel(provider: DjAiProvider): String =
        recommendedModels(provider).firstOrNull().orEmpty()

    fun parseMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.optString(key, "")
                    if (key.isNotBlank()) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encodeMap(map: Map<String, String>): String {
        if (map.isEmpty()) return ""
        val obj = JSONObject()
        map.forEach { (key, value) ->
            if (key.isNotBlank()) obj.put(key, value)
        }
        return obj.toString()
    }

    fun getForProvider(
        mapJson: String?,
        provider: DjAiProvider,
        fallback: String = "",
    ): String = parseMap(mapJson)[provider.id]?.takeIf { it.isNotBlank() } ?: fallback

    fun putForProvider(
        mapJson: String?,
        provider: DjAiProvider,
        value: String,
    ): String {
        val next = parseMap(mapJson).toMutableMap()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            next.remove(provider.id)
        } else {
            next[provider.id] = trimmed
        }
        return encodeMap(next)
    }

    /**
     * Resolve the API key for [provider], preferring the per-provider map, then the
     * legacy active [DjAiApiKey] value, then OpenRouter lyrics key as last resort.
     */
    fun resolveApiKey(
        provider: DjAiProvider,
        keysByProviderJson: String?,
        activeApiKey: String?,
        openRouterFallback: String? = null,
    ): String {
        getForProvider(keysByProviderJson, provider).takeIf { it.isNotBlank() }?.let { return it }
        activeApiKey?.takeIf { it.isNotBlank() }?.let { return it }
        if (provider == DjAiProvider.OPENROUTER) {
            openRouterFallback?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    /**
     * Resolve the model for [provider], preferring the per-provider map, then the
     * legacy active model, then the provider default.
     */
    fun resolveModel(
        provider: DjAiProvider,
        modelsByProviderJson: String?,
        activeModel: String?,
        openRouterFallback: String? = null,
    ): String {
        getForProvider(modelsByProviderJson, provider).takeIf { it.isNotBlank() }?.let { return it }
        activeModel?.takeIf { it.isNotBlank() }?.let { return it }
        if (provider == DjAiProvider.OPENROUTER) {
            openRouterFallback?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return defaultModel(provider)
    }

    /**
     * Seed the per-provider map from a legacy single active value when the map has no
     * entry for [provider] yet (one-time migration).
     */
    fun migrateActiveIntoMap(
        mapJson: String?,
        provider: DjAiProvider,
        activeValue: String?,
    ): String {
        if (activeValue.isNullOrBlank()) return mapJson.orEmpty()
        val map = parseMap(mapJson)
        if (map.containsKey(provider.id)) return mapJson.orEmpty()
        return putForProvider(mapJson, provider, activeValue)
    }
}
