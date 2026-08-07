/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

/**
 * Backend used for Metro DJ commentary / picks and live taste refresh.
 * Cloud providers need an API key; [NANO] uses on-device Gemini Nano (GMS + AICore).
 */
enum class DjAiProvider(
    val id: String,
    val displayName: String,
) {
    NANO("nano", "Metro DJ (on-device)"),
    OPENAI("openai", "OpenAI"),
    ANTHROPIC("anthropic", "Anthropic"),
    HUGGINGFACE("huggingface", "Hugging Face"),
    OPENROUTER("openrouter", "OpenRouter"),
    GROQ("groq", "Groq"),
    HACKCLUB("hackclub", "Hack Club"),
    ;

    companion object {
        fun fromId(raw: String?): DjAiProvider =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: NANO
    }
}
