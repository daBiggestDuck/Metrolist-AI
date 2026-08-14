/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.ai.NanoDjLauncher
import com.metrolist.music.ai.TasteSummary
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.ListeningTasteActiveLaneKey
import com.metrolist.music.constants.ListeningTasteArtistsKey
import com.metrolist.music.constants.ListeningTasteCategoriesKey
import com.metrolist.music.constants.ListeningTasteExcludedSongIdsKey
import com.metrolist.music.constants.ListeningTasteSummaryKey
import com.metrolist.music.constants.ListeningTasteTracksKey
import com.metrolist.music.constants.NanoDjSpeakKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.SpotifyTasteHintsKey
import com.metrolist.music.constants.SpotifyTasteSummaryKey
import com.metrolist.music.constants.SpotifyTopArtistsKey
import com.metrolist.music.constants.SpotifyTopTracksKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.spotify.SpotifyFileTasteImporter
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.spotify.SpotifyImportProgress
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.aura.AuraArtistAvatar
import com.metrolist.music.ui.component.aura.AuraDivider
import com.metrolist.music.ui.component.aura.AuraHeader
import com.metrolist.music.ui.component.aura.AuraHintPill
import com.metrolist.music.ui.component.aura.AuraPrimaryPill
import com.metrolist.music.ui.component.aura.AuraRow
import com.metrolist.music.ui.component.aura.AuraScreen
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.AuraTrackRow
import com.metrolist.music.ui.component.aura.AuraSectionLabel
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun parseNewlineList(raw: String): List<String> =
    raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }

