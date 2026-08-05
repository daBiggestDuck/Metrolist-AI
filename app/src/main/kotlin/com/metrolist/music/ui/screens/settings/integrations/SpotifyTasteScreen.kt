/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.NanoDjLauncher
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.ListeningTasteActiveLaneKey
import com.metrolist.music.constants.ListeningTasteArtistsKey
import com.metrolist.music.constants.ListeningTasteCategoriesKey
import com.metrolist.music.constants.ListeningTasteExcludedSongIdsKey
import com.metrolist.music.constants.ListeningTasteSummaryKey
import com.metrolist.music.constants.ListeningTasteTracksKey
import com.metrolist.music.constants.NanoDjSpeakKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.SpotifyClientIdKey
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.spotify.SpotifyApi
import com.metrolist.music.spotify.SpotifyFileTasteImporter
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.spotify.SpotifyImportProgress
import com.metrolist.music.spotify.SpotifyTokenStore
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.component.aura.AuraArtistAvatar
import com.metrolist.music.ui.component.aura.AuraDivider
import com.metrolist.music.ui.component.aura.AuraHeader
import com.metrolist.music.ui.component.aura.AuraHintPill
import com.metrolist.music.ui.component.aura.AuraPrimaryPill
import com.metrolist.music.ui.component.aura.AuraBanner
import com.metrolist.music.ui.component.aura.AuraRow
import com.metrolist.music.ui.component.aura.AuraScreen
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSectionLabel
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.AuraTrackRow
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
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

private val ArtistAvatarPalette =
    listOf(
        Color(0xFF1DB954),
        Color(0xFF2D46B9),
        Color(0xFFE91429),
        Color(0xFFAF2896),
        Color(0xFF8C67AC),
        Color(0xFF148A08),
        Color(0xFF509BF5),
        Color(0xFFE13300),
    )

