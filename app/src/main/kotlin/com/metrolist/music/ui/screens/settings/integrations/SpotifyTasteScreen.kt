/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.spotify.SpotifyApi
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.spotify.SpotifyImportProgress
import com.metrolist.music.spotify.SpotifyTokenStore
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private fun parseNewlineList(raw: String): List<String> =
    raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }

private fun parseTrackPrefs(raw: String): List<Pair<String, String>> =
    parseNewlineList(raw).map { line ->
        val sep = when {
            " — " in line -> " — "
            " - " in line -> " - "
            else -> null
        }
        if (sep != null) {
            val idx = line.indexOf(sep)
            line.substring(0, idx).trim() to line.substring(idx + sep.length).trim()
        } else {
            line to ""
        }
    }

/**
 * Shows what Nano DJ has learned about the listener's Spotify taste: a plain-language summary,
 * top artists/tracks, and the recommendation ideas Nano DJ derives from them. Lets the listener
 * refresh the analysis, generate a recommendations playlist, or jump straight into Nano DJ.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpotifyTasteScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    val clientId by rememberPreference(SpotifyClientIdKey, BuildConfig.SPOTIFY_CLIENT_ID)
    var tasteSummary by rememberPreference(SpotifyTasteSummaryKey, "")
    var tasteHints by rememberPreference(SpotifyTasteHintsKey, "")
    var topArtistsPref by rememberPreference(SpotifyTopArtistsKey, "")
    var topTracksPref by rememberPreference(SpotifyTopTracksKey, "")
    val enableGeminiNano by rememberPreference(EnableGeminiNanoKey, true)
    val (nanoDjSpeak, _) = rememberPreference(NanoDjSpeakKey, true)

    val isConnected = !SpotifyTokenStore.retrieve().isNullOrBlank()

    var artists by remember { mutableStateOf(emptyList<String>()) }
    var tracks by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    val hints = remember(tasteHints) { parseNewlineList(tasteHints) }

    var isLoadingLive by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(SpotifyImportProgress()) }

    // Sync lists when DataStore prefs finish loading (rememberPreference starts empty).
    LaunchedEffect(topArtistsPref, topTracksPref) {
        if (artists.isEmpty() && topArtistsPref.isNotBlank()) {
            artists = parseNewlineList(topArtistsPref)
        }
        if (tracks.isEmpty() && topTracksPref.isNotBlank()) {
            tracks = parseTrackPrefs(topTracksPref)
        }
    }

    fun persistProfile(
        newArtists: List<String>,
        newTracks: List<Pair<String, String>>,
    ) {
        if (newArtists.isNotEmpty()) topArtistsPref = newArtists.joinToString("\n")
        // Use " — " (em dash) so titles containing " - " round-trip cleanly
        if (newTracks.isNotEmpty()) {
            topTracksPref = newTracks.joinToString("\n") { "${it.first} — ${it.second}" }
        }
    }

    fun loadLiveTaste() {
        if (!isConnected || clientId.isBlank()) return
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoadingLive = true }
            val api = SpotifyApi()
            try {
                val manager = SpotifyImportManager(database, api)
                val token = manager.ensureValidToken(clientId)
                val liveArtists = api.getTopArtists(token, "medium_term", 20).map { it.name }
                val liveTracks = api.getTopTracks(token, "medium_term", 30).map { it.name to it.artistsJoined }
                withContext(Dispatchers.Main) {
                    if (liveArtists.isNotEmpty()) artists = liveArtists
                    if (liveTracks.isNotEmpty()) tracks = liveTracks
                    persistProfile(
                        if (liveArtists.isNotEmpty()) liveArtists else artists,
                        if (liveTracks.isNotEmpty()) liveTracks else tracks,
                    )
                }
            } catch (e: Exception) {
                Timber.tag("SpotifyTaste").w(e, "Failed to load live taste")
            } finally {
                api.close()
                withContext(Dispatchers.Main) { isLoadingLive = false }
            }
        }
    }

    LaunchedEffect(isConnected, clientId) { loadLiveTaste() }

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

        if (isLoadingLive) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text(
                    text = stringResource(R.string.spotify_taste_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (tasteSummary.isBlank() && artists.isEmpty() && tracks.isEmpty() && hints.isEmpty() && !isLoadingLive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = stringResource(R.string.spotify_taste_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (tasteSummary.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.spotify_taste_summary),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = tasteSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        TasteChipSection(
            title = stringResource(R.string.spotify_taste_top_artists),
            icon = painterResource(R.drawable.person),
            entries = artists,
            emptyLabel = stringResource(R.string.spotify_taste_no_artists),
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (tracks.isNotEmpty()) {
            Text(
                text = stringResource(R.string.spotify_taste_top_tracks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    tracks.forEachIndexed { index, (name, artist) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(
                                text = if (artist.isNotBlank()) "$name — $artist" else name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.spotify_taste_top_tracks),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
            )
            Text(
                text = stringResource(R.string.spotify_taste_no_tracks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.spotify_taste_hints),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
        )
        if (hints.isNotEmpty()) {
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                hints.forEach { hint ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(hint, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.spotify_taste_no_hints),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.refresh),
                    title = { Text(stringResource(R.string.spotify_taste_refresh)) },
                    description = { Text(stringResource(R.string.spotify_taste_refresh_desc)) },
                    enabled = !isBusy && isConnected,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            statusMessage = null
                            progress = SpotifyImportProgress(phase = "Starting")
                            val manager = SpotifyImportManager(database)
                            try {
                                val profile = manager.refreshTasteProfile(
                                    clientId = clientId,
                                    enableGeminiNano = enableGeminiNano,
                                    onProgress = { p ->
                                        scope.launch(Dispatchers.Main) { progress = p }
                                    },
                                )
                                artists = profile.topArtists
                                tracks = profile.topTracks
                                tasteSummary = profile.analysis.summary
                                tasteHints = profile.analysis.searchHints.joinToString("\n")
                                persistProfile(profile.topArtists, profile.topTracks)
                                statusMessage = context.getString(R.string.spotify_taste_refresh_result)
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
                                val result = manager.generateRecommendations(
                                    clientId = clientId,
                                    enableGeminiNano = enableGeminiNano,
                                    cachedSummary = tasteSummary,
                                    cachedHints = hints,
                                    onProgress = { p ->
                                        scope.launch(Dispatchers.Main) { progress = p }
                                    },
                                )
                                result.tasteAnalysis?.let { analysis ->
                                    tasteSummary = analysis.summary
                                    tasteHints = analysis.searchHints.joinToString("\n")
                                }
                                if (result.topArtists.isNotEmpty()) artists = result.topArtists
                                if (result.topTracks.isNotEmpty()) tracks = result.topTracks
                                persistProfile(result.topArtists, result.topTracks)
                                statusMessage = context.getString(
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
                            val result = NanoDjLauncher.start(
                                context = context,
                                playerConnection = connection,
                                speak = nanoDjSpeak,
                            )
                            statusMessage = result.fold(
                                onSuccess = { context.getString(R.string.nano_dj_started) },
                                onFailure = { it.message },
                            )
                            isBusy = false
                        }
                    },
                ),
            ),
        )

        if (isBusy) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = buildString {
                    append(progress.phase)
                    if (progress.currentTitle.isNotBlank()) {
                        append(" — ")
                        append(progress.currentTitle)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
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

        if (!isConnected) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.spotify_taste_connect_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.spotify_taste_title)) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasteChipSection(
    title: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    entries: List<String>,
    emptyLabel: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
    )
    if (entries.isNotEmpty()) {
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            entries.forEach { entry ->
                SuggestionChip(
                    onClick = {},
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(entry) },
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                )
            }
        }
    } else {
        Text(
            text = emptyLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