private fun parseTrackPrefs(raw: String): List<Pair<String, String>> =
    parseNewlineList(raw).map { line ->
        val sep =
            when {
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
 * Dedicated taste detail: summary, top artists/tracks, and import actions.
 * Recommendations generation lives on the Spotify settings screen — not duplicated here.
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

    var tasteSummary by rememberPreference(SpotifyTasteSummaryKey, "")
    var tasteHints by rememberPreference(SpotifyTasteHintsKey, "")
    var topArtistsPref by rememberPreference(SpotifyTopArtistsKey, "")
    var topTracksPref by rememberPreference(SpotifyTopTracksKey, "")
    var listeningSummary by rememberPreference(ListeningTasteSummaryKey, "")
    val listeningArtistsPref by rememberPreference(ListeningTasteArtistsKey, "")
    val listeningTracksPref by rememberPreference(ListeningTasteTracksKey, "")
    val listeningCategoriesPref by rememberPreference(ListeningTasteCategoriesKey, "")
    val listeningLaneId by rememberPreference(ListeningTasteActiveLaneKey, "")
    val excludedTasteIds by rememberPreference(ListeningTasteExcludedSongIdsKey, emptySet<String>())
    val enableGeminiNano by rememberPreference(EnableGeminiNanoKey, true)
    val (nanoDjSpeak, _) = rememberPreference(NanoDjSpeakKey, true)

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
                    val sep =
                        when {
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

    // Always mirror DataStore lists (CSV import writes prefs from another screen).
    val artists = remember(topArtistsPref, listeningArtists) {
        parseNewlineList(topArtistsPref).ifEmpty { listeningArtists }
    }
    val tracks = remember(topTracksPref, listeningTracks) {
        parseTrackPrefs(topTracksPref).ifEmpty { listeningTracks }
    }
    val hints = remember(tasteHints) {
        parseNewlineList(tasteHints).filter { TasteSummary.isUsable(it) }
    }

    val displaySummary =
        remember(listeningSummary, tasteSummary, artists, tracks) {
            // Prefer imported Spotify/CSV AI summary over live listening heuristic.
            TasteSummary.coalesce(tasteSummary, listeningSummary)
                ?: TasteSummary.fromArtistsAndTracks(artists, tracks).takeIf {
                    artists.isNotEmpty() || tracks.isNotEmpty()
                }
        }
    val hasTaste =
        displaySummary != null || artists.isNotEmpty() || tracks.isNotEmpty() || listeningArtists.isNotEmpty()

    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(SpotifyImportProgress()) }
    var showLocalPlaylistPicker by remember { mutableStateOf(false) }
    var showTasteOverwriteConfirm by remember { mutableStateOf(false) }
    var pendingTasteOverwrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showClearExclusionsConfirm by remember { mutableStateOf(false) }
    var showResetListeningTasteConfirm by remember { mutableStateOf(false) }
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
        Toast
            .makeText(
                context,
                context.getString(R.string.taste_updated_snackbar, trackCount),
                Toast.LENGTH_SHORT,
            ).show()
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

    val localPlaylists by remember {
        database.playlists(PlaylistSortType.NAME, false).map { list ->
            list.filter { it.songCount > 0 }
        }
    }.collectAsState(initial = emptyList())

    fun applyTasteProfile(
        newArtists: List<String>,
        newTracks: List<Pair<String, String>>,
        summary: String,
        searchHints: List<String>,
    ) {
        TasteSummary.sanitizeOrNull(summary)?.let {
            tasteSummary = it
            listeningSummary = it
        }
        if (searchHints.isNotEmpty()) {
            tasteHints =
                searchHints
                    .filter { TasteSummary.isUsable(it) }
                    .joinToString("\n")
        }
        if (newArtists.isNotEmpty()) topArtistsPref = newArtists.joinToString("\n")
        if (newTracks.isNotEmpty()) {
            topTracksPref = newTracks.joinToString("\n") { "${it.first} — ${it.second}" }
        }
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
                val analysis = result.tasteAnalysis
                if (analysis == null || !TasteSummary.isUsable(analysis.summary)) {
                    showTasteError(context.getString(R.string.spotify_taste_import_failed_summary))
                } else {
                    applyTasteProfile(
                        result.topArtists,
                        result.topTracks,
                        analysis.summary,
                        analysis.searchHints,
                    )
                    statusMessage =
                        if (result.matched > 0) {
                            context.getString(
                                R.string.spotify_file_import_result,
                                result.matched,
                                result.failed,
                            )
                        } else {
                            context.getString(
                                R.string.csv_import_taste_success,
                                result.topTracks.size,
                                result.topArtists.size,
                            )
                        }
                    showTasteSuccess(
                        analysis.summary,
                        result.topTracks.size.coerceAtLeast(fileTracks.size),
                        analysis.usedAi,
                    )
                }
            } catch (e: Exception) {
                showTasteError(e.message)
                reportException(e)
            } finally {
                manager.close()
                isBusy = false
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
                if (!TasteSummary.isUsable(profile.analysis.summary)) {
                    showTasteError(context.getString(R.string.spotify_taste_import_failed_summary))
                } else {
                    applyTasteProfile(
                        profile.topArtists,
                        profile.topTracks,
                        profile.analysis.summary,
                        profile.analysis.searchHints,
                    )
                    statusMessage =
                        context.getString(
                            R.string.taste_updated_message,
                            profile.topTracks.size,
                            profile.analysis.summary.take(160),
                        )
                    showTasteSuccess(
                        profile.analysis.summary,
                        profile.topTracks.size,
                        profile.analysis.usedAi,
                    )
                }
            } catch (e: Exception) {
                showTasteError(e.message)
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
                        showTasteError(statusMessage)
                        return@launch
                    }
                    val playlistName =
                        parsed.playlistName?.takeIf { it.isNotBlank() }
                            ?: SpotifyImportManager.TASTE_PLAYLIST_NAME
                    statusMessage =
                        context.getString(
                            R.string.spotify_file_import_started,
                            parsed.tracks.size,
                        )
                    Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                    // The file itself is the explicit overwrite action. Do not add a second
                    // playlist-name and confirmation flow before the taste update can start.
                    runFileTasteImport(parsed.tracks, playlistName)
                } catch (e: Exception) {
                    showTasteError(e.message)
                    reportException(e)
                }
            }
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
                    Text(stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(onClick = {
                    showTasteOverwriteConfirm = false
                    pendingTasteOverwrite?.invoke()
                    pendingTasteOverwrite = null
                }) {
                    Text(stringResource(R.string.taste_overwrite_confirm))
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

    if (showClearExclusionsConfirm) {
        DefaultDialog(
            onDismiss = { showClearExclusionsConfirm = false },
            title = { Text(stringResource(R.string.listening_taste_clear_exclusions_confirm_title)) },
            content = {
                Text(
                    text = stringResource(R.string.listening_taste_clear_exclusions_confirm_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showClearExclusionsConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(onClick = {
                    showClearExclusionsConfirm = false
                    scope.launch {
                        context.safeDataStoreEdit {
                            it[ListeningTasteExcludedSongIdsKey] = emptySet()
                        }
                    }
                }) {
                    Text(stringResource(R.string.listening_taste_clear_exclusions))
                }
            },
        )
    }

    if (showResetListeningTasteConfirm) {
        DefaultDialog(
            onDismiss = { showResetListeningTasteConfirm = false },
            title = { Text(stringResource(R.string.listening_taste_reset_confirm_title)) },
            content = {
                Text(
                    text = stringResource(R.string.listening_taste_reset_confirm_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showResetListeningTasteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(onClick = {
                    showResetListeningTasteConfirm = false
                    scope.launch {
                        ListeningTasteTracker.reset(context)
                    }
                }) {
                    Text(stringResource(R.string.listening_taste_reset))
                }
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
                                requestTasteOverwrite { runLocalPlaylistTaste(playlist) }
                            },
                    )
                }
            }
        }
    }

    AuraScreen {
        AuraHeader(
            title = stringResource(R.string.spotify_taste_title),
            subtitle = stringResource(R.string.spotify_taste_screen_subtitle),
            onBack = navController::navigateUp,
            onBackLongClick = navController::backToMain,
        )

        // —— My taste ——
        AuraSectionLabel(stringResource(R.string.spotify_taste_my_section))
        if (isBusy) {
            Text(
                text =
                    buildString {
                        append(progress.phase.ifBlank { context.getString(R.string.spotify_taste_loading) })
                        if (progress.currentTitle.isNotBlank()) {
                            append(" — ")
                            append(progress.currentTitle)
                        }
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = AuraSpotifyGreen,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (!hasTaste) {
            Text(
                text = stringResource(R.string.spotify_taste_empty_friendly),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        } else {
            if (listeningLaneId.isNotBlank() || listeningArtists.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.listening_taste_active_lane, activeLane),
                    style = MaterialTheme.typography.labelLarge,
                    color = AuraSpotifyGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            displaySummary?.let { summary ->
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
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 8,
                            softWrap = true,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (artists.isNotEmpty()) {
                AuraSectionLabel(stringResource(R.string.spotify_taste_top_artists))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(artists.take(12)) { index, name ->
                        AuraArtistAvatar(
                            name = name,
                            color = ArtistAvatarPalette[index % ArtistAvatarPalette.size],
                        )
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                AuraSectionLabel(stringResource(R.string.spotify_taste_top_tracks))
                tracks.take(10).forEachIndexed { index, (name, artist) ->
                    AuraTrackRow(
                        index = index + 1,
                        title = name,
                        artist = artist.takeIf { it.isNotBlank() },
                    )
                    if (index < minOf(9, tracks.lastIndex)) AuraDivider()
                }
            }

            if (listeningCategories.isNotEmpty()) {
                AuraSectionLabel(stringResource(R.string.listening_taste_categories))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listeningCategories.forEach { cat ->
                        AuraHintPill(text = cat)
                    }
                }
            }

            if (hints.isNotEmpty()) {
                AuraSectionLabel(stringResource(R.string.spotify_taste_seeds_section))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hints.forEach { hint ->
                        AuraHintPill(text = hint)
                    }
                }
            }

            if (excludedTasteIds.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.listening_taste_excluded_count, excludedTasteIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AuraSecondaryAction(
                    text = stringResource(R.string.listening_taste_clear_exclusions),
                    enabled = !isBusy,
                    onClick = { showClearExclusionsConfirm = true },
                )
            }
            val hasListeningTaste =
                listeningArtists.isNotEmpty() ||
                    listeningTracks.isNotEmpty() ||
                    listeningSummary.isNotBlank() ||
                    listeningCategories.isNotEmpty()
            if (hasListeningTaste) {
                Spacer(Modifier.height(8.dp))
                AuraSecondaryAction(
                    text = stringResource(R.string.listening_taste_reset),
                    enabled = !isBusy,
                    onClick = { showResetListeningTasteConfirm = true },
                )
            }
        }

        // —— Import ——
        AuraSectionLabel(stringResource(R.string.spotify_taste_import_section))
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

        // —— Metro DJ (playback), not recommendations generate ——
        AuraSectionLabel(stringResource(R.string.nano_dj_section))
        Text(
            text = stringResource(R.string.spotify_taste_dj_from_here),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AuraPrimaryPill(
            text = stringResource(R.string.nano_dj_start),
            enabled = !isBusy && playerConnection != null && hasTaste,
            onClick = {
                val connection = playerConnection ?: return@AuraPrimaryPill
                scope.launch {
                    isBusy = true
                    statusMessage = null
                    progress = SpotifyImportProgress(phase = "Starting Metro DJ")
                    val result =
                        NanoDjLauncher.start(
                            context = context,
                            playerConnection = connection,
                            speak = nanoDjSpeak,
                        )
                    statusMessage =
                        result.fold(
                            onSuccess = {
                                val msg = context.getString(R.string.nano_dj_started)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                msg
                            },
                            onFailure = {
                                val msg = it.message ?: context.getString(R.string.ai_error_unknown)
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                msg
                            },
                        )
                    isBusy = false
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        AuraRow(
            title = stringResource(R.string.spotify_taste_go_recommendations),
            subtitle = stringResource(R.string.spotify_taste_go_recommendations_desc),
            showChevron = true,
            onClick = { navController.navigateUp() },
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

        Spacer(Modifier.height(24.dp))
    }
}