/**
 * Shows what Nano DJ has learned about the listener's Spotify taste: a plain-language summary,
 * top artists/tracks, and the recommendation ideas Nano DJ derives from them. Lets the listener
 * refresh the analysis, generate a recommendations playlist, or jump straight into Nano DJ.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val listeningSummary by rememberPreference(ListeningTasteSummaryKey, "")
    val listeningArtistsPref by rememberPreference(ListeningTasteArtistsKey, "")
    val listeningTracksPref by rememberPreference(ListeningTasteTracksKey, "")
    val listeningCategoriesPref by rememberPreference(ListeningTasteCategoriesKey, "")
    val listeningLaneId by rememberPreference(ListeningTasteActiveLaneKey, "")
    val excludedTasteIds by rememberPreference(ListeningTasteExcludedSongIdsKey, emptySet<String>())
    val enableGeminiNano by rememberPreference(EnableGeminiNanoKey, true)
    val (nanoDjSpeak, _) = rememberPreference(NanoDjSpeakKey, true)

    val isConnected = !SpotifyTokenStore.retrieve().isNullOrBlank()

    val listeningArtists =
        remember(listeningArtistsPref) {
            ListeningTasteTracker.decodeWeights(listeningArtistsPref)
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
        }
    val listeningTracks =
        remember(listeningTracksPref) {
            ListeningTasteTracker.decodeWeights(listeningTracksPref)
                .entries
                .sortedByDescending { it.value }
                .map { (line, _) ->
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
        }
    val listeningCategories =
        remember(listeningCategoriesPref) {
            ListeningTasteTracker.decodeWeights(listeningCategoriesPref)
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
        }
    val activeLane =
        remember(listeningLaneId) {
            ListeningTasteTracker.DjLane.fromId(listeningLaneId).displayName
        }

    var artists by remember { mutableStateOf(emptyList<String>()) }
    var tracks by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    val hints = remember(tasteHints) { parseNewlineList(tasteHints) }

    var isLoadingLive by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(SpotifyImportProgress()) }
    var pendingFileTracks by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var showFilePlaylistNameDialog by remember { mutableStateOf(false) }
    var pendingFilePlaylistName by remember { mutableStateOf(SpotifyImportManager.TASTE_PLAYLIST_NAME) }
    var showLocalPlaylistPicker by remember { mutableStateOf(false) }

    val localPlaylists by remember {
        database.playlists(PlaylistSortType.NAME, false).map { list ->
            list.filter { it.songCount > 0 }
        }
    }.collectAsState(initial = emptyList())

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

    fun applyTasteProfile(
        newArtists: List<String>,
        newTracks: List<Pair<String, String>>,
        summary: String,
        searchHints: List<String>,
    ) {
        if (summary.isNotBlank()) tasteSummary = summary
        if (searchHints.isNotEmpty()) tasteHints = searchHints.joinToString("\n")
        if (newArtists.isNotEmpty()) artists = newArtists
        if (newTracks.isNotEmpty()) tracks = newTracks
        persistProfile(newArtists, newTracks)
    }

    fun runFileTasteImport(
        fileTracks: List<Pair<String, String>>,
        playlistName: String,
    ) {
        scope.launch {
            isBusy = true
            statusMessage = null
            progress = SpotifyImportProgress(phase = "Importing from file")
            val manager = SpotifyImportManager(database, context)
            try {
                val result =
                    manager.importTasteFromTracks(
                        tracks = fileTracks,
                        playlistName = playlistName,
                        enableNano = enableGeminiNano,
                        onProgress = { p ->
                            scope.launch(Dispatchers.Main) { progress = p }
                        },
                    )
                result.tasteAnalysis?.let { analysis ->
                    applyTasteProfile(
                        result.topArtists,
                        result.topTracks,
                        analysis.summary,
                        analysis.searchHints,
                    )
                } ?: run {
                    if (result.topArtists.isNotEmpty()) artists = result.topArtists
                    if (result.topTracks.isNotEmpty()) tracks = result.topTracks
                    persistProfile(result.topArtists, result.topTracks)
                }
                statusMessage =
                    context.getString(
                        R.string.spotify_file_import_result,
                        result.matched,
                        result.failed,
                    )
            } catch (e: Exception) {
                statusMessage = e.message
                reportException(e)
            } finally {
                manager.close()
                isBusy = false
                pendingFileTracks = null
            }
        }
    }

    fun runLocalPlaylistTaste(playlist: Playlist) {
        scope.launch {
            isBusy = true
            statusMessage = null
            progress = SpotifyImportProgress(phase = "Reading playlist")
            val manager = SpotifyImportManager(database, context)
            try {
                val profile =
                    manager.buildTasteFromLocalPlaylist(
                        playlistId = playlist.id,
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
                statusMessage =
                    context.getString(
                        R.string.spotify_playlist_taste_result,
                        playlist.title,
                        profile.topTracks.size,
                    )
            } catch (e: Exception) {
                statusMessage = e.message
                reportException(e)
            } finally {
                manager.close()
                isBusy = false
            }
        }
    }

    val fileImportMimeTypes =
        remember {
            arrayOf(
                "text/*",
                "text/csv",
                "text/plain",
                "text/comma-separated-values",
                "application/csv",
                "application/json",
                "*/*",
            )
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val parsed =
                        withContext(Dispatchers.IO) {
                            SpotifyFileTasteImporter.parse(context, uri)
                        }
                    if (parsed.tracks.isEmpty()) {
                        statusMessage = context.getString(R.string.spotify_file_import_empty)
                        return@launch
                    }
                    pendingFileTracks = parsed.tracks
                    pendingFilePlaylistName =
                        parsed.playlistName?.takeIf { it.isNotBlank() }
                            ?: SpotifyImportManager.TASTE_PLAYLIST_NAME
                    showFilePlaylistNameDialog = true
                } catch (e: Exception) {
                    statusMessage = e.message
                    reportException(e)
                }
            }
        }

    fun loadLiveTaste() {
        if (!isConnected || clientId.isBlank()) return
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoadingLive = true }
            val api = SpotifyApi()
            try {
                val manager = SpotifyImportManager(database, context, api)
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

    if (showFilePlaylistNameDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.spotify_file_import_playlist_name_title)) },
            icon = { Icon(painterResource(R.drawable.playlist_add), null) },
            initialTextFieldValue = TextFieldValue(text = pendingFilePlaylistName),
            onDone = { name ->
                showFilePlaylistNameDialog = false
                val fileTracks = pendingFileTracks
                if (fileTracks != null) {
                    runFileTasteImport(
                        fileTracks = fileTracks,
                        playlistName = name.trim().ifBlank { SpotifyImportManager.TASTE_PLAYLIST_NAME },
                    )
                }
            },
            onDismiss = {
                showFilePlaylistNameDialog = false
                pendingFileTracks = null
            },
        )
    }

    if (showLocalPlaylistPicker) {
        ListDialog(onDismiss = { showLocalPlaylistPicker = false }) {
            item {
                Text(
                    text = stringResource(R.string.spotify_playlist_taste_pick_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            if (localPlaylists.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.spotify_playlist_taste_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            } else {
                items(localPlaylists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.title) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.spotify_playlist_tracks,
                                    playlist.songCount,
                                ),
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                showLocalPlaylistPicker = false
                                runLocalPlaylistTaste(playlist)
                            },
                    )
                }
            }
        }
    }

    AuraScreen {
        AuraHeader(
            title = stringResource(R.string.spotify_taste_title),
            onBack = navController::navigateUp,
            onBackLongClick = navController::backToMain,
        )

        AuraBanner(text = stringResource(R.string.spotify_oauth_premium_disclaimer))

        AuraSectionLabel(stringResource(R.string.spotify_easy_import_section))
        Text(
            text = stringResource(R.string.spotify_export_guide_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = stringResource(R.string.spotify_export_guide_steps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        AuraPrimaryPill(
            text = stringResource(R.string.spotify_import_file_cta),
            enabled = !isBusy,
            onClick = { filePickerLauncher.launch(fileImportMimeTypes) },
        )
        Spacer(Modifier.height(8.dp))
        AuraRow(
            title = stringResource(R.string.spotify_playlist_taste_pick),
            subtitle = stringResource(R.string.spotify_playlist_taste_pick_desc),
            enabled = !isBusy,
            showChevron = true,
            onClick = { showLocalPlaylistPicker = true },
        )
        AuraDivider()
        Text(
            text = stringResource(R.string.spotify_file_import_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        AuraSectionLabel(stringResource(R.string.listening_taste_live_section))
        Text(
            text = stringResource(R.string.listening_taste_live_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (listeningSummary.isNotBlank() || listeningArtists.isNotEmpty() || listeningTracks.isNotEmpty()) {
            Text(
                text = stringResource(R.string.listening_taste_active_lane, activeLane),
                style = MaterialTheme.typography.labelLarge,
                color = AuraSpotifyGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (listeningSummary.isNotBlank()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1A3A28), Color(0xFF181818)),
                                ),
                            )
                            .padding(20.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.spotify_taste_summary),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = listeningSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (listeningArtists.isNotEmpty()) {
                AuraSectionLabel(stringResource(R.string.spotify_taste_top_artists))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(listeningArtists.take(12)) { index, name ->
                        AuraArtistAvatar(
                            name = name,
                            color = ArtistAvatarPalette[index % ArtistAvatarPalette.size],
                        )
                    }
                }
            }
            if (listeningTracks.isNotEmpty()) {
                AuraSectionLabel(stringResource(R.string.spotify_taste_top_tracks))
                listeningTracks.take(8).forEachIndexed { index, (name, artist) ->
                    AuraTrackRow(
                        index = index + 1,
                        title = name,
                        artist = artist.takeIf { it.isNotBlank() },
                    )
                    if (index < minOf(7, listeningTracks.lastIndex)) AuraDivider()
                }
            }
            AuraSectionLabel(stringResource(R.string.listening_taste_categories))
            if (listeningCategories.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listeningCategories.forEach { cat ->
                        AuraHintPill(text = cat)
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.listening_taste_no_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (excludedTasteIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AuraSectionLabel(stringResource(R.string.listening_taste_excluded_section))
                Text(
                    text = stringResource(R.string.listening_taste_excluded_count, excludedTasteIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AuraSecondaryAction(
                    text = stringResource(R.string.listening_taste_clear_exclusions),
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            context.safeDataStoreEdit {
                                it[ListeningTasteExcludedSongIdsKey] = emptySet()
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (isLoadingLive) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                    color = AuraSpotifyGreen,
                )
                Text(
                    text = stringResource(R.string.spotify_taste_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (
            tasteSummary.isBlank() &&
            artists.isEmpty() &&
            tracks.isEmpty() &&
            hints.isEmpty() &&
            listeningSummary.isBlank() &&
            listeningArtists.isEmpty() &&
            listeningTracks.isEmpty() &&
            !isLoadingLive
        ) {
            Text(
                text = stringResource(R.string.spotify_taste_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        if (tasteSummary.isNotBlank()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A3A28), Color(0xFF181818)),
                            ),
                        )
                        .padding(20.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.spotify_taste_summary),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = tasteSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        AuraSectionLabel(stringResource(R.string.spotify_taste_top_artists))
        if (artists.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(artists) { index, name ->
                    AuraArtistAvatar(
                        name = name,
                        color = ArtistAvatarPalette[index % ArtistAvatarPalette.size],
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.spotify_taste_no_artists),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AuraSectionLabel(stringResource(R.string.spotify_taste_top_tracks))
        if (tracks.isNotEmpty()) {
            tracks.forEachIndexed { index, (name, artist) ->
                AuraTrackRow(
                    index = index + 1,
                    title = name,
                    artist = artist.takeIf { it.isNotBlank() },
                )
                if (index < tracks.lastIndex) AuraDivider()
            }
        } else {
            Text(
                text = stringResource(R.string.spotify_taste_no_tracks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AuraSectionLabel(stringResource(R.string.spotify_taste_hints))
        if (hints.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hints.forEach { hint ->
                    AuraHintPill(text = hint)
                }
            }
        } else {
            Text(
                text = stringResource(R.string.spotify_taste_no_hints),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))

        AuraPrimaryPill(
            text = stringResource(R.string.nano_dj_start),
            enabled = !isBusy && playerConnection != null,
            onClick = {
                val connection = playerConnection ?: return@AuraPrimaryPill
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
                            onSuccess = { context.getString(R.string.nano_dj_started) },
                            onFailure = { it.message },
                        )
                    isBusy = false
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        AuraSecondaryAction(
            text = stringResource(R.string.spotify_taste_refresh),
            enabled = !isBusy && isConnected,
            onClick = {
                scope.launch {
                    isBusy = true
                    statusMessage = null
                    progress = SpotifyImportProgress(phase = "Starting")
                    val manager = SpotifyImportManager(database, context)
                    try {
                        val profile =
                            manager.refreshTasteProfile(
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
        )

        AuraSecondaryAction(
            text = stringResource(R.string.nano_recommendations_generate),
            enabled = !isBusy,
            onClick = {
                scope.launch {
                    isBusy = true
                    statusMessage = null
                    progress = SpotifyImportProgress(phase = "Generating recommendations")
                    val manager = SpotifyImportManager(database, context)
                    try {
                        val result =
                            manager.generateRecommendations(
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
        )

        if (isBusy) {
            Spacer(Modifier.height(16.dp))
            Text(
                text =
                    buildString {
                        append(progress.phase)
                        if (progress.currentTitle.isNotBlank()) {
                            append(" — ")
                            append(progress.currentTitle)
                        }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        if (!isConnected) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.spotify_taste_connect_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
