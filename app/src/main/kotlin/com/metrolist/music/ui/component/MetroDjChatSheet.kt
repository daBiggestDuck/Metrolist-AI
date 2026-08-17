/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.ai.GeminiNanoClient
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.NanoDjLauncher
import com.metrolist.music.ai.NanoDjSession
import com.metrolist.music.ai.DjAgent
import com.metrolist.music.ai.DjPlan
import com.metrolist.music.ai.DjPlannedAction
import com.metrolist.music.ai.DjActionResult
import com.metrolist.music.constants.NanoDjHoldToVoiceKey
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.ui.component.aura.AuraBottomSheet
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraPlayerChrome
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import com.metrolist.music.ui.component.aura.AuraIconButton
import com.metrolist.music.ui.component.aura.AuraPrimaryButton
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MetroDjMessage(
    val fromDj: Boolean,
    val text: String,
    val playlistPreview: DjPlaylistPreview? = null,
)

/** A DJ-made playlist draft: shown in chat, saved to the library only on request. */
data class DjPlaylistPreview(
    val name: String,
    val songs: List<MediaMetadata>,
)

private data class MetroDjQuickAction(
    val label: String,
    val command: String,
)

private data class PendingConfirm(
    val message: String,
    val confirm: () -> Unit,
)

private val DESTRUCTIVE_ACTIONS = setOf("clear_queue", "delete_playlist", "download")

/**
 * The DJ conversation outlives the chat sheet. It only resets when a new radio session
 * starts (NanoDjSession.sessionId changes), not every time the sheet is opened.
 */
object NanoDjChatHistory {
    val messages = mutableStateListOf<MetroDjMessage>()

    /** Session the current conversation belongs to; Long.MIN_VALUE means no session yet. */
    var sessionId: Long = Long.MIN_VALUE

    /** Last DJ host line already shown in chat, so the same line never bubbles up twice. */
    var lastCommentaryLine: String? = null
}

/**
 * Phrase fragments that are NOT a song request when they follow "play" / "add ... to the queue"
 * (e.g. "play this song", "play the next one"). Anything here falls back to normal chat.
 */
private val SONG_QUERY_STOPWORDS =
    setOf(
        "next song",
        "the next song",
        "this song",
        "the current song",
        "current song",
        "a song",
        "some song",
        "something",
        "the queue",
        "this",
        "that",
        "it",
        "the next one",
        "a track",
        "the track",
        "this track",
        "the current track",
        "my playlist",
        "the playlist",
        "a playlist",
        "me a song",
        "me something",
        "some music",
        "music",
    )

private val LANE_WORDS = listOf("chill", "hype", "focus", "nostalgia", "artist radio")

/**
 * Pulls a song-ish query out of a natural request like "after this song, can you play the chainsaw
 * man intro", "add bohemian rhapsody to the queue", or "play <song>". Returns null when the
 * message is really about something else (lane switches, playlists, "play this song", etc.).
 */
