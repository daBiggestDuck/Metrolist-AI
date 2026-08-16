/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single action the DJ can perform, as decided by the AI planner.
 * [action] is one of the catalog entries below; [args] carries the needed parameters.
 */
data class DjPlannedAction(
    val action: String,
    val args: Map<String, String>,
)

/**
 * The result of executing one [DjPlannedAction]: whether it worked and a short
 * human-readable description of the outcome (used both in chat and in the final summary).
 */
data class DjActionResult(
    val action: String,
    val success: Boolean,
    val message: String,
)

/** What the planner decided: either a plain conversational reply, or a list of actions. */
sealed class DjPlan {
    data class Chat(val reply: String) : DjPlan()

    data class Actions(val actions: List<DjPlannedAction>) : DjPlan()
}

/**
 * The agentic brain behind Metro DJ chat. It plans a natural-language request into a list of
 * concrete actions, lets the app execute them, then summarizes the real outcomes back to the
 * listener — so the DJ only ever reports what actually happened (or what could not be done).
 */
object DjAgent {
    private val ACTIONS_CATALOG =
        """
        - create_playlist { name }                      create a new local playlist with this name
        - add_to_playlist { name, query }               search "query" on YouTube Music and add the results to playlist "name"; omit query to add the current track
        - delete_playlist { name }
        - rename_playlist { old, new }
        - queue_song { query }                          search "query" and append the first match to the END of the queue
        - play_next { query }                           search "query" and insert it right after the current track so it plays next (omit query to move the current track next)
        - insert_song { query, after }                  search "query" and insert it right after the track titled "after" (omit "after" to insert after the current track)
        - insert_at { query, position }                 search "query" and insert it at zero-based queue position "position"
        - like                                          like the current track
        - dislike                                       dislike the current track
        - undo_dislike                                  undo the dislike of the current track
        - skip                                          skip to the next track
        - switch_lane { lane }                          lane is one of: chill, hype, focus, nostalgia, artist_radio
        - open_settings
        - toggle_voice { on }                           on is "true" or "false"
        - open_disliked                                 open the disliked songs playlist
        - open_recommendations                          open the Metro DJ Recommendations playlist
        - download { scope }                            scope is "current" or "queue"
        - stop                                          stop the Metro DJ radio
        - clear_queue                                   stop playback and clear the queue
        - refresh                                       rebuild / refresh the radio queue ("more like this")
        """.trimIndent()

    /**
     * Plans [userMessage] into either a conversational reply or a list of actions.
     * Returns null when the AI is unavailable or the output cannot be parsed (callers must
     * fall back to their local command matcher in that case).
     */
    suspend fun plan(
        client: GeminiNanoClient,
        userMessage: String,
        currentState: String,
    ): DjPlan? {
        val prompt =
            """
            You are Metro DJ, an AI music-nerd radio host inside a music app. You can actually DO
            things — you are not just a talker. Given the listener's message, decide what to do.

            Available actions (choose from EXACTLY these):
            $ACTIONS_CATALOG

            Rules:
            - "play <song> next", "put <song> next", or "play <song> after this" means play_next
              with query = <song> (insert right after the current track).
            - "add <song> to the queue" or "queue <song>" means queue_song (append to the end).
            - "play <song> after <song>" or "insert <song> after <song>" means insert_song with
              query = <song> and after = the other song.
            - "insert <song> at position N" means insert_at with query = <song> and position = N.
            - If the listener asks you to do something you CAN do, output a JSON object with a list
              of the actions needed, in execution order. Use plain arguments (no quotes inside names
              unless they are part of the name).
            - If a request needs multiple steps, include all of them (for example "make a chainsaw
              man playlist" is create_playlist then add_to_playlist with query "chainsaw man").
            - If the listener just wants to talk (a question, a greeting, music chat), output a chat
              reply instead of actions. Chat replies must be short and brisk (one or two sentences).
            - If the listener asks for something you CANNOT do, output a chat reply saying plainly
              that you can't do it and (briefly) why. Never invent actions that are not in the list.
            - Never promise a result; the app executes the actions and will tell the listener the
              real outcome afterward. Do not add a trailing confirmation message to an action reply.

            Respond with ONLY one JSON object, no markdown, no prose, in one of these two shapes:
            {"type":"chat","reply":"<short, casual reply>"}
            {"type":"actions","actions":[{"action":"create_playlist","args":{"name":"Chainsaw Man"}},{"action":"add_to_playlist","args":{"name":"Chainsaw Man","query":"chainsaw man"}}]}

            Current state:
            $currentState

            Listener: $userMessage
            """.trimIndent()

        val raw =
            runCatching { client.generateContent(prompt) }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) return null
        return parse(raw)
    }

    /**
     * Summarizes the real [results] of executing [userMessage]'s actions into one short, honest
     * reply. Returns null when the AI is unavailable (callers build a fallback from the results).
     */
    suspend fun summarize(
        client: GeminiNanoClient,
        userMessage: String,
        results: List<DjActionResult>,
        conversation: String = "",
    ): String? {
        val resultsText =
            results.joinToString("\n") { r ->
                val status = if (r.success) "done" else "FAILED"
                "${r.action}: $status — ${r.message}"
            }
        val prompt =
            """
            You are Metro DJ, a casual music-nerd radio host. You just carried out (or tried to
            carry out) the listener's request. Report the outcome in ONE short, brisk, natural
            sentence (max two sentences). Be honest: if something failed, say exactly what failed
            and why, and if you could not do something the listener asked, say so plainly. Mention
            what you DID accomplish when relevant. No markdown, no emoji, no bullet points, no
            "as an AI".

            Recent conversation:
            ${conversation.ifBlank { "(none)" }}

            Listener request: $userMessage
            Results:
            $resultsText
            """.trimIndent()

        return runCatching { client.generateContent(prompt) }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parse(raw: String): DjPlan? {
        val jsonText = stripFences(raw)
        val obj =
            runCatching { JSONObject(jsonText) }.getOrNull()
                ?: runCatching { JSONObject(raw) }.getOrNull()
                ?: return null

        val type = obj.optString("type", "").trim().lowercase()
        return when (type) {
            "chat" -> {
                val reply = obj.optString("reply", "").trim()
                if (reply.isBlank()) null else DjPlan.Chat(reply)
            }

            "actions" -> {
                val arr = obj.optJSONArray("actions") ?: return null
                val actions = buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val action = item.optString("action", "").trim().lowercase()
                        if (action.isBlank()) continue
                        val argsJson = item.optJSONObject("args")
                        val args = buildMap {
                            if (argsJson != null) {
                                val keys = argsJson.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    put(key, argsJson.optString(key, "").trim())
                                }
                            }
                        }
                        add(DjPlannedAction(action, args))
                    }
                }
                if (actions.isEmpty()) null else DjPlan.Actions(actions)
            }

            else -> null
        }
    }

    private fun stripFences(raw: String): String {
        var text = raw.trim()
        text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
        text = text.removeSuffix("```")
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start in 0..end && end > start) {
            text = text.substring(start, end + 1)
        }
        return text
    }
}
