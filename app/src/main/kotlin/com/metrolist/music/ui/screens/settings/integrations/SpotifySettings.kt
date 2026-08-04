/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ai.NanoDjLauncher
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.NanoDjSpeakKey
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
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.compose.material3.Switch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifySettings(
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
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
    val enableGeminiNano by rememberPreference(EnableGeminiNanoKey, true)
    val (nanoDjSpeak, onNanoDjSpeakChange) = rememberPreference(NanoDjSpeakKey, true)

    var isConnected by remember {
        mutableStateOf(!SpotifyTokenStore.retrieve().isNullOrBlank())
    }
    var playlists by remember { mutableStateOf<List<SpotifyPlaylistSummary>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(SpotifyImportProgress()) }
    var showClientIdDialog by rememberSaveable { mutableStateOf(false) }

    val redirectNote = stringResource(R.string.spotify_redirect_uri_note)
    val connectLabel = stringResource(R.string.spotify_connect)
    val disconnectLabel = stringResource(R.string.spotify_disconnect)

    fun persistTasteProfile(
        artists: List<String>,
        tracks: List<Pair<String, String>>,
    ) {
        if (artists.isNotEmpty()) topArtistsPref = artists.joinToString("\n")
        if (tracks.isNotEmpty()) topTracksPref = tracks.joinToString("\n") { "${it.first} - ${it.second}" }
    }

    fun refreshPlaylists() {
        scope.launch {
            val id = clientId
            if (id.isBlank() || !isConnected) return@launch
            try {
                isBusy = true
                val api = SpotifyApi()
                try {
                    val manager = SpotifyImportManager(database, api)
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

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.spotify_integration),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.key),
                        title = { Text(stringResource(R.string.spotify_client_id)) },
                        description = {
                            Text(
                                if (clientId.isNotBlank()) {
                                    clientId.take(8) + "…"
                                } else {
                                    stringResource(R.string.not_set)
                                },
                            )
                        },
                        onClick = { showClientIdDialog = true },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.link),
                        title = { Text(stringResource(R.string.spotify_redirect_uri)) },
                        description = { Text(redirectNote) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.music_note),
                        title = {
                            Text(
                                if (isConnected) {
                                    stringResource(R.string.spotify_connected_as, displayName.ifBlank { "Spotify" })
                                } else {
                                    stringResource(R.string.spotify_not_connected)
                                },
                            )
                        },
                        description = {
                            Text(stringResource(R.string.spotify_connect_description))
                        },
                        trailingContent = {
                            OutlinedButton(
                                enabled = !isBusy && (isConnected || clientId.isNotBlank()),
                                onClick = {
                                    if (isConnected) {
                                        SpotifyTokenStore.clear()
                                        displayName = ""
                                        tasteSummary = ""
                                        tasteHints = ""
                                        topArtistsPref = ""
                                        topTracksPref = ""
                                        playlists = emptyList()
                                        isConnected = false
                                        statusMessage = null
                                    } else {
                                        val activity = context as? Activity ?: return@OutlinedButton
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
                            ) {
                                Text(if (isConnected) disconnectLabel else connectLabel)
                            }
                        },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        if (isConnected || tasteSummary.isNotBlank() || tasteHints.isNotBlank()) {
            Material3SettingsGroup(
                items =
                    listOf(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.trending_up),
                            title = { Text(stringResource(R.string.spotify_view_taste)) },
                            description = { Text(stringResource(R.string.spotify_view_taste_desc)) },
                            isHighlighted = true,
                            onClick = { navController.navigate("settings/integrations/spotify/taste") },
                        ),
                    ),
            )

            Spacer(modifier = Modifier.height(27.dp))
        }

        Material3SettingsGroup(
            title = stringResource(R.string.nano_dj_section),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.radio),
                        title = { Text(stringResource(R.string.nano_dj_start)) },
                        description = { Text(stringResource(R.string.nano_dj_start_desc)) },
                        enabled = !isBusy && playerConnection != null,
                        onClick = {
                            val connection = playerConnection ?: return@Material3SettingsItem
                            scope.launch {
                                isBusy = true
                                statusMessage = null
                                progress = SpotifyImportProgress(phase = "Starting Nano DJ")
                                val result =
                                    NanoDjLauncher.start(
                                        context = context,
                                        playerConnection = connection,
                                        speak = nanoDjSpeak,
                                    )
                                statusMessage =
                                    result.fold(
                                        onSuccess = {
                                            context.getString(R.string.nano_dj_started)
                                        },
                                        onFailure = { it.message },
                                    )
                                isBusy = false
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.discover_tune),
                        title = { Text(stringResource(R.string.nano_recommendations_generate)) },
                        description = { Text(stringResource(R.string.nano_recommendations_generate_desc)) },
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                statusMessage = null
                                progress = SpotifyImportProgress(phase = "Generating recommendations")
                                val manager = SpotifyImportManager(database)
                                try {
                                    val result =
                                        manager.generateRecommendations(
                                            clientId = clientId,
                                            enableGeminiNano = enableGeminiNano,
                                            cachedSummary = tasteSummary,
                                            cachedHints =
                                                tasteHints
                                                    .split('\n')
                                                    .map { it.trim() }
                                                    .filter { it.isNotBlank() },
                                            onProgress = { p ->
                                                scope.launch(Dispatchers.Main) {
                                                    progress = p
                                                }
                                            },
                                        )
                                    result.tasteAnalysis?.let { analysis ->
                                        tasteSummary = analysis.summary
                                        tasteHints = analysis.searchHints.joinToString("\n")
                                    }
                                    persistTasteProfile(result.topArtists, result.topTracks)
                                    statusMessage =
                                        context.getString(
                                            R.string.nano_recommendations_result,
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
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.music_note),
                        title = { Text(stringResource(R.string.nano_dj_speak)) },
                        description = { Text(stringResource(R.string.nano_dj_speak_desc)) },
                        trailingContent = {
                            Switch(
                                checked = nanoDjSpeak,
                                onCheckedChange = onNanoDjSpeakChange,
                            )
                        },
                        onClick = { onNanoDjSpeakChange(!nanoDjSpeak) },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        if (isConnected) {
            Material3SettingsGroup(
                title = stringResource(R.string.spotify_import),
                items =
                    buildList {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.playlist_add),
                                title = { Text(stringResource(R.string.spotify_import_taste)) },
                                description = {
                                    Text(stringResource(R.string.spotify_import_taste_desc))
                                },
                                enabled = !isBusy,
                                onClick = {
                                    scope.launch {
                                        isBusy = true
                                        statusMessage = null
                                        progress = SpotifyImportProgress(phase = "Starting")
                                        val manager = SpotifyImportManager(database)
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
                                            }
                                            persistTasteProfile(result.topArtists, result.topTracks)
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
                            ),
                        )
                        if (tasteSummary.isNotBlank()) {
                            add(
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.info),
                                    title = { Text(stringResource(R.string.spotify_taste_summary)) },
                                    description = { Text(tasteSummary) },
                                ),
                            )
                        }
                    },
            )

            Spacer(modifier = Modifier.height(27.dp))

            Material3SettingsGroup(
                title = stringResource(R.string.spotify_playlists),
                items =
                    if (playlists.isEmpty()) {
                        listOf(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.queue_music),
                                title = { Text(stringResource(R.string.spotify_no_playlists)) },
                                description = {
                                    Text(stringResource(R.string.spotify_nano_note))
                                },
                            ),
                        )
                    } else {
                        buildList {
                            add(
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.library_add),
                                    title = { Text(stringResource(R.string.spotify_import_all_playlists)) },
                                    description = { Text(stringResource(R.string.spotify_import_all_playlists_desc)) },
                                    isHighlighted = true,
                                    enabled = !isBusy,
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            statusMessage = null
                                            val snapshot = playlists
                                            val totalPlaylists = snapshot.size
                                            var totalMatched = 0
                                            var totalFailed = 0
                                            val manager = SpotifyImportManager(database)
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
                                    },
                                ),
                            )
                            addAll(
                                playlists.map { playlist ->
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.queue_music),
                                title = { Text(playlist.name) },
                                description = {
                                    Text(
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
                                    )
                                },
                                enabled = !isBusy,
                                trailingContent = {
                                    OutlinedButton(
                                        enabled = !isBusy,
                                        onClick = {
                                            scope.launch {
                                                isBusy = true
                                                statusMessage = null
                                                progress = SpotifyImportProgress(phase = "Starting")
                                                val manager = SpotifyImportManager(database)
                                                try {
                                                    val result =
                                                        manager.importPlaylist(
                                                            clientId = clientId,
                                                            playlist = playlist,
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
                                    ) {
                                        Text(stringResource(R.string.spotify_import_action))
                                    }
                                },
                            )
                                },
                            )
                        }
                    },
            )
        }

        if (isBusy) {
            Spacer(modifier = Modifier.height(16.dp))
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
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
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.spotify_nano_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.spotify_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