private fun extractSongQuery(raw: String): String? {
    val command = raw.trim()
    val normalized = command.lowercase()
    if ("playlist" in normalized || "playing" in normalized || "play next" in normalized) return null

    var query: String? = null
    // "add <song> to the queue"
    Regex("""\badd\s+(.+?)\s+to\s+the\s+queue\b""", RegexOption.IGNORE_CASE)
        .find(command)
        ?.let { query = it.groupValues[1].trim() }
    // "queue <song>"
    if (query == null && normalized.startsWith("queue ")) {
        query = command.substringAfter("queue ").trim()
    }
    // "put on <song>"
    if (query == null) {
        Regex("""\bput on\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(command)
            ?.let { query = it.groupValues[1].trim() }
    }
    // "play <song>" / "can you play <song>" — everything after the last "play".
    if (query == null && " play " in " $normalized ") {
        val idx = command.lastIndexOf("play", ignoreCase = true)
        if (idx >= 0) query = command.substring(idx + 4).trim()
    }

    query =
        query
            ?.trim()
            ?.trim(',', '.', '"', '\'')
            ?.removeSuffix(" please")
            ?.removeSuffix(" now")
            ?.trim()
    if (query.isNullOrBlank() || query.length < 3) return null

    val q = query.lowercase()
    if (SONG_QUERY_STOPWORDS.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }) return null
    if (LANE_WORDS.any { q.contains(it) }) return null
    return query
}

@Composable
fun MetroDjChatButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val context = LocalContext.current
    val active by NanoDjSession.active.collectAsStateWithLifecycle()
    val starting by NanoDjSession.starting.collectAsStateWithLifecycle()
    val (holdToVoice, _) = rememberPreference(NanoDjHoldToVoiceKey, true)
    var showChat by remember { mutableStateOf(false) }
    // Hold-to-voice: long-pressing morphs the button into a live voice button, runs the command
    // headlessly (no popup), and speaks the result back.
    var voiceActive by remember { mutableStateOf(false) }
    var voiceListening by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableStateOf(0f) }
    // Only the launcher button is gated on being on air. The chat sheet must stay composed even
    // while a radio restart flips `active` off momentarily (e.g. "more like this"), otherwise the
    // sheet's coroutine scope is cancelled mid-rebuild and the action never completes.
    if (active) {
        AuraIconButton(
            onClick = { showChat = true },
            onLongClick = if (holdToVoice) ({ voiceActive = true }) else null,
            modifier = modifier.size(42.dp),
            containerColor =
                if (voiceListening) AuraSpotifyGreen.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.1f),
            contentColor = tint,
        ) {
            when {
                starting ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AuraSpotifyGreen,
                    )
                voiceListening -> VoiceWave(voiceRms, Modifier.size(20.dp))
                else ->
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = stringResource(R.string.nano_dj_open_chat),
                        modifier = Modifier.size(20.dp),
                    )
            }
        }
    }

    if (showChat) {
        MetroDjChatSheet(onDismiss = { showChat = false })
    }
    if (voiceActive) {
        MetroDjChatSheet(
            onDismiss = { voiceActive = false },
            headless = true,
            onVoiceState = { listening, rms ->
                voiceListening = listening
                voiceRms = rms
            },
            onSpokenReply = { text ->
                NanoDjSession.announce(text)
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                voiceActive = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroDjChatSheet(
    onDismiss: () -> Unit,
    headless: Boolean = false,
    fullScreen: Boolean = false,
    onVoiceState: ((Boolean, Float) -> Unit)? = null,
    onSpokenReply: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val connection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val active by NanoDjSession.active.collectAsStateWithLifecycle()
    val sessionId by NanoDjSession.sessionId.collectAsStateWithLifecycle()
    val commentary by NanoDjSession.commentary.collectAsStateWithLifecycle()
    val messages = NanoDjChatHistory.messages
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var laneName by remember { mutableStateOf(ListeningTasteTracker.DjLane.ARTIST_RADIO.displayName) }
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }
    val messagesListState = rememberLazyListState()
    fun reply(text: String) {
        messages += MetroDjMessage(true, text)
        if (headless) onSpokenReply?.invoke(text)
    }

    /** Runs a blocking DJ action with the busy flag guaranteed to reset even on failure. */
    fun launchBusy(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    fun currentMedia(): MediaMetadata? =
        runCatching { connection?.player?.currentMediaItem?.metadata }.getOrNull()

    suspend fun answerNaturally(question: String): String {
        val current = currentMedia()
        val artistLine =
            current?.artists?.map { it.name }?.take(2)?.joinToString(", ")
        val contextPrompt =
            """
            You are Metro DJ, the listener's music-nerd radio host — like Spotify's AI DJ but warmer.
            Talk like a real person, not an assistant: casual, first person, short, and brisk. Reply
            in at most two short sentences. Never enumerate features or dump a list unprompted — if the
            listener asks what you can do, summarize the big ones in one friendly line.
            You are a talker, not a doer: the app carries out any actions the listener asks for,
            so just answer conversationally. Never claim you personally performed an action, and
            never tell the listener to "say a phrase" — just answer what they asked about the
            current song, artist, lane, or the music itself. If they asked for something you can't
            answer, say so briefly. No markdown, no emoji, no bullet points, never "as an AI".

            Current state: ${if (active) "on air" else "off air"}
            Lane: $laneName
            Host line: ${commentary.orEmpty().ifBlank { "none" }}
            Current song: ${current?.title.orEmpty().ifBlank { "none" }}${if (artistLine.isNullOrBlank()) "" else " by $artistLine"}
            Recent chat:
            ${messages.takeLast(6).joinToString("\\n") { if (it.fromDj) "DJ: ${it.text}" else "Listener: ${it.text}" }}

            Listener: $question
            """.trimIndent()
        val generated =
            runCatching {
                withContext(Dispatchers.IO) {
                    GeminiNanoClient.get(context).generateContent(contextPrompt)?.trim()
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
        return generated ?:
            when {
                question.contains("why", ignoreCase = true) && commentary != null ->
                    commentary.orEmpty()
                active ->
                    "I'm listening with you in the $laneName lane. Ask me about the current song, the vibe, or what you want to hear next."
                else ->
                    "I'm here to talk music with you. Ask about an artist, a genre, a song, or the kind of mood you're after."
            }
    }

    fun queuedMedia(): List<MediaMetadata> =
        runCatching {
            connection?.player?.let { player ->
                (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).metadata }
            }.orEmpty()
        }.getOrDefault(emptyList())

    suspend fun enqueueDownload(metadata: MediaMetadata) {
        withContext(Dispatchers.IO) {
            database.withTransaction { upsert(metadata.toSongEntity()) }
        }
        val request =
            DownloadRequest
                .Builder(metadata.id, metadata.id.toUri())
                .setCustomCacheKey(metadata.id)
                .setData(metadata.title.toByteArray())
                .build()
        DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
    }

    /** Searches YouTube Music for [query] and adds the first result to the queue. Returns the
     *  human-readable outcome instead of replying, so callers can report it honestly. */
    suspend fun searchAndQueue(query: String): String {
        val item =
            withContext(Dispatchers.IO) {
                runCatching {
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                        ?.toMediaItem()
                }.getOrNull()
            }
        return if (item == null) {
            context.getString(R.string.nano_dj_song_not_found, query)
        } else {
            val playerConnection = connection
                ?: return context.getString(R.string.nano_dj_no_player)
            val beforeCount = playerConnection.player.mediaItemCount
            playerConnection.addToQueue(item)
            if (playerConnection.player.mediaItemCount <= beforeCount) {
                context.getString(R.string.nano_dj_action_failed)
            } else {
                val title = item.mediaMetadata.title?.toString().orEmpty()
                context.getString(R.string.nano_dj_song_added, title.ifBlank { query })
            }
        }
    }

    /** Searches YouTube Music for songs matching [query], returned as playable metadata. */
    suspend fun searchSongs(query: String): List<MediaMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.take(20)
                    ?.map { it.toMediaMetadata() }
            }.getOrNull().orEmpty()
        }

    /** Human-readable failure message instead of a raw exception dump. */
    fun failureText(e: Throwable?): String {
        val raw = (e?.message ?: "").trim()
        val low = raw.lowercase()
        val reason =
            when {
                "timed out" in low || "timeout" in low ->
                    context.getString(R.string.nano_dj_failure_timeout)
                "http 429" in low || "rate limit" in low ->
                    context.getString(R.string.nano_dj_failure_rate_limited)
                "http 401" in low || "http 403" in low || "api key" in low ->
                    context.getString(R.string.nano_dj_failure_api_key)
                "network" in low || "failed to connect" in low || "unable to resolve host" in low ||
                    "no internet" in low -> context.getString(R.string.nano_dj_failure_network)
                raw.isNotEmpty() -> raw.take(120)
                else -> context.getString(R.string.nano_dj_failure_unknown)
            }
        return context.getString(R.string.nano_dj_action_failed_reason, reason)
    }

    /** Snapshot of the live DJ state, fed to the AI planner so it can reason about context. */
    fun currentState(): String {
        val current = currentMedia()
        val artistLine = current?.artists?.map { it.name }?.take(2)?.joinToString(", ")
        val queueSize = runCatching { connection?.player?.mediaItemCount ?: 0 }.getOrDefault(0)
        val recentChat =
            messages.takeLast(12).joinToString("\n") { m ->
                if (m.fromDj) "DJ: ${m.text}" else "Listener: ${m.text}"
            }
        return buildString {
            append("on air: ").append(active).append('\n')
            append("lane: ").append(laneName).append('\n')
            append("current song: ").append(current?.title.orEmpty().ifBlank { "none" })
            if (!artistLine.isNullOrBlank()) append(" by ").append(artistLine)
            append('\n')
            append("host line: ").append(commentary.orEmpty().ifBlank { "none" }).append('\n')
            append("queue size: ").append(queueSize).append('\n')
            append("recent conversation (last messages, oldest first):\n")
            append(recentChat.ifBlank { "(none)" })
        }
    }

    /**
     * Executes a single planned action and reports the real outcome. Never throws — failures
     * are turned into an honest [DjActionResult] so the DJ can say exactly what went wrong.
     */
    suspend fun executePlannedAction(planned: DjPlannedAction): DjActionResult {
        val action = planned.action
        val args = planned.args
        fun ok(message: String) = DjActionResult(action, true, message)
        fun fail(message: String) = DjActionResult(action, false, message)

        return try {
            when (action) {
                "create_playlist" -> {
                    val name = args["name"].orEmpty().trim().trim('"', '\'')
                    if (name.isBlank()) {
                        fail(context.getString(R.string.nano_dj_playlist_name_needed))
                    } else {
                        withContext(Dispatchers.IO) {
                            // Bookmark the new playlist so it appears in the library right away.
                            database.ensureLocalPlaylist(name)
                        }
                        ok(context.getString(R.string.nano_dj_playlist_created, name))
                    }
                }

                "add_to_playlist" -> {
                    val name =
                        args["name"].orEmpty().trim().trim('"', '\'')
                            .ifBlank { SpotifyImportManager.RECOMMENDATIONS_PLAYLIST_NAME }
                    val query = args["query"].orEmpty().trim()
                    val playlist =
                        withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                        }
                    if (playlist == null) {
                        fail(context.getString(R.string.nano_dj_playlist_not_found, name))
                    } else {
                        val items =
                            if (query.isNotBlank()) searchSongs(query)
                            else listOfNotNull(currentMedia())
                        if (items.isEmpty()) {
                            fail(
                                if (query.isNotBlank()) {
                                    context.getString(R.string.nano_dj_song_not_found, query)
                                } else {
                                    context.getString(R.string.nano_dj_no_current_song)
                                },
                            )
                        } else {
                            val localPlaylist =
                                withContext(Dispatchers.IO) { database.playlistBlocking(playlist.id) }
                            if (localPlaylist == null) {
                                fail(context.getString(R.string.nano_dj_playlist_not_found, name))
                            } else {
                                withContext(Dispatchers.IO) {
                                    database.withTransaction {
                                        items.forEach { upsert(it.toSongEntity()) }
                                        addSongsToPlaylist(localPlaylist, items.map { it.id to null })
                                    }
                                }
                                ok(context.getString(R.string.nano_dj_added_to_playlist, name))
                            }
                        }
                    }
                }

                "delete_playlist" -> {
                    val name = args["name"].orEmpty().trim().trim('"', '\'')
                    val playlist =
                        withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                        }
                    if (playlist == null) {
                        fail(context.getString(R.string.nano_dj_playlist_not_found, name))
                    } else {
                        withContext(Dispatchers.IO) {
                            database.withTransaction { delete(playlist) }
                            playlist.browseId?.let { com.metrolist.innertube.YouTube.deletePlaylist(it) }
                        }
                        ok(context.getString(R.string.nano_dj_playlist_deleted, name))
                    }
                }

                "rename_playlist" -> {
                    val old = args["old"].orEmpty().trim().trim('"', '\'')
                    val new = args["new"].orEmpty().trim().trim('"', '\'')
                    val playlist =
                        withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(old, ignoreCase = true) }
                        }
                    if (playlist == null) {
                        fail(context.getString(R.string.nano_dj_playlist_not_found, old))
                    } else {
                        withContext(Dispatchers.IO) {
                            database.withTransaction {
                                update(
                                    playlist.copy(
                                        name = new,
                                        lastUpdateTime = java.time.LocalDateTime.now(),
                                    ),
                                )
                            }
                            playlist.browseId?.let { com.metrolist.innertube.YouTube.renamePlaylist(it, new) }
                        }
                        ok(context.getString(R.string.nano_dj_playlist_renamed, new))
                    }
                }

                "queue_song" -> {
                    val query = args["query"].orEmpty().trim()
                    if (query.isBlank()) {
                        fail(context.getString(R.string.nano_dj_song_query_needed))
                    } else {
                        ok(searchAndQueue(query))
                    }
                }

                "like" -> {
                    val media = currentMedia()
                    if (media == null) {
                        fail(context.getString(R.string.nano_dj_no_current_song))
                    } else {
                        val playerConnection = connection
                        if (playerConnection == null) {
                            fail(context.getString(R.string.nano_dj_no_player))
                        } else {
                            playerConnection.toggleLike()
                            ok(context.getString(R.string.nano_dj_liked, media.title))
                        }
                    }
                }

                "dislike" -> {
                    val media = currentMedia()
                    if (media == null) {
                        fail(context.getString(R.string.nano_dj_no_current_song))
                    } else {
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setDisliked(
                                context,
                                media.id,
                                true,
                                media.title,
                                media.artists.map { it.name },
                            )
                        }
                        connection?.service?.onSongDisliked(media.id)
                        ok(context.getString(R.string.nano_dj_disliked, media.title))
                    }
                }

                "undo_dislike" -> {
                    val media = currentMedia()
                    if (media == null) {
                        fail(context.getString(R.string.nano_dj_no_current_song))
                    } else {
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setDisliked(context, media.id, false)
                        }
                        connection?.service?.onSongUndisliked(media.id)
                        ok(context.getString(R.string.nano_dj_dislike_removed, media.title))
                    }
                }

                "play_next" -> {
                    val query = args["query"].orEmpty().trim()
                    val item =
                        if (query.isNotBlank()) {
                            searchSongs(query).firstOrNull()?.toMediaItem()
                        } else {
                            currentMedia()?.toMediaItem()
                        }
                    if (item == null) {
                        fail(
                            if (query.isNotBlank()) {
                                context.getString(R.string.nano_dj_song_not_found, query)
                            } else {
                                context.getString(R.string.nano_dj_no_current_song)
                            },
                        )
                    } else {
                        val playerConnection = connection
                        if (playerConnection == null) {
                            fail(context.getString(R.string.nano_dj_no_player))
                        } else {
                            playerConnection.playNext(item)
                            ok(
                            if (query.isNotBlank()) {
                                context.getString(R.string.nano_dj_song_playing_next, query)
                            } else {
                                context.getString(R.string.nano_dj_playing_next)
                            },
                            )
                        }
                    }
                }

                "insert_at" -> {
                    val query = args["query"].orEmpty().trim()
                    val position = args["position"]?.toIntOrNull()
                    if (query.isBlank() || position == null || position < 0) {
                        fail(context.getString(R.string.nano_dj_song_query_needed))
                    } else {
                        val item = searchSongs(query).firstOrNull()?.toMediaItem()
                        val playerConnection = connection
                        when {
                            item == null -> fail(context.getString(R.string.nano_dj_song_not_found, query))
                            playerConnection == null -> fail(context.getString(R.string.nano_dj_no_player))
                            else -> {
                                val safePosition = position.coerceIn(0, playerConnection.player.mediaItemCount)
                                playerConnection.insertAt(safePosition, item)
                                ok(context.getString(R.string.nano_dj_song_inserted, query))
                            }
                        }
                    }
                }

                "insert_song" -> {
                    val query = args["query"].orEmpty().trim()
                    val after = args["after"].orEmpty().trim()
                    if (query.isBlank()) {
                        fail(context.getString(R.string.nano_dj_song_query_needed))
                    } else {
                        val item = searchSongs(query).firstOrNull()?.toMediaItem()
                        if (item == null) {
                            fail(context.getString(R.string.nano_dj_song_not_found, query))
                        } else {
                            val player = connection?.player
                            val targetIndex =
                                if (after.isBlank() || after.equals("current", true) || after.equals("this", true)) {
                                    (player?.currentMediaItemIndex ?: -1) + 1
                                } else {
                                    var idx = -1
                                    val count = player?.mediaItemCount ?: 0
                                    for (i in 0 until count) {
                                        val title = player?.getMediaItemAt(i)?.mediaMetadata?.title?.toString()
                                        if (title?.equals(after, ignoreCase = true) == true) {
                                            idx = i
                                            break
                                        }
                                    }
                                    if (idx >= 0) idx + 1 else -1
                                }
                            val playerConnection = connection
                            if (targetIndex < 0) {
                                fail(context.getString(R.string.nano_dj_song_not_found, after.ifBlank { query }))
                            } else if (playerConnection == null) {
                                fail(context.getString(R.string.nano_dj_no_player))
                            } else {
                                playerConnection.insertAt(targetIndex, item)
                                ok(context.getString(R.string.nano_dj_song_inserted, query))
                            }
                        }
                    }
                }

                "skip" -> {
                    connection?.seekToNext()
                    ok(context.getString(R.string.nano_dj_skipped))
                }

                "switch_lane" -> {
                    val lane = ListeningTasteTracker.DjLane.fromId(args["lane"].orEmpty())
                    val player = connection
                    if (player == null) {
                        fail(context.getString(R.string.nano_dj_no_player))
                    } else {
                        laneName = lane.displayName
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setActiveLane(context, lane)
                        }
                        NanoDjSession.stop()
                        NanoDjLauncher.start(
                            context,
                            player,
                            NanoDjSession.isSpeakEnabled(),
                            replaceCurrentQueue = true,
                        ).fold(
                            onSuccess = {
                                ok(context.getString(R.string.nano_dj_switching_now, lane.displayName))
                            },
                            onFailure = { fail(failureText(it)) },
                        )
                    }
                }

                "open_settings" -> {
                    navController.navigate("settings")
                    ok(context.getString(R.string.nano_dj_settings_opened))
                }

                "toggle_voice" -> {
                    val rawOn = args["on"].orEmpty().lowercase()
                    val on = rawOn.isNotBlank() && rawOn != "false" && rawOn != "off" && rawOn != "no"
                    NanoDjSession.setSpeakEnabled(on)
                    ok(
                        if (on) context.getString(R.string.nano_dj_voice_on)
                        else context.getString(R.string.nano_dj_voice_off),
                    )
                }

                "open_disliked" -> {
                    navController.navigate("auto_playlist/disliked")
                    ok(context.getString(R.string.nano_dj_playlist_opened))
                }

                "open_recommendations" -> {
                    val playlistId =
                        withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull {
                                    it.name.equals(
                                        SpotifyImportManager.RECOMMENDATIONS_PLAYLIST_NAME,
                                        ignoreCase = true,
                                    )
                                }?.id
                        }
                    if (playlistId == null) {
                        fail(
                            context.getString(
                                R.string.nano_dj_playlist_not_found,
                                SpotifyImportManager.RECOMMENDATIONS_PLAYLIST_NAME,
                            ),
                        )
                    } else {
                        navController.navigate("local_playlist/$playlistId")
                        ok(context.getString(R.string.nano_dj_playlist_opened))
                    }
                }

                "download" -> {
                    val items =
                        if (args["scope"].orEmpty().lowercase() == "queue") {
                            queuedMedia()
                        } else {
                            listOfNotNull(currentMedia())
                        }
                    if (items.isEmpty()) {
                        fail(context.getString(R.string.nano_dj_no_current_song))
                    } else {
                        items.forEach { enqueueDownload(it) }
                        ok(context.getString(R.string.nano_dj_download_started, items.size))
                    }
                }

                "stop" -> {
                    if (active) {
                        connection?.service?.stopMetroDj()
                        connection?.player?.stop()
                        connection?.player?.clearMediaItems()
                    }
                    ok(context.getString(R.string.nano_dj_stopped))
                }

                "clear_queue" -> {
                    connection?.service?.stopMetroDj()
                    connection?.player?.stop()
                    connection?.player?.clearMediaItems()
                    ok(context.getString(R.string.nano_dj_queue_cleared))
                }

                "refresh" -> {
                    val player = connection
                    if (player == null) {
                        fail(context.getString(R.string.nano_dj_no_player))
                    } else {
                        NanoDjSession.stop()
                        NanoDjLauncher.start(
                            context,
                            player,
                            NanoDjSession.isSpeakEnabled(),
                            replaceCurrentQueue = true,
                        ).fold(
                            onSuccess = { ok(context.getString(R.string.nano_dj_refreshing_now)) },
                            onFailure = { fail(failureText(it)) },
                        )
                    }
                }

                else -> fail(context.getString(R.string.nano_dj_action_failed))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Never swallow cancellation: it means the chat/sheet was dismissed mid-action, so
            // the remaining steps must not keep running as if nothing happened.
            throw e
        } catch (e: Throwable) {
            fail(failureText(e))
        }
    }

    /** Honest fallback when the AI summarizer is unavailable: report done + failed parts. */
    fun fallbackSummary(results: List<DjActionResult>): String {
        val failed = results.filter { !it.success }
        val done = results.filter { it.success }
        val donePart = done.lastOrNull()?.message
        val failedPart =
            failed.takeIf { it.isNotEmpty() }?.let {
                context.getString(R.string.nano_dj_action_failed) + " " +
                    it.joinToString(" ") { r -> r.message }
            }
        return listOfNotNull(donePart, failedPart).joinToString(" ")
    }

    suspend fun runLegacyCommand(command: String, normalized: String, songRequest: String?) {
        when {
            // DJ spoken-voice toggle. Must come before the plain "stop" branch so
            // "stop talking" quiets the voice instead of killing the radio.
            (normalized.contains("voice") || normalized.contains("talking") || normalized.contains("speaking")) &&
                (normalized.contains("off") || normalized.contains("quiet") || normalized.contains("stop") ||
                    normalized.contains("mute") || normalized.contains("silent") || normalized.contains("on")) -> {
                val enable =
                    !(normalized.contains("off") || normalized.contains("quiet") || normalized.contains("stop") ||
                        normalized.contains("mute") || normalized.contains("silent"))
                NanoDjSession.setSpeakEnabled(enable)
                reply(
                    if (enable) context.getString(R.string.nano_dj_voice_on)
                    else context.getString(R.string.nano_dj_voice_off),
                )
            }

            normalized.contains("open") && normalized.contains("settings") -> {
                navController.navigate("settings")
                reply(context.getString(R.string.nano_dj_settings_opened))
            }

            normalized == "stop" || normalized.contains("stop metro dj") || normalized.contains("turn off") -> {
                if (active) {
                    connection?.service?.stopMetroDj()
                    connection?.player?.stop()
                    connection?.player?.clearMediaItems()
                }
                reply(context.getString(R.string.nano_dj_stopped))
            }

            normalized.contains("clear") && normalized.contains("queue") -> {
                pendingConfirm =
                    PendingConfirm(
                        message = context.getString(R.string.nano_dj_clear_queue_confirm),
                        confirm = {
                            launchBusy {
                                runCatching {
                                    connection?.service?.stopMetroDj()
                                    connection?.player?.stop()
                                    connection?.player?.clearMediaItems()
                                }.onSuccess {
                                    reply(context.getString(R.string.nano_dj_queue_cleared))
                                }.onFailure {
                                    reply(failureText(it))
                                }
                            }
                        },
                    )
            }

            normalized.contains("delete") && normalized.contains("playlist") -> {
                val name = command.substringAfter("playlist").trim().trim('"', '\'')
                if (name.isBlank()) {
                    reply(context.getString(R.string.nano_dj_playlist_name_needed))
                } else {
                    pendingConfirm =
                        PendingConfirm(
                            message = context.getString(R.string.nano_dj_delete_playlist_confirm, name),
                            confirm = {
                                launchBusy {
                                    val playlist = withContext(Dispatchers.IO) {
                                        database.playlistEntitiesByNameAsc()
                                            .firstOrNull { it.name.equals(name, ignoreCase = true) }
                                    }
                                    if (playlist == null) {
                                        reply(context.getString(R.string.nano_dj_playlist_not_found, name))
                                    } else {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                database.withTransaction { delete(playlist) }
                                                playlist.browseId?.let { com.metrolist.innertube.YouTube.deletePlaylist(it) }
                                            }
                                        }.onSuccess {
                                            reply(context.getString(R.string.nano_dj_playlist_deleted, playlist.name))
                                        }.onFailure {
                                            reply(failureText(it))
                                        }
                                    }
                                }
                            },
                        )
                }
            }

            normalized.startsWith("create playlist") -> {
                val name = command.substringAfter("create playlist").trim().trim('"', '\'')
                if (name.isBlank()) {
                    reply(context.getString(R.string.nano_dj_playlist_name_needed))
                } else {
                    launchBusy {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                // Bookmark the new playlist so it appears in the library right away.
                                database.ensureLocalPlaylist(name)
                            }
                        }.onSuccess {
                            reply(context.getString(R.string.nano_dj_playlist_created, name))
                        }.onFailure {
                            reply(failureText(it))
                        }
                    }
                }
            }

            normalized.startsWith("rename playlist") -> {
                val remainder = command.substringAfter("rename playlist").trim()
                val parts = Regex("^(.+?)\\s+to\\s+(.+)$", RegexOption.IGNORE_CASE).find(remainder)
                if (parts == null) {
                    reply(context.getString(R.string.nano_dj_rename_playlist_format))
                } else {
                    val oldName = parts.groupValues[1].trim('"', '\'')
                    val newName = parts.groupValues[2].trim('"', '\'')
                    launchBusy {
                        val playlist = withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(oldName, ignoreCase = true) }
                        }
                        if (playlist == null) {
                            reply(context.getString(R.string.nano_dj_playlist_not_found, oldName))
                        } else {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    database.withTransaction {
                                        update(playlist.copy(name = newName, lastUpdateTime = java.time.LocalDateTime.now()))
                                    }
                                    playlist.browseId?.let { com.metrolist.innertube.YouTube.renamePlaylist(it, newName) }
                                }
                            }.onSuccess {
                                reply(context.getString(R.string.nano_dj_playlist_renamed, newName))
                            }.onFailure {
                                reply(failureText(it))
                            }
                        }
                    }
                }
            }

            (normalized.contains("open") || normalized.contains("show")) &&
                (normalized.contains("metro dj") || normalized.contains("recommendation") || normalized.contains("disliked")) -> {
                scope.launch {
                    val isDislikedPlaylist = normalized.contains("disliked")
                    val targetName =
                        if (isDislikedPlaylist) {
                            context.getString(R.string.disliked_songs)
                        } else {
                            SpotifyImportManager.RECOMMENDATIONS_PLAYLIST_NAME
                        }
                    if (isDislikedPlaylist) {
                        navController.navigate("auto_playlist/disliked")
                        reply(context.getString(R.string.nano_dj_playlist_opened))
                    } else {
                        val playlistId = withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                                ?.id
                        }
                        if (playlistId == null) {
                            reply(context.getString(R.string.nano_dj_playlist_not_found, targetName))
                        } else {
                            navController.navigate("local_playlist/$playlistId")
                            reply(context.getString(R.string.nano_dj_playlist_opened))
                        }
                    }
                }
            }

            normalized.contains("download") -> {
                val items = if (normalized.contains("queue")) queuedMedia() else listOfNotNull(currentMedia())
                if (items.isEmpty()) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    pendingConfirm =
                        PendingConfirm(
                            message = context.getString(R.string.nano_dj_download_confirm, items.size),
                            confirm = {
                                launchBusy {
                                    runCatching { items.forEach { enqueueDownload(it) } }
                                        .onSuccess { reply(context.getString(R.string.nano_dj_download_started, items.size)) }
                                        .onFailure { reply(failureText(it)) }
                                }
                            },
                        )
                }
            }

            normalized.contains("add") && normalized.contains("playlist") -> {
                val name =
                    if (normalized.contains("metro dj") || normalized.contains("recommendations")) {
                        SpotifyImportManager.RECOMMENDATIONS_PLAYLIST_NAME
                    } else {
                        command.substringAfter("playlist").trim().trim('"', '\'')
                    }
                val items = if (normalized.contains("queue")) queuedMedia() else listOfNotNull(currentMedia())
                if (name.isBlank()) {
                    reply(context.getString(R.string.nano_dj_playlist_name_needed))
                } else if (items.isEmpty()) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    launchBusy {
                        val playlist = withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                        }
                        if (playlist == null) {
                            reply(context.getString(R.string.nano_dj_playlist_not_found, name))
                        } else {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    database.withTransaction {
                                        items.forEach { upsert(it.toSongEntity()) }
                                        playlistBlocking(playlist.id)?.let { local ->
                                            addSongsToPlaylist(local, items.map { it.id to null })
                                        }
                                    }
                                }
                            }.onSuccess {
                                reply(context.getString(R.string.nano_dj_added_to_playlist, name))
                            }.onFailure {
                                reply(failureText(it))
                            }
                        }
                    }
                }
            }

            normalized.contains("remove dislike") || normalized.contains("undo dislike") || normalized.contains("like this again") -> {
                val media = currentMedia()
                if (media == null) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    launchBusy {
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setDisliked(context, media.id, false)
                        }
                        connection?.service?.onSongUndisliked(media.id)
                        reply(context.getString(R.string.nano_dj_dislike_removed, media.title))
                    }
                }
            }

            normalized.contains("like") && normalized.contains("this") && !normalized.contains("again") -> {
                val media = currentMedia()
                if (media == null) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    connection?.toggleLike()
                    reply(context.getString(R.string.nano_dj_liked, media.title))
                }
            }

            normalized.contains("dislike") || normalized.contains("not for my taste") -> {
                val media = currentMedia()
                if (media == null) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    launchBusy {
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setDisliked(
                                context,
                                media.id,
                                true,
                                media.title,
                                media.artists.map { it.name },
                            )
                        }
                        connection?.service?.onSongDisliked(media.id)
                        reply(context.getString(R.string.nano_dj_disliked, media.title))
                    }
                }
            }

            normalized.contains("play next") -> {
                currentMedia()?.toMediaItem()?.let { connection?.playNext(it) }
                reply(context.getString(R.string.nano_dj_playing_next))
            }

            normalized.contains("skip") || normalized.contains("next song") -> {
                connection?.seekToNext()
                reply(context.getString(R.string.nano_dj_skipped))
            }

            normalized.contains("add") && normalized.contains("queue") -> {
                // "add <any song> to the queue" searches and queues that song; messages without
                // a song name ("add this to the queue") still queue the current track.
                if (songRequest != null) {
                    launchBusy { reply(searchAndQueue(songRequest)) }
                } else {
                    currentMedia()?.toMediaItem()?.let { connection?.addToQueue(it) }
                    reply(context.getString(R.string.nano_dj_added_queue))
                }
            }

            normalized.contains("refresh") || normalized.contains("rebuild") || normalized.contains("more like") -> {
                val player = connection
                if (player == null) {
                    reply(context.getString(R.string.nano_dj_no_player))
                } else {
                    // Acknowledge instantly so the tap visibly lands, then rebuild in the
                    // background. The new session's opening line lands in chat afterwards.
                    reply(context.getString(R.string.nano_dj_refreshing_now))
                    launchBusy {
                        NanoDjSession.stop()
                        NanoDjLauncher.start(
                            context,
                            player,
                            NanoDjSession.isSpeakEnabled(),
                            replaceCurrentQueue = true,
                        ).onFailure {
                            reply(failureText(it))
                        }
                    }
                }
            }

            (normalized.startsWith("make it") ||
                normalized.startsWith("switch to") ||
                normalized.startsWith("change to") ||
                normalized.startsWith("play ") ||
                normalized.startsWith("give me")) &&
                (normalized.contains("chill") || normalized.contains("hype") || normalized.contains("focus") ||
                    normalized.contains("nostalgia") || normalized.contains("artist radio")) -> {
                val laneId = when {
                    normalized.contains("artist radio") -> "artist_radio"
                    normalized.contains("chill") -> "chill"
                    normalized.contains("hype") -> "hype"
                    normalized.contains("focus") -> "focus"
                    else -> "nostalgia"
                }
                val lane = ListeningTasteTracker.DjLane.fromId(laneId)
                val player = connection
                if (player == null) {
                    reply(context.getString(R.string.nano_dj_no_player))
                } else {
                    reply(context.getString(R.string.nano_dj_switching_now, lane.displayName))
                    launchBusy {
                        laneName = lane.displayName
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setActiveLane(context, lane)
                        }
                        NanoDjSession.stop()
                        NanoDjLauncher.start(
                            context,
                            player,
                            NanoDjSession.isSpeakEnabled(),
                            replaceCurrentQueue = true,
                        ).onFailure {
                            reply(failureText(it))
                        }
                    }
                }
            }

            songRequest != null -> {
                // "play <any song>" / "can you play <song>" / "after this, play <song>" /
                // "put on <song>" searches YouTube Music and adds the result to the queue.
                launchBusy { reply(searchAndQueue(songRequest)) }
            }

            normalized.contains("why") || normalized.contains("explain") -> {
                // Answer for real (the host line is in the prompt's context); the canned
                // commentary is only the last-resort fallback inside answerNaturally.
                launchBusy {
                    reply(answerNaturally(command))
                }
            }

            normalized.contains("modify") && normalized.contains("dj playlist") -> {
                reply(context.getString(R.string.nano_dj_playlist_is_queue))
            }

            else -> {
                launchBusy {
                    reply(answerNaturally(command))
                }
            }
        }
    }

    fun executeActions(command: String, actions: List<DjPlannedAction>) {
        launchBusy {
            val results = actions.map { executePlannedAction(it) }
            val conversation =
                messages.takeLast(12).joinToString("\n") { m ->
                    if (m.fromDj) "DJ: ${m.text}" else "Listener: ${m.text}"
                }
            val summary =
                DjAgent.summarize(
                    GeminiNanoClient.get(context),
                    command,
                    results,
                    conversation,
                )
            reply(summary ?: fallbackSummary(results))
        }
    }

    fun destructiveConfirmationMessage(actions: List<DjPlannedAction>): String =
        actions.joinToString("\n\n") { action ->
            when (action.action) {
                "clear_queue" -> context.getString(R.string.nano_dj_clear_queue_confirm)
                "delete_playlist" ->
                    context.getString(
                        R.string.nano_dj_delete_playlist_confirm,
                        action.args["name"].orEmpty().trim().trim('"', '\''),
                    )
                "download" -> {
                    val scope = action.args["scope"].orEmpty().lowercase()
                    val count =
                        if (scope == "queue") queuedMedia().size
                        else if (currentMedia() != null) 1 else 0
                    context.getString(R.string.nano_dj_download_confirm, count)
                }
                else -> action.action
            }
        }

    fun playPreview(preview: DjPlaylistPreview) {
        val player = connection ?: return
        player.playQueue(
            ListQueue(
                title = preview.name,
                items = preview.songs.map { it.toMediaItem() },
                startIndex = 0,
            ),
        )
    }

    fun savePreviewToLibrary(preview: DjPlaylistPreview) {
        launchBusy {
            runCatching {
                withContext(Dispatchers.IO) {
                    // ensureLocalPlaylist bookmarks the playlist so it actually shows up in
                    // the library (plain inserts without bookmarkedAt stay invisible there).
                    val playlist = database.ensureLocalPlaylist(preview.name)
                    database.withTransaction {
                        preview.songs.forEach { upsert(it.toSongEntity()) }
                        playlistBlocking(playlist.id)?.let { local ->
                            addSongsToPlaylist(local, preview.songs.map { it.id to null })
                        }
                    }
                }
            }.onSuccess {
                reply(context.getString(R.string.nano_dj_playlist_saved, preview.name))
            }.onFailure {
                reply(failureText(it))
            }
        }
    }

    fun runCommand(raw: String) {
        val command = raw.trim()
        if (command.isBlank() || busy) return
        messages += MetroDjMessage(false, command)
        input = ""
        val normalized = command.lowercase()
        val songRequest = extractSongQuery(command)

        scope.launch {
            busy = true
            try {
                val plan = DjAgent.plan(GeminiNanoClient.get(context), command, currentState())
                when (plan) {
                    is DjPlan.Chat -> reply(plan.reply)
                    is DjPlan.Actions -> {
                        val createAction = plan.actions.firstOrNull { it.action == "create_playlist" }
                        val addAction = plan.actions.firstOrNull { it.action == "add_to_playlist" }
                        if (createAction != null && addAction != null) {
                            val name = createAction.args["name"].orEmpty().trim().trim('"', '\'')
                            val query = addAction.args["query"].orEmpty().trim()
                            if (name.isBlank()) {
                                reply(context.getString(R.string.nano_dj_playlist_name_needed))
                            } else {
                                launchBusy {
                                    val songs =
                                        if (query.isNotBlank()) searchSongs(query)
                                        else listOfNotNull(currentMedia())
                                    if (songs.isEmpty()) {
                                        reply(
                                            if (query.isNotBlank()) {
                                                context.getString(R.string.nano_dj_song_not_found, query)
                                            } else {
                                                context.getString(R.string.nano_dj_no_current_song)
                                            },
                                        )
                                    } else {
                                        messages +=
                                            MetroDjMessage(
                                                fromDj = true,
                                                text = context.getString(R.string.nano_dj_playlist_preview, name),
                                                playlistPreview = DjPlaylistPreview(name, songs),
                                            )
                                    }
                                }
                            }
                        } else {
                            val destructive = plan.actions.filter { it.action in DESTRUCTIVE_ACTIONS }
                            if (destructive.isNotEmpty()) {
                                pendingConfirm =
                                    PendingConfirm(
                                        message = destructiveConfirmationMessage(destructive),
                                        confirm = { executeActions(command, plan.actions) },
                                    )
                            } else {
                                executeActions(command, plan.actions)
                            }
                        }
                    }
                    null -> runLegacyCommand(command, normalized, songRequest)
                }
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(commentary) {
        // Host lines are republished on every queue batch; only show a line in chat once so the
        // same commentary never bubbles up over and over.
        commentary?.takeIf { it.isNotBlank() }?.let { line ->
            if (NanoDjChatHistory.lastCommentaryLine != line) {
                NanoDjChatHistory.lastCommentaryLine = line
                messages += MetroDjMessage(true, line)
            }
        }
    }

    // Keep the conversation pinned to the newest message when a new one arrives (or the typing
    // indicator while the DJ is busy). We do NOT re-scroll on list height changes: the sheet
    // shrinks when the IME opens (clipping the TOP of the list, not the newest message), and
    // forcing a scroll there would yank the user back to the bottom every time they try to read
    // history while typing.
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            messagesListState.scrollToItem(if (busy) messages.size else messages.lastIndex)
        }
    }

    LaunchedEffect(sessionId) {
        // A new radio session is a fresh conversation. The chat itself is never reset just by
        // opening the sheet again (messages live in NanoDjChatHistory and survive it).
        if (sessionId != NanoDjChatHistory.sessionId) {
            // If the user just triggered the restart from chat, carry that exchange over so
            // quick actions visibly work instead of vanishing. The session id can bump while
            // the command is still being executed (the DJ's reply lands afterwards), so an
            // in-flight listener message is carried too, not only complete user → DJ pairs.
            val carried =
                when {
                    messages.isEmpty() -> emptyList()
                    !messages.last().fromDj -> messages.takeLast(1).toList()
                    messages.size >= 2 && !messages[messages.size - 2].fromDj ->
                        messages.takeLast(2).toList()
                    else -> emptyList()
                }
            messages.clear()
            input = ""
            NanoDjChatHistory.lastCommentaryLine = null
            messages += MetroDjMessage(true, context.getString(R.string.nano_dj_chat_welcome))
            if (carried.isNotEmpty()) messages += carried
            NanoDjChatHistory.sessionId = sessionId
        }
        laneName = withContext(Dispatchers.IO) {
            ListeningTasteTracker.loadMergedTaste(context).lane.displayName
        }
    }

    val quickActions =
        if (active) {
            // Complex tasks / conversation starters, not trivial one-tap actions — the player
            // already has dedicated buttons for skip, like, and the like.
            listOf(
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_explain), "why did you choose this?"),
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_more_like_this), "more like this"),
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_next), "what should I listen to next?"),
            )
        } else {
            listOf(
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_ask_taste), "what do you know about my taste?"),
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_ask_recommendation), "what should I listen to right now?"),
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_chat_music), "tell me something interesting about music"),
            )
        }

    // Voice input via a headless SpeechRecognizer — no Google dialog. The mic button morphs
    // into a loudness-reactive waveform while listening and auto-sends the transcription.
    var isListening by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableStateOf(0f) }
    val speechRecognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val runCommandState = rememberUpdatedState<(String) -> Unit>({ runCommand(it) })
    var pendingVoiceStart by remember { mutableStateOf(false) }
    val voicePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingVoiceStart = true
            } else {
                Toast.makeText(context, R.string.nano_dj_voice_permission, Toast.LENGTH_SHORT).show()
            }
        }

    fun startListening() {
        val recognizer = speechRecognizer.value
        if (recognizer == null) {
            Toast.makeText(context, R.string.nano_dj_voice_unavailable, Toast.LENGTH_SHORT).show()
            if (headless) onDismiss()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
        voiceRms = 0f
        isListening = true
        onVoiceState?.invoke(true, 0f)
        runCatching { recognizer.startListening(intent) }.onFailure {
            isListening = false
            onVoiceState?.invoke(false, 0f)
            Toast.makeText(context, R.string.nano_dj_voice_error, Toast.LENGTH_SHORT).show()
            if (headless) onDismiss()
        }
    }

    fun stopListening() {
        runCatching { speechRecognizer.value?.stopListening() }
        isListening = false
        voiceRms = 0f
        onVoiceState?.invoke(false, 0f)
    }

    // Hold-to-voice: start listening the moment the headless sheet appears (no popup).
    LaunchedEffect(headless) {
        if (headless) startListening()
    }

    val startListeningAction = rememberUpdatedState<() -> Unit>({ startListening() })

    // Resume listening once the user grants mic permission via the system dialog.
    LaunchedEffect(pendingVoiceStart) {
        if (pendingVoiceStart) {
            pendingVoiceStart = false
            startListeningAction.value()
        }
    }

    DisposableEffect(Unit) {
        val recognizer =
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            } else {
                null
            }
        speechRecognizer.value = recognizer
        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    voiceRms = 0f
                    onVoiceState?.invoke(true, 0f)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    voiceRms = rmsdB
                    if (isListening) onVoiceState?.invoke(true, rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    // Keep the waveform until results or an error arrive.
                }

                override fun onError(error: Int) {
                    isListening = false
                    voiceRms = 0f
                    onVoiceState?.invoke(false, 0f)
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(context, R.string.nano_dj_voice_error, Toast.LENGTH_SHORT).show()
                    }
                    if (headless) onDismiss()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    voiceRms = 0f
                    onVoiceState?.invoke(false, 0f)
                    val text =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                    if (!text.isNullOrBlank()) {
                        runCommandState.value(text)
                    } else if (headless) {
                        onDismiss()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            },
        )
        onDispose {
            runCatching { speechRecognizer.value?.destroy() }
            speechRecognizer.value = null
            isListening = false
            voiceRms = 0f
        }
    }

    if (headless) {
        // Headless mode: no popup at all — the recognizer above listens and runCommand handles
        // the request; replies are spoken via onSpokenReply.
        return
    }

    val chatContent: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (fullScreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.heightIn(max = 640.dp)
                        },
                    )
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
        ) {
            LazyColumn(
                state = messagesListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message ->
                    PopInMessage {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                if (message.fromDj) Arrangement.Start else Arrangement.End,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (message.fromDj) {
                                AuraIconButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.size(28.dp),
                                    containerColor = AuraSpotifyGreen.copy(alpha = 0.16f),
                                    contentColor = AuraSpotifyGreen,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.radio),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .padding(horizontal = 6.dp),
                                horizontalAlignment =
                                    if (message.fromDj) Alignment.Start else Alignment.End,
                            ) {
                                Text(
                                    text = if (message.fromDj) stringResource(R.string.nano_dj_badge) else stringResource(R.string.nano_dj_you),
                                    color = if (message.fromDj) AuraSpotifyGreen else Color.White.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    text = message.text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (message.fromDj) 4.dp else 16.dp,
                                                bottomEnd = if (message.fromDj) 16.dp else 4.dp,
                                            ),
                                        )
                                        .background(
                                            if (message.fromDj) AuraSpotifyGreen.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.12f),
                                        )
                                        .padding(horizontal = 13.dp, vertical = 9.dp),
                                )
                                message.playlistPreview?.let { preview ->
                                    Column(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(AuraPlayerChrome)
                                                .padding(12.dp),
                                    ) {
                                        Text(
                                            text = preview.name,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Column {
                                                preview.songs.take(3).forEachIndexed { index, song ->
                                                    Text(
                                                        text = "${index + 1}. ${song.title}",
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(vertical = 3.dp),
                                                    )
                                                }
                                            }
                                            if (preview.songs.size > 3) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .height(36.dp)
                                                            .align(Alignment.BottomCenter)
                                                            .background(
                                                                Brush.verticalGradient(
                                                                    listOf(Color.Transparent, AuraPlayerChrome),
                                                                ),
                                                            ),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AuraPrimaryButton(
                                                onClick = { playPreview(preview) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding =
                                                    androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.play),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.nano_dj_playlist_play))
                                            }
                                            AuraSecondaryAction(
                                                text = stringResource(R.string.nano_dj_playlist_add_to_library),
                                                onClick = { savePreviewToLibrary(preview) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (busy) {
                    item(key = "dj_typing") {
                        DjTypingRow()
                    }
                }
                if (messages.lastOrNull()?.fromDj == true && !busy) {
                    item(key = "dj_suggestions") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            // A little breathing room below the last DJ message.
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            quickActions.forEach { action ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            // runCommand itself guards against double-execution while busy.
                                            .clickable { runCommand(action.command) }
                                            .padding(horizontal = 2.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = action.label,
                                        color = AuraSpotifyGreen,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_forward),
                                        contentDescription = null,
                                        tint = AuraSpotifyGreen.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // The composer is a fixed footer below the scrollable conversation — a compact pill
            // in the same spirit (and roughly the same size) as the mini player.
            Row(
                modifier =
                    Modifier
                    .fillMaxWidth()
                    .auraFloatingIsland(
                        shape = RoundedCornerShape(50),
                        color = AuraPlayerChrome,
                        elevation = 6.dp,
                    )
                    .padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Multi-line composer: grows up to a few lines instead of scrolling one line.
            // BasicTextField keeps the pill compact (Material's field min-height is too tall
            // for the mini-player-sized composer); everything is transparent anyway.
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White.copy(alpha = 0.7f)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { runCommand(input) }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        innerTextField()
                    }
                },
            )
            AuraIconButton(
                onClick = { if (isListening) stopListening() else startListening() },
                modifier = Modifier.size(36.dp),
                containerColor = if (isListening) AuraSpotifyGreen.copy(alpha = 0.22f) else Color.Transparent,
                contentColor = if (isListening) AuraSpotifyGreen else Color.White.copy(alpha = 0.72f),
            ) {
                if (isListening) {
                    VoiceWave(voiceRms, Modifier.size(20.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.mic),
                        contentDescription = stringResource(R.string.nano_dj_voice),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            AuraPrimaryButton(
                onClick = { runCommand(input) },
                modifier = Modifier.height(40.dp),
                enabled = input.isNotBlank() && !busy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.send),
                    contentDescription = stringResource(R.string.send),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    }

    if (fullScreen) {
        chatContent()
    } else {
        AuraBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = AuraElevated,
            contentWindowInsets = { WindowInsets.ime.union(WindowInsets.systemBars) },
        ) {
            chatContent()
        }
    }

    pendingConfirm?.let { pending ->
        DefaultDialog(
            onDismiss = { pendingConfirm = null },
            title = { Text(stringResource(R.string.nano_dj_confirmation_title)) },
            content = { Text(pending.message) },
            buttons = {
                AuraSecondaryAction(
                    onClick = {
                        pendingConfirm = null
                        pending.confirm()
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
                AuraSecondaryAction(onClick = { pendingConfirm = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * iOS-style pop-in for chat bubbles: new messages (from either side) fade in and settle with
 * a small scale spring on first appearance. Kept subtle so DJ and listener bubbles feel
 * consistent together.
 */
@Composable
private fun PopInMessage(content: @Composable () -> Unit) {
    // Named to avoid clashing with GraphicsLayerScope's own scale/alpha properties.
    val scaleAnim = remember { Animatable(0.92f) }
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { alphaAnim.animateTo(1f, tween(durationMillis = 160)) }
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    Box(
        modifier =
            Modifier.graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                alpha = alphaAnim.value
            },
    ) {
        content()
    }
}

/**
 * "DJ is typing" bubble — three staggered pulsing dots in a regular DJ bubble, shown while a
 * command is being processed so the chat never looks dead.
 */
@Composable
private fun DjTypingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        AuraIconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.size(28.dp),
            containerColor = AuraSpotifyGreen.copy(alpha = 0.16f),
            contentColor = AuraSpotifyGreen,
        ) {
            Icon(
                painter = painterResource(R.drawable.radio),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.nano_dj_badge),
                color = AuraSpotifyGreen,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 16.dp,
                            ),
                        )
                        .background(AuraSpotifyGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val transition = rememberInfiniteTransition(label = "djTyping")
                repeat(3) { i ->
                    val dotAlpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(durationMillis = 420),
                                repeatMode = RepeatMode.Reverse,
                                initialStartOffset = StartOffset(i * 160),
                            ),
                        label = "djTypingDot$i",
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(AuraSpotifyGreen.copy(alpha = dotAlpha)),
                    )
                }
            }
        }
    }
}

/**
 * Three-bar equalizer that pulses with the speaker's loudness (driven by SpeechRecognizer's
 * onRmsChanged values, 0..~10 dB). Used in place of the mic icon while the DJ is listening.
 */
@Composable
private fun VoiceWave(
    rms: Float,
    modifier: Modifier = Modifier,
    color: Color = AuraSpotifyGreen,
) {
    val smooth by animateFloatAsState(
        targetValue = (rms / 10f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "voiceWaveRms",
    )
    // Fixed-size box keeps the three bars centered in the button regardless of the loudness
    // animation; the bars share a common baseline like a classic equalizer.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(0.45f, 1f, 0.65f).forEach { multiplier ->
                val fraction = (0.3f + 0.7f * smooth * multiplier).coerceIn(0.3f, 1f)
                Box(
                    Modifier
                        .width(3.dp)
                        .height(6.dp + 12.dp * fraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
    }
}
