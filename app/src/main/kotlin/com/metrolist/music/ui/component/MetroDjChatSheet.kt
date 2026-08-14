/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.NanoDjLauncher
import com.metrolist.music.ai.NanoDjSession
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.ui.component.aura.AuraIconButton
import com.metrolist.music.ui.component.aura.AuraPrimaryButton
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MetroDjMessage(val fromDj: Boolean, val text: String)

@Composable
fun MetroDjChatButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val sheetState = LocalBottomSheetPageState.current
    AuraIconButton(
        onClick = {
            sheetState.show { MetroDjChatSheet(onDismiss = sheetState::dismiss) }
        },
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
}

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
    val commentary by NanoDjSession.commentary.collectAsStateWithLifecycle()
    val messages = remember { mutableStateListOf<MetroDjMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var laneName by remember { mutableStateOf(ListeningTasteTracker.DjLane.ARTIST_RADIO.displayName) }
    var confirmationText by remember { mutableStateOf<String?>(null) }
    var pendingConfirmation by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun reply(text: String) {
        messages += MetroDjMessage(true, text)
    }

    fun requestConfirmation(message: String, action: () -> Unit) {
        confirmationText = message
        pendingConfirmation = action
    }

    fun currentMedia(): MediaMetadata? =
        runCatching { connection?.player?.currentMediaItem?.metadata }.getOrNull()

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
            normalized == "start" || normalized.contains("start metro dj") || normalized.contains("turn on") -> {
                val player = connection
                if (player == null) {
                    reply(context.getString(R.string.nano_dj_no_player))
                } else {
                    scope.launch {
                        busy = true
                        NanoDjLauncher.start(context, player, NanoDjSession.isSpeakEnabled())
                            .onFailure { reply(it.message ?: context.getString(R.string.nano_dj_action_failed)) }
                            .onSuccess { reply(context.getString(R.string.nano_dj_started)) }
                        busy = false
                    }
                }
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
                (normalized.contains("metro dj") || normalized.contains("recommendation")) -> {
                scope.launch {
                    val playlistId = withContext(Dispatchers.IO) {
                        database.playlistEntitiesByNameAsc()
                            .firstOrNull {
                                it.name.equals(
                                    SpotifyImportManager.RECOMMENDATIONS_PLAYLIST_NAME,
                                    ignoreCase = true,
                                )
                            }?.id
                    }
                    if (playlistId == null) {
                        reply(context.getString(R.string.nano_dj_playlist_not_found, SpotifyImportManager.RECOMMENDATIONS_PLAYLIST_NAME))
                    } else {
                        navController.navigate("local_playlist/$playlistId")
                        reply(context.getString(R.string.nano_dj_playlist_opened))
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

            normalized.contains("dislike") || normalized.contains("not for my taste") -> {
                val media = currentMedia()
                if (media == null) {
                    reply(context.getString(R.string.nano_dj_no_current_song))
                } else {
                    scope.launch {
                        busy = true
                        withContext(Dispatchers.IO) {
                            ListeningTasteTracker.setExcluded(
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

            normalized.contains("chill") || normalized.contains("hype") || normalized.contains("focus") ||
                normalized.contains("nostalgia") || normalized.contains("artist radio") -> {
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

            else -> reply(context.getString(R.string.nano_dj_command_help))
        }
    }

    LaunchedEffect(commentary) {
        commentary?.takeIf { it.isNotBlank() }?.let { line ->
            if (messages.lastOrNull()?.text != line) messages += MetroDjMessage(true, line)
        }
    }

    LaunchedEffect(Unit) {
        laneName = withContext(Dispatchers.IO) {
            ListeningTasteTracker.loadMergedTaste(context).lane.displayName
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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Text(stringResource(R.string.nano_dj_section), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (active) stringResource(R.string.nano_dj_on_air) else stringResource(R.string.nano_dj_off_air),
            color = if (active) AuraSpotifyGreen else Color.White.copy(alpha = 0.65f),
        )
        Text(
            text = stringResource(R.string.nano_dj_lane_format, laneName),
            color = Color.White.copy(alpha = 0.65f),
        )
        commentary?.let { Text(it, color = Color.White.copy(alpha = 0.75f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.nano_dj_action_start),
                stringResource(R.string.nano_dj_action_chill),
                stringResource(R.string.nano_dj_action_skip),
            ).forEach { label ->
                AssistChip(
                    onClick = { runCommand(label) },
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        labelColor = Color.White,
                    ),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 180.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages) { message ->
                Text(
                    text = if (message.fromDj) "Metro DJ: ${message.text}" else "You: ${message.text}",
                    color = if (message.fromDj) AuraSpotifyGreen else Color.White,
                )
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.nano_dj_chat_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { runCommand(input) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraSpotifyGreen,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuraPrimaryButton(onClick = { runCommand(input) }, enabled = input.isNotBlank() && !busy) {
                Text(stringResource(R.string.send))
            }
            AuraSecondaryAction(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
