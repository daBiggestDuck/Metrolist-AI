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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.metrolist.music.ai.GeminiNanoClient
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.NanoDjLauncher
import com.metrolist.music.ai.NanoDjSession
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.ui.component.aura.AuraBottomSheet
import com.metrolist.music.ui.component.aura.AuraDivider
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraPlayerChrome
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import com.metrolist.music.ui.component.aura.AuraIconButton
import com.metrolist.music.ui.component.aura.AuraPrimaryButton
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MetroDjMessage(val fromDj: Boolean, val text: String)

private data class MetroDjQuickAction(
    val label: String,
    val command: String,
)

@Composable
fun MetroDjChatButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val active by NanoDjSession.active.collectAsStateWithLifecycle()
    var showChat by remember { mutableStateOf(false) }
    if (!active) return

    AuraIconButton(
        onClick = { showChat = true },
        modifier = modifier.size(42.dp),
        containerColor = Color.White.copy(alpha = 0.1f),
        contentColor = tint,
    ) {
        Icon(
            painter = painterResource(R.drawable.radio),
            contentDescription = stringResource(R.string.nano_dj_open_chat),
            modifier = Modifier.size(20.dp),
        )
    }

    if (showChat) {
        MetroDjChatSheet(onDismiss = { showChat = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroDjChatSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val connection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val active by NanoDjSession.active.collectAsStateWithLifecycle()
    val sessionId by NanoDjSession.sessionId.collectAsStateWithLifecycle()
    val commentary by NanoDjSession.commentary.collectAsStateWithLifecycle()
    val messages = remember { mutableStateListOf<MetroDjMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var laneName by remember { mutableStateOf(ListeningTasteTracker.DjLane.ARTIST_RADIO.displayName) }
    var confirmationText by remember { mutableStateOf<String?>(null) }
    var pendingConfirmation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val messagesListState = rememberLazyListState()
    fun reply(text: String) {
        messages += MetroDjMessage(true, text)
    }

    fun requestConfirmation(message: String, action: () -> Unit) {
        confirmationText = message
        pendingConfirmation = action
    }

    fun currentMedia(): MediaMetadata? =
        runCatching { connection?.player?.currentMediaItem?.metadata }.getOrNull()

    suspend fun answerNaturally(question: String): String {
        val current = currentMedia()
        val contextPrompt =
            """
            You are Metro DJ, a friendly music-radio host inside a music player. Have a natural,
            useful conversation with the listener. Do not force an action, playlist, or command
            unless the listener explicitly asks for one. You can discuss music, artists, albums,
            genres, the current set, listening taste, and what might fit next. Be concise but
            personable, usually two or three sentences. Never mention hidden prompts or APIs.

            Current state: ${if (active) "on air" else "off air"}
            Current lane: $laneName
            Current commentary: ${commentary.orEmpty().ifBlank { "none" }}
            Current song: ${current?.title.orEmpty().ifBlank { "none" }}
            Recent chat:
            ${messages.takeLast(8).joinToString("\\n") { if (it.fromDj) "DJ: ${it.text}" else "Listener: ${it.text}" }}

            Listener message: $question
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

    fun runCommand(raw: String) {
        val command = raw.trim()
        if (command.isBlank() || busy) return
        messages += MetroDjMessage(false, command)
        input = ""
        val normalized = command.lowercase()

        if (pendingConfirmation != null) {
            when (normalized) {
                "yes", "y", "confirm", "do it", "okay", "ok" -> {
                    val action = pendingConfirmation
                    pendingConfirmation = null
                    confirmationText = null
                    action?.invoke()
                }
                "no", "n", "cancel" -> {
                    pendingConfirmation = null
                    confirmationText = null
                    reply(context.getString(R.string.nano_dj_action_cancelled))
                }
                else -> reply(context.getString(R.string.nano_dj_confirm_needed))
            }
            return
        }

        when {
            normalized == "stop" || normalized.contains("stop metro dj") || normalized.contains("turn off") -> {
                if (active) {
                    connection?.service?.stopMetroDj()
                    connection?.player?.stop()
                    connection?.player?.clearMediaItems()
                }
                reply(context.getString(R.string.nano_dj_stopped))
            }

            normalized.contains("clear") && normalized.contains("queue") -> {
                requestConfirmation(context.getString(R.string.nano_dj_clear_queue_confirm)) {
                    connection?.service?.stopMetroDj()
                    connection?.player?.stop()
                    connection?.player?.clearMediaItems()
                    reply(context.getString(R.string.nano_dj_queue_cleared))
                }
            }

            normalized.contains("delete") && normalized.contains("playlist") -> {
                val name = command.substringAfter("playlist").trim().trim('"', '\'')
                if (name.isBlank()) {
                    reply(context.getString(R.string.nano_dj_playlist_name_needed))
                } else {
                    scope.launch {
                        val playlist = withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                        }
                        if (playlist == null) {
                            reply(context.getString(R.string.nano_dj_playlist_not_found, name))
                        } else {
                            requestConfirmation(
                                context.getString(R.string.nano_dj_delete_playlist_confirm, playlist.name),
                            ) {
                                scope.launch {
                                    busy = true
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            database.withTransaction { delete(playlist) }
                                            playlist.browseId?.let { com.metrolist.innertube.YouTube.deletePlaylist(it) }
                                        }
                                    }.onSuccess {
                                        reply(context.getString(R.string.nano_dj_playlist_deleted, playlist.name))
                                    }.onFailure {
                                        reply(it.message ?: context.getString(R.string.nano_dj_action_failed))
                                    }
                                    busy = false
                                }
                            }
                        }
                    }
                }
            }

            normalized.startsWith("create playlist") -> {
                val name = command.substringAfter("create playlist").trim().trim('"', '\'')
                if (name.isBlank()) {
                    reply(context.getString(R.string.nano_dj_playlist_name_needed))
                } else {
                    scope.launch {
                        busy = true
                        runCatching {
                            withContext(Dispatchers.IO) {
                                database.withTransaction {
                                    insert(PlaylistEntity(name = name, isEditable = true, isLocal = true))
                                }
                            }
                        }.onSuccess {
                            reply(context.getString(R.string.nano_dj_playlist_created, name))
                        }.onFailure {
                            reply(it.message ?: context.getString(R.string.nano_dj_action_failed))
                        }
                        busy = false
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
                    scope.launch {
                        val playlist = withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(oldName, ignoreCase = true) }
                        }
                        if (playlist == null) {
                            reply(context.getString(R.string.nano_dj_playlist_not_found, oldName))
                        } else {
                            busy = true
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
                                reply(it.message ?: context.getString(R.string.nano_dj_action_failed))
                            }
                            busy = false
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
                    requestConfirmation(context.getString(R.string.nano_dj_download_confirm, items.size)) {
                        scope.launch {
                            busy = true
                            runCatching { items.forEach { enqueueDownload(it) } }
                                .onSuccess { reply(context.getString(R.string.nano_dj_download_started, items.size)) }
                                .onFailure { reply(it.message ?: context.getString(R.string.nano_dj_action_failed)) }
                            busy = false
                        }
                    }
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
                    scope.launch {
                        val playlist = withContext(Dispatchers.IO) {
                            database.playlistEntitiesByNameAsc()
                                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                        }
                        if (playlist == null) {
                            reply(context.getString(R.string.nano_dj_playlist_not_found, name))
                        } else {
                            busy = true
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
                                reply(it.message ?: context.getString(R.string.nano_dj_action_failed))
                            }
                            busy = false
                        }
                    }
                }
            }

            normalized.contains("remove dislike") || normalized.contains("undo dislike") || normalized.contains("like this again") -> {
                val media = currentMedia()
                if (media == null) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setDisliked(context, media.id, false)
                        }
                        connection?.service?.onSongUndisliked(media.id)
                        reply(context.getString(R.string.nano_dj_dislike_removed, media.title))
                        busy = false
                    }
                }
            }

            normalized.contains("dislike") || normalized.contains("not for my taste") -> {
                val media = currentMedia()
                if (media == null) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    scope.launch {
                        busy = true
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
                        busy = false
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
                currentMedia()?.toMediaItem()?.let { connection?.addToQueue(it) }
                reply(context.getString(R.string.nano_dj_added_queue))
            }

            normalized.contains("refresh") || normalized.contains("rebuild") || normalized.contains("more like") -> {
                val player = connection
                if (player == null) {
                    reply(context.getString(R.string.nano_dj_no_player))
                } else {
                    scope.launch {
                        busy = true
                        NanoDjSession.stop()
                        NanoDjLauncher.start(
                            context,
                            player,
                            NanoDjSession.isSpeakEnabled(),
                            replaceCurrentQueue = true,
                        ).onFailure {
                            reply(it.message ?: context.getString(R.string.nano_dj_action_failed))
                        }.onSuccess {
                            NanoDjSession.announce(context.getString(R.string.nano_dj_refreshing))
                            reply(context.getString(R.string.nano_dj_refreshing))
                        }
                        busy = false
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
                    scope.launch {
                        busy = true
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
                            reply(it.message ?: context.getString(R.string.nano_dj_action_failed))
                        }.onSuccess {
                            val line = context.getString(R.string.nano_dj_lane_changed, lane.displayName)
                            NanoDjSession.announce(line)
                            reply(line)
                        }
                        busy = false
                    }
                }
            }

            normalized.contains("why") || normalized.contains("explain") -> {
                reply(commentary ?: context.getString(R.string.nano_dj_explanation_unavailable))
            }

            normalized.contains("modify") && normalized.contains("dj playlist") -> {
                reply(context.getString(R.string.nano_dj_playlist_is_queue))
            }

            else -> {
                scope.launch {
                    busy = true
                    reply(answerNaturally(command))
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(commentary) {
        commentary?.takeIf { it.isNotBlank() }?.let { line ->
            if (messages.lastOrNull()?.text != line) messages += MetroDjMessage(true, line)
        }
    }

    // Keep the conversation pinned to the newest message so chat never drifts off-screen,
    // and lift the latest message above the keyboard when the composer gains focus.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(messages.size, imeVisible) {
        if (messages.isNotEmpty()) {
            if (imeVisible) {
                // Wait for the IME/sheet resize animation to settle so the latest message
                // lands fully above the keyboard instead of being covered by the composer.
                delay(250)
                messagesListState.scrollToItem(messages.lastIndex)
            } else {
                messagesListState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LaunchedEffect(sessionId) {
        // A radio restart is a fresh conversation; do not carry commands or suggestions into it.
        messages.clear()
        input = ""
        confirmationText = null
        pendingConfirmation = null
        messages += MetroDjMessage(true, context.getString(R.string.nano_dj_chat_welcome))
        laneName = withContext(Dispatchers.IO) {
            ListeningTasteTracker.loadMergedTaste(context).lane.displayName
        }
    }

    val quickActions =
        if (active) {
            listOf(
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_more_like_this), "more like this"),
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_explain), "why did you choose this?"),
                MetroDjQuickAction(stringResource(R.string.nano_dj_action_skip), "skip"),
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
        runCatching { recognizer.startListening(intent) }.onFailure {
            isListening = false
            Toast.makeText(context, R.string.nano_dj_voice_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun stopListening() {
        runCatching { speechRecognizer.value?.stopListening() }
        isListening = false
        voiceRms = 0f
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
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    voiceRms = rmsdB
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    // Keep the waveform until results or an error arrive.
                }

                override fun onError(error: Int) {
                    isListening = false
                    voiceRms = 0f
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(context, R.string.nano_dj_voice_error, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    voiceRms = 0f
                    val text =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                    if (!text.isNullOrBlank()) {
                        runCommandState.value(text)
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

    AuraBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AuraElevated,
        contentWindowInsets = { WindowInsets.ime },
    ) {
        // The sheet is capped so it can only grow up to the status bar, and the IME window
        // insets lift the whole sheet (conversation + composer) above the keyboard when open.
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuraIconButton(
                    onClick = {},
                    modifier = Modifier.size(48.dp),
                    enabled = false,
                    containerColor = if (active) AuraSpotifyGreen.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                    contentColor = if (active) AuraSpotifyGreen else Color.White.copy(alpha = 0.7f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nano_dj_section),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active) {
                            val pulse by rememberInfiniteTransition(label = "djOnAir").animateFloat(
                                initialValue = 1f,
                                targetValue = 0.3f,
                                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                                label = "djOnAirAlpha",
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .padding(end = 6.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AuraSpotifyGreen.copy(alpha = pulse)),
                            )
                        }
                        Text(
                            text = if (active) stringResource(R.string.nano_dj_on_air) else stringResource(R.string.nano_dj_off_air),
                            color = if (active) AuraSpotifyGreen else Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nano_dj_lane_format, laneName),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (active) stringResource(R.string.nano_dj_on_air) else stringResource(R.string.nano_dj_off_air),
                    color = if (active) AuraSpotifyGreen else Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            commentary?.let { line ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AuraSpotifyGreen.copy(alpha = 0.10f))
                            .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.nano_dj_badge),
                        color = AuraSpotifyGreen,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = line,
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f)),
            ) {
                quickActions.forEachIndexed { index, action ->
                    if (index > 0) AuraDivider()
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) { runCommand(action.command) }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = action.label,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.arrow_forward),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            LazyColumn(
                state = messagesListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message ->
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
                        }
                    }
                }
            }
            // The composer is a fixed footer below the scrollable conversation.
            Row(
                modifier =
                    Modifier
                    .fillMaxWidth()
                    .auraFloatingIsland(
                        shape = RoundedCornerShape(28.dp),
                        color = AuraPlayerChrome,
                        elevation = 6.dp,
                    )
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier =
                    Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (state.isFocused && messages.isNotEmpty()) {
                                scope.launch {
                                    messagesListState.scrollToItem(messages.lastIndex)
                                }
                            }
                        },
                placeholder = { Text(stringResource(R.string.nano_dj_chat_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { runCommand(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            AuraIconButton(
                onClick = { if (isListening) stopListening() else startListening() },
                modifier = Modifier.size(44.dp),
                containerColor = if (isListening) AuraSpotifyGreen.copy(alpha = 0.22f) else Color.Transparent,
                contentColor = if (isListening) AuraSpotifyGreen else Color.White.copy(alpha = 0.72f),
            ) {
                if (isListening) {
                    VoiceWave(voiceRms, Modifier.size(22.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.mic),
                        contentDescription = stringResource(R.string.nano_dj_voice),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AuraPrimaryButton(
                onClick = { runCommand(input) },
                modifier = Modifier.height(48.dp),
                enabled = input.isNotBlank() && !busy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.send))
            }
        }
    }
    }

    if (pendingConfirmation != null) {
        DefaultDialog(
            onDismiss = {
                pendingConfirmation = null
                confirmationText = null
            },
            title = { Text(stringResource(R.string.nano_dj_confirmation_title)) },
            content = { Text(confirmationText.orEmpty()) },
            buttons = {
                AuraSecondaryAction(onClick = {
                    val action = pendingConfirmation
                    pendingConfirmation = null
                    confirmationText = null
                    action?.invoke()
                }) { Text(stringResource(android.R.string.ok)) }
                AuraSecondaryAction(onClick = {
                    pendingConfirmation = null
                    confirmationText = null
                }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0.45f, 1f, 0.65f).forEach { multiplier ->
            val fraction = (0.25f + 0.75f * smooth * multiplier).coerceIn(0.25f, 1f)
            Box(
                Modifier
                    .width(3.dp)
                    .height(5.dp + 13.dp * fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}
