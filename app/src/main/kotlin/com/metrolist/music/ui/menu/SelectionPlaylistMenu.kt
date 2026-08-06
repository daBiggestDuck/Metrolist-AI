/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SelectionPlaylistMenu(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && listenTogetherManager.isHost == false

    val playlistSelection = playlists
    val deletable =
        remember(playlistSelection) {
            playlistSelection.filter { it.playlist.isEditable == true || it.songCount != 0 }
        }

    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    LaunchedEffect(playlistSelection) {
        if (playlistSelection.isEmpty()) {
            allSongs = emptyList()
            return@LaunchedEffect
        }
        allSongs =
            withContext(Dispatchers.IO) {
                playlistSelection.flatMap { playlist ->
                    database.playlistSongs(playlist.id).first().map(PlaylistSong::song)
                }
            }
    }

    LaunchedEffect(allSongs) {
        if (allSongs.isEmpty()) {
            downloadState = Download.STATE_STOPPED
            return@LaunchedEffect
        }
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (allSongs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (
                    allSongs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DefaultDialog(
            onDismiss = { showDeleteDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlists_confirm,
                            deletable.size,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(
                    onClick = {
                        showDeleteDialog = false
                        onDismiss()
                        database.transaction {
                            deletable.forEach { playlist ->
                                if (playlist.playlist.bookmarkedAt != null) {
                                    update(playlist.playlist.toggleLike())
                                }
                                delete(playlist.playlist)
                            }
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            deletable.forEach { playlist ->
                                playlist.playlist.browseId?.let { YouTube.deletePlaylist(it) }
                            }
                        }
                        clearAction()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlists_confirm,
                            playlistSelection.size,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showRemoveDownloadDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(
                    onClick = {
                        showRemoveDownloadDialog = false
                        allSongs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false,
                            )
                        }
                        onDismiss()
                        clearAction()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    LazyColumn(
        contentPadding =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                PaddingValues(0.dp)
            } else {
                PaddingValues(
                    start = 0.dp,
                    top = 0.dp,
                    end = 0.dp,
                    bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
                )
            },
    ) {
        item {
            NewActionGrid(
                actions =
                    listOfNotNull(
                        if (!isGuest) {
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    onDismiss()
                                    if (allSongs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            com.metrolist.music.playback.queues.ListQueue(
                                                title = "Selection",
                                                items = allSongs.map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                    clearAction()
                                },
                            )
                        } else {
                            null
                        },
                        if (!isGuest) {
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.queue_music),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.add_to_queue),
                                onClick = {
                                    onDismiss()
                                    if (allSongs.isNotEmpty()) {
                                        playerConnection.addToQueue(allSongs.map { it.toMediaItem() })
                                    }
                                    clearAction()
                                },
                            )
                        } else {
                            null
                        },
                    ),
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items =
                    buildList {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> {
                                add(
                                    Material3MenuItemData(
                                        title = { Text(text = stringResource(R.string.remove_download)) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = { showRemoveDownloadDialog = true },
                                    ),
                                )
                            }
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                add(
                                    Material3MenuItemData(
                                        title = { Text(text = stringResource(R.string.downloading)) },
                                        icon = {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        },
                                        onClick = { showRemoveDownloadDialog = true },
                                    ),
                                )
                            }
                            else -> {
                                add(
                                    Material3MenuItemData(
                                        title = { Text(text = stringResource(R.string.action_download)) },
                                        description = { Text(text = stringResource(R.string.download_desc)) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            allSongs.forEach { song ->
                                                val downloadRequest =
                                                    DownloadRequest
                                                        .Builder(song.id, song.id.toUri())
                                                        .setCustomCacheKey(song.id)
                                                        .setData(song.song.title.toByteArray())
                                                        .build()
                                                DownloadService.sendAddDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    downloadRequest,
                                                    false,
                                                )
                                            }
                                            onDismiss()
                                            clearAction()
                                        },
                                    ),
                                )
                            }
                        }

                        if (!isGuest && deletable.isNotEmpty()) {
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.delete)) },
                                    description = { Text(text = stringResource(R.string.delete_desc)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.delete),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = { showDeleteDialog = true },
                                ),
                            )
                        }
                    },
            )
        }
    }
}
