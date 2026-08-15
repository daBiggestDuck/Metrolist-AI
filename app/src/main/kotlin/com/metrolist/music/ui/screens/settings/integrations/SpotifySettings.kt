/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.ai.TasteSummary
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.ListeningTasteSummaryKey
import com.metrolist.music.constants.SpotifyClientIdKey
import com.metrolist.music.constants.SpotifyDisplayNameKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.spotify.SpotifyApi
import com.metrolist.music.spotify.SpotifyAuth
import com.metrolist.music.spotify.SpotifyAuthException
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.spotify.SpotifyImportProgress
import com.metrolist.music.spotify.SpotifyPlaylistSummary
import com.metrolist.music.spotify.SpotifyTokenStore
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.component.aura.AuraBanner
import com.metrolist.music.ui.component.aura.AuraDivider
import com.metrolist.music.ui.component.aura.AuraHeader
import com.metrolist.music.ui.component.aura.AuraRow
import com.metrolist.music.ui.component.aura.AuraScreen
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSectionLabel
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun SpotifySettings(
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    var clientId by rememberPreference(
        SpotifyClientIdKey,
        BuildConfig.SPOTIFY_CLIENT_ID,
    )
    var displayName by rememberPreference(SpotifyDisplayNameKey, "")
    var tasteSummary by rememberPreference(SpotifyTasteSummaryKey, "")
    var tasteHints by rememberPreference(SpotifyTasteHintsKey, "")
    var topArtistsPref by rememberPreference(SpotifyTopArtistsKey, "")
    var topTracksPref by rememberPreference(SpotifyTopTracksKey, "")
    var listeningSummary by rememberPreference(ListeningTasteSummaryKey, "")
    val enableGeminiNano by rememberPreference(EnableGeminiNanoKey, true)
    var isConnected by remember {
        mutableStateOf(!SpotifyTokenStore.retrieve().isNullOrBlank())
    }
    var playlists by remember { mutableStateOf<List<SpotifyPlaylistSummary>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(SpotifyImportProgress()) }
    var showClientIdDialog by rememberSaveable { mutableStateOf(false) }
    var showDisconnectConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingSpotifyPlaylist by remember { mutableStateOf<SpotifyPlaylistSummary?>(null) }
    var showTasteOverwriteConfirm by remember { mutableStateOf(false) }
    var pendingTasteOverwrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showImportAllConfirm by remember { mutableStateOf(false) }
    var showTasteSuccessDialog by remember { mutableStateOf(false) }
    var tasteSuccessSummary by remember { mutableStateOf("") }
    var tasteSuccessTrackCount by remember { mutableIntStateOf(0) }
    var tasteSuccessUsedAi by remember { mutableStateOf(true) }
    var showTasteErrorDialog by remember { mutableStateOf(false) }
    var tasteErrorMessage by remember { mutableStateOf("") }

    fun showTasteSuccess(summary: String, trackCount: Int, usedAi: Boolean) {
        tasteSuccessSummary = summary.take(280)
        tasteSuccessTrackCount = trackCount
        tasteSuccessUsedAi = usedAi
        showTasteSuccessDialog = true
    }

    fun showTasteError(message: String?) {
        tasteErrorMessage = message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ai_error_unknown)
        showTasteErrorDialog = true
        statusMessage = tasteErrorMessage
        Toast.makeText(context, tasteErrorMessage, Toast.LENGTH_LONG).show()
    }

    fun requestTasteOverwrite(action: () -> Unit) {
        pendingTasteOverwrite = action
        showTasteOverwriteConfirm = true
    }

    val redirectNote = stringResource(R.string.spotify_redirect_uri_note)
    val connectLabel = stringResource(R.string.spotify_connect)
    val disconnectLabel = stringResource(R.string.spotify_disconnect)

    fun persistTasteProfile(
        artists: List<String>,
        tracks: List<Pair<String, String>>,
    ) {
        if (artists.isNotEmpty()) topArtistsPref = artists.joinToString("\n")
        if (tracks.isNotEmpty()) {
            topTracksPref = tracks.joinToString("\n") { "${it.first} — ${it.second}" }
        }
    }

    fun applyTasteProfile(
        artists: List<String>,
        tracks: List<Pair<String, String>>,
        summary: String,
        hints: List<String>,
    ) {
        TasteSummary.sanitizeOrNull(summary)?.let {
            tasteSummary = it
            listeningSummary = it
        }
        if (hints.isNotEmpty()) {
            tasteHints = hints.filter { TasteSummary.isUsable(it) }.joinToString("\n")
        }
        persistTasteProfile(artists, tracks)
    }

    fun runSpotifyPlaylistTaste(playlist: SpotifyPlaylistSummary) {
        scope.launch {
            isBusy = true
            statusMessage = null
            progress = SpotifyImportProgress(phase = "Fetching playlist tracks")
            val manager = SpotifyImportManager(database, context)
            try {
                val profile =
                    manager.buildTasteFromSpotifyPlaylist(
                        clientId = clientId,
                        playlist = playlist,
                        enableNano = enableGeminiNano,
                        onProgress = { p ->
                            scope.launch(Dispatchers.Main) { progress = p }
                        },
                    )
                applyTasteProfile(
                    profile.topArtists,
                    profile.topTracks,
                    profile.analysis.summary,
                    profile.analysis.searchHints,
                )
                showTasteSuccess(
                    profile.analysis.summary,
                    profile.topTracks.size,
                    profile.analysis.usedAi,
                )
                statusMessage =
                    context.getString(
                        R.string.spotify_playlist_taste_result,
                        playlist.name,
                        profile.topTracks.size,
                    )
            } catch (e: Exception) {
                showTasteError(e.message)
                reportException(e)
            } finally {
                manager.close()
                isBusy = false
            }
        }
    }

    fun importAllPlaylists() {
        scope.launch {
            isBusy = true
            statusMessage = null
            val snapshot = playlists
            val totalPlaylists = snapshot.size
            var totalMatched = 0
            var totalFailed = 0
            val manager = SpotifyImportManager(database, context)
            try {
                snapshot.forEachIndexed { index, pl ->
                    val label =
                        context.getString(
                            R.string.spotify_import_all_playlists_progress,
                            index + 1,
                            totalPlaylists,
                            pl.name,
                        )
                    progress =
                        SpotifyImportProgress(
                            current = index,
                            total = totalPlaylists,
                            phase = label,
                        )
                    try {
                        val result =
                            manager.importPlaylist(
                                clientId = clientId,
                                playlist = pl,
                                onProgress = { p ->
                                    scope.launch(Dispatchers.Main) {
                                        progress = p.copy(phase = label)
                                    }
                                },
                            )
                        totalMatched += result.matched
                        totalFailed += result.failed
                    } catch (e: Exception) {
                        Timber.tag("SpotifySettings")
                            .w(e, "Failed importing playlist %s", pl.name)
                        reportException(e)
                    }
                }
                statusMessage =
                    context.getString(
                        R.string.spotify_import_all_playlists_result,
                        totalPlaylists,
                        totalMatched,
                        totalFailed,
                    )
            } finally {
                manager.close()
                isBusy = false
            }
        }
    }

    fun refreshPlaylists() {
        scope.launch {
            val id = clientId
            if (id.isBlank() || !isConnected) return@launch
            try {
                isBusy = true
                val api = SpotifyApi()
                try {
                    val manager = SpotifyImportManager(database, context, api)
                    val token = manager.ensureValidToken(id)
                    playlists = api.getPlaylists(token)
                } finally {
                    api.close()
                }
            } catch (e: Exception) {
                Timber.tag("SpotifySettings").w(e, "Failed to load playlists")
                statusMessage = e.message
                reportException(e)
            } finally {
                isBusy = false
            }
        }
    }

    LaunchedEffect(isConnected, clientId) {
        if (isConnected && clientId.isNotBlank()) {
            refreshPlaylists()
        } else {
            playlists = emptyList()
        }
    }

    fun disconnectSpotify() {
        SpotifyTokenStore.clear()
        displayName = ""
        tasteSummary = ""
        tasteHints = ""
        topArtistsPref = ""
        topTracksPref = ""
        playlists = emptyList()
        isConnected = false
        statusMessage = null
    }

    if (showDisconnectConfirm) {
        DefaultDialog(
            onDismiss = { showDisconnectConfirm = false },
            title = { Text(stringResource(R.string.spotify_disconnect_confirm_title)) },
            content = {
                Text(
                    text = stringResource(R.string.spotify_disconnect_confirm_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showDisconnectConfirm = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(
                    onClick = {
                        showDisconnectConfirm = false
                        disconnectSpotify()
                    },
                ) {
                    Text(text = stringResource(R.string.spotify_disconnect))
                }
            },
        )
    }

    if (showTasteOverwriteConfirm) {
        DefaultDialog(
            onDismiss = {
                showTasteOverwriteConfirm = false
                pendingTasteOverwrite = null
            },
            title = { Text(stringResource(R.string.taste_overwrite_confirm_title)) },
            content = {
                Text(
                    text = stringResource(R.string.taste_overwrite_confirm_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = {
                    showTasteOverwriteConfirm = false
                    pendingTasteOverwrite = null
                }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(onClick = {
                    showTasteOverwriteConfirm = false
                    pendingTasteOverwrite?.invoke()
                    pendingTasteOverwrite = null
                }) {
                    Text(text = stringResource(R.string.taste_overwrite_confirm))
                }
            },
        )
    }

    if (showImportAllConfirm) {
        DefaultDialog(
            onDismiss = { showImportAllConfirm = false },
            title = { Text(stringResource(R.string.spotify_import_all_playlists_confirm_title)) },
            content = {
                Text(
                    text = stringResource(R.string.spotify_import_all_playlists_confirm_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showImportAllConfirm = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(onClick = {
                    showImportAllConfirm = false
                    importAllPlaylists()
                }) {
                    Text(text = stringResource(R.string.spotify_import_all_playlists))
                }
            },
        )
    }

    if (showTasteSuccessDialog) {
        DefaultDialog(
            onDismiss = { showTasteSuccessDialog = false },
            title = {
                Text(
                    stringResource(
                        if (tasteSuccessUsedAi) {
                            R.string.taste_updated_title
                        } else {
                            R.string.taste_updated_without_ai_title
                        },
                    ),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showTasteSuccessDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(
                text =
                    stringResource(
                        R.string.taste_updated_message,
                        tasteSuccessTrackCount,
                        tasteSuccessSummary,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }

    if (showTasteErrorDialog) {
        DefaultDialog(
            onDismiss = { showTasteErrorDialog = false },
            title = { Text(stringResource(R.string.taste_import_failed_title)) },
            buttons = {
                AuraSecondaryAction(onClick = { showTasteErrorDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(
                text =
                    stringResource(
                        R.string.taste_import_failed_message,
                        tasteErrorMessage,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }

    if (showClientIdDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.spotify_client_id)) },
            icon = { Icon(painterResource(R.drawable.key), null) },
            initialTextFieldValue = TextFieldValue(text = clientId),
            onDone = {
                clientId = it.trim()
                showClientIdDialog = false
            },
            onDismiss = { showClientIdDialog = false },
        )
    }

    pendingSpotifyPlaylist?.let { spotifyPl ->
        ListDialog(onDismiss = { pendingSpotifyPlaylist = null }) {
            item {
                Text(
                    text = spotifyPl.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.spotify_playlist_taste_use)) },
                    supportingContent = {
                        Text(stringResource(R.string.spotify_playlist_taste_use_desc))
                    },
                    modifier =
                        Modifier.clickable {
                            pendingSpotifyPlaylist = null
                            requestTasteOverwrite { runSpotifyPlaylistTaste(spotifyPl) }
                        },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.spotify_import_action)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.spotify_playlist_tracks,
                                spotifyPl.trackCount,
                            ),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            pendingSpotifyPlaylist = null
                            scope.launch {
                                isBusy = true
                                statusMessage = null
                                progress = SpotifyImportProgress(phase = "Starting")
                                val manager = SpotifyImportManager(database, context)
                                try {
                                    val result =
                                        manager.importPlaylist(
                                            clientId = clientId,
                                            playlist = spotifyPl,
                                            onProgress = { p ->
                                                scope.launch(Dispatchers.Main) {
                                                    progress = p
                                                }
                                            },
                                        )
                                    statusMessage =
                                        context.getString(
                                            R.string.spotify_import_result,
                                            result.matched,
                                            result.failed,
                                        )
                                } catch (e: Exception) {
                                    statusMessage = e.message
                                    reportException(e)
                                } finally {
                                    manager.close()
                                    isBusy = false
                                }
                            }
                        },
                )
            }
        }
    }

    AuraScreen {
        AuraHeader(
            title = stringResource(R.string.spotify_integration),
            onBack = navController::navigateUp,
            onBackLongClick = navController::backToMain,
        )

        AuraBanner(text = stringResource(R.string.spotify_oauth_premium_disclaimer))

        AuraSectionLabel(stringResource(R.string.spotify_section_account))
        AuraRow(
            title = stringResource(R.string.spotify_client_id),
            subtitle =
                if (clientId.isNotBlank()) {
                    clientId.take(8) + "…"
                } else {
                    stringResource(R.string.not_set)
                },
            showChevron = true,
            onClick = { showClientIdDialog = true },
        )
        AuraDivider()
        AuraRow(
            title = stringResource(R.string.spotify_redirect_uri),
            subtitle = redirectNote,
        )
        AuraDivider()
        AuraRow(
            title =
                if (isConnected) {
                    stringResource(R.string.spotify_connected_as, displayName.ifBlank { "Spotify" })
                } else {
                    stringResource(R.string.spotify_not_connected)
                },
            subtitle = stringResource(R.string.spotify_connect_description),
            enabled = !isBusy && (isConnected || clientId.isNotBlank()),
            trailingText = if (isConnected) disconnectLabel else connectLabel,
            onClick = {
                if (isConnected) {
                    showDisconnectConfirm = true
                } else {
                    val activity = context as? Activity ?: return@AuraRow
                    scope.launch {
                        isBusy = true
                        statusMessage = null
                        val auth = SpotifyAuth()
                        try {
                            val result =
                                withContext(Dispatchers.IO) {
                                    auth.authorize(activity, clientId)
                                }
                            SpotifyTokenStore.storeFull(
                                accessToken = result.accessToken,
                                refreshToken = result.refreshToken,
                                expiresInSec = result.expiresInSec,
                            )
                            val api = SpotifyApi()
                            try {
                                val me =
                                    withContext(Dispatchers.IO) {
                                        api.getMe(result.accessToken)
                                    }
                                displayName = me.displayName
                            } finally {
                                api.close()
                            }
                            isConnected = true
                        } catch (e: SpotifyAuthException.UserCancelled) {
                            statusMessage = e.message
                        } catch (e: Exception) {
                            statusMessage = e.message
                            reportException(e)
                        } finally {
                            auth.close()
                            isBusy = false
                        }
                    }
                }
            },
        )

        if (isConnected) {
            AuraSectionLabel(stringResource(R.string.spotify_import))
            AuraRow(
                title = stringResource(R.string.spotify_import_taste),
                subtitle = stringResource(R.string.spotify_import_taste_desc),
                enabled = !isBusy,
                onClick = {
                    requestTasteOverwrite {
                        scope.launch {
                            isBusy = true
                            statusMessage = null
                            progress = SpotifyImportProgress(phase = "Starting")
                            val manager = SpotifyImportManager(database, context)
                            try {
                                val result =
                                    manager.importTaste(
                                        clientId = clientId,
                                        enableGeminiNano = enableGeminiNano,
                                        onProgress = { p ->
                                            scope.launch(Dispatchers.Main) {
                                                progress = p
                                            }
                                        },
                                    )
                                result.tasteAnalysis?.let { analysis ->
                                    tasteSummary = analysis.summary
                                    tasteHints = analysis.searchHints.joinToString("\n")
                                    showTasteSuccess(
                                        analysis.summary,
                                        result.topTracks.size,
                                        analysis.usedAi,
                                    )
                                }
                                persistTasteProfile(result.topArtists, result.topTracks)
                                statusMessage =
                                    context.getString(
                                        R.string.spotify_import_result,
                                        result.matched,
                                        result.failed,
                                    )
                            } catch (e: Exception) {
                                showTasteError(e.message)
                                reportException(e)
                            } finally {
                                manager.close()
                                isBusy = false
                            }
                        }
                    }
                },
            )
            AuraDivider()


            AuraSectionLabel(stringResource(R.string.spotify_playlists))
            if (playlists.isEmpty()) {
                AuraRow(
                    title = stringResource(R.string.spotify_no_playlists),
                    subtitle = stringResource(R.string.spotify_nano_note),
                )
            } else {
                AuraRow(
                    title = stringResource(R.string.spotify_import_all_playlists),
                    subtitle = stringResource(R.string.spotify_import_all_playlists_desc),
                    enabled = !isBusy,
                    trailingText = stringResource(R.string.spotify_import_action),
                    onClick = { showImportAllConfirm = true },
                )
                AuraDivider()
                playlists.forEach { playlist ->
                    AuraRow(
                        title = playlist.name,
                        subtitle =
                            if (!playlist.ownerName.isNullOrBlank()) {
                                stringResource(
                                    R.string.spotify_playlist_tracks_owner,
                                    playlist.trackCount,
                                    playlist.ownerName,
                                )
                            } else {
                                stringResource(
                                    R.string.spotify_playlist_tracks,
                                    playlist.trackCount,
                                )
                            },
                        enabled = !isBusy,
                        trailingText = stringResource(R.string.spotify_playlist_taste_use),
                        showChevron = true,
                        onClick = { pendingSpotifyPlaylist = playlist },
                    )
                    AuraDivider()
                }
            }
        }

        if (isBusy) {
            Spacer(Modifier.height(16.dp))
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                    color = AuraSpotifyGreen,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.padding(8.dp),
                    color = AuraSpotifyGreen,
                )
            }
            Text(
                text =
                    buildString {
                        append(progress.phase)
                        if (progress.currentTitle.isNotBlank()) {
                            append(" — ")
                            append(progress.currentTitle)
                        }
                        if (progress.total > 0) {
                            append(" (")
                            append(progress.matched)
                            append(" matched / ")
                            append(progress.failed)
                            append(" missed)")
                        }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        statusMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.spotify_nano_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
