/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AndroidAutoSearchLocalLimitKey
import com.metrolist.music.constants.AndroidAutoSectionsOrderKey
import com.metrolist.music.constants.AndroidAutoTargetPlaylistKey
import com.metrolist.music.constants.AndroidAutoYouTubePlaylistsKey
import com.metrolist.music.constants.MediaSessionConstants
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraHairline
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.AuraTopBar
import com.metrolist.music.ui.component.aura.auraHairlineBorder
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.flow.map
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

enum class AndroidAutoSection(val id: String) {
    LIKED("liked"),
    SONGS("songs"),
    ARTISTS("artists"),
    ALBUMS("albums"),
    PLAYLISTS("playlists"),
}

@Composable
fun AndroidAutoSection.label(): String = when (this) {
    AndroidAutoSection.LIKED -> stringResource(R.string.liked_songs)
    AndroidAutoSection.SONGS -> stringResource(R.string.songs)
    AndroidAutoSection.ARTISTS -> stringResource(R.string.artists)
    AndroidAutoSection.ALBUMS -> stringResource(R.string.albums)
    AndroidAutoSection.PLAYLISTS -> stringResource(R.string.playlists)
}

fun serializeSections(sections: List<Pair<AndroidAutoSection, Boolean>>): String =
    sections.joinToString(",") { (section, enabled) -> "${section.id}:$enabled" }

fun deserializeSections(raw: String): List<Pair<AndroidAutoSection, Boolean>> {
    if (raw.isBlank()) return AndroidAutoSection.values().map { it to true }
    return raw.split(",").mapNotNull { token ->
        val parts = token.split(":")
        if (parts.size != 2) return@mapNotNull null
        val section = AndroidAutoSection.values().find { it.id == parts[0] } ?: return@mapNotNull null
        val enabled = parts[1].toBooleanStrictOrNull() ?: true
        section to enabled
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAutoSettings(
    navController: NavController,
) {
    val haptic = LocalHapticFeedback.current
    val database = LocalDatabase.current

    val userPlaylists by remember {
        database.playlistsByCreateDateAsc().map { list -> list.map { it.playlist } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val (youtubePlaylistsEnabled, onYoutubePlaylistsChange) = rememberPreference(
        key = AndroidAutoYouTubePlaylistsKey,
        defaultValue = false
    )

    val (sectionsRaw, onSectionsChange) = rememberPreference(
        key = AndroidAutoSectionsOrderKey,
        defaultValue = serializeSections(AndroidAutoSection.values().map { it to true })
    )

    val (targetPlaylist, onTargetPlaylistChange) = rememberPreference(
        key = AndroidAutoTargetPlaylistKey,
        defaultValue = MediaSessionConstants.TARGET_PLAYLIST_AUTO
    )

    val (androidAutoSearchLocalLimit, onAndroidAutoSearchLocalLimitChange) = rememberPreference(
        AndroidAutoSearchLocalLimitKey,
        defaultValue = 75
    )

    var sections by remember(sectionsRaw) {
        mutableStateOf(deserializeSections(sectionsRaw))
    }

    val lazyListState = rememberLazyListState()
    // Reorderable indices are offset by top inset + visible-sections header items.
    val sectionListOffset = 2
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromReal = from.index - sectionListOffset
        val toReal = to.index - sectionListOffset
        if (fromReal >= 0 && toReal >= 0 && fromReal < sections.size && toReal < sections.size) {
            sections = sections.toMutableList().apply {
                add(toReal, removeAt(fromReal))
            }
            onSectionsChange(serializeSections(sections))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val playlistOptions = listOf(MediaSessionConstants.TARGET_PLAYLIST_AUTO) +
            userPlaylists.map { it.id }

    val playlistLabels: @Composable (String) -> String = { id ->
        if (id == MediaSessionConstants.TARGET_PLAYLIST_AUTO) {
            stringResource(R.string.android_auto_target_playlist_auto)
        } else {
            userPlaylists.find { it.id == id }?.name ?: id
        }
    }

    var showTargetPlaylistDialog by remember { mutableStateOf(false) }

    if (showTargetPlaylistDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTargetPlaylistDialog = false },
            title = { Text(stringResource(R.string.android_auto_target_playlist)) },
            text = {
                LazyColumn {
                    items(playlistOptions) { value ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTargetPlaylistDialog = false
                                    onTargetPlaylistChange(value)
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = value == targetPlaylist,
                                onClick = null,
                            )
                            Text(
                                text = playlistLabels(value),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AuraSecondaryAction(onClick = { showTargetPlaylistDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        ) {
            item(key = "top_inset") {
                Spacer(
                    Modifier.windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                    ),
                )
                Spacer(modifier = Modifier.height(56.dp))
            }

            item(key = "visible_sections_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsSearchAnchor("android_auto_visible_sections")
                        .background(
                            settingsSearchHighlightColor("android_auto_visible_sections"),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(bottom = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.android_auto_visible_sections).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.android_auto_reorder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    )
                }
            }

            itemsIndexed(
                sections,
                key = { _, (section, _) -> section.id },
            ) { index, (section, enabled) ->
                ReorderableItem(reorderableState, key = section.id) {
                    val shape =
                        when {
                            sections.size == 1 -> RoundedCornerShape(12.dp)
                            index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            index == sections.lastIndex ->
                                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(AuraElevated)
                            .then(
                                if (index == 0) {
                                    Modifier.auraHairlineBorder(
                                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (enabled) {
                                            AuraSpotifyGreen.copy(alpha = 0.18f)
                                        } else {
                                            AuraHairline
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.android_auto_section_position, index + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) AuraSpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                painter = painterResource(
                                    when (section) {
                                        AndroidAutoSection.LIKED -> R.drawable.favorite
                                        AndroidAutoSection.SONGS -> R.drawable.music_note
                                        AndroidAutoSection.ARTISTS -> R.drawable.artist
                                        AndroidAutoSection.ALBUMS -> R.drawable.album
                                        AndroidAutoSection.PLAYLISTS -> R.drawable.queue_music
                                    }
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = if (enabled) 1f else 0.45f,
                                ),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (enabled) 1f else 0.55f),
                            ) {
                                Text(
                                    text = section.label(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = stringResource(
                                        if (enabled) {
                                            R.string.android_auto_section_shown
                                        } else {
                                            R.string.android_auto_section_hidden
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (enabled) {
                                        AuraSpotifyGreen
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(end = 4.dp)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = enabled,
                                onCheckedChange = { newValue ->
                                    sections = sections.map { (s, e) ->
                                        if (s == section) s to newValue else s to e
                                    }
                                    onSectionsChange(serializeSections(sections))
                                },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            if (enabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        }
                        if (index < sections.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(1.dp)
                                    .background(AuraHairline),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(AuraHairline.copy(alpha = 0.35f)),
                            )
                        }
                    }
                }
            }

            item(key = "after_sections_spacer") {
                Spacer(modifier = Modifier.height(20.dp))
            }

            item(key = "target_playlist") {
                Material3SettingsGroup(
                    title = stringResource(R.string.android_auto_target_playlist),
                    items = listOf(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.playlist_add),
                            title = { Text(stringResource(R.string.android_auto_target_playlist)) },
                            description = {
                                Text(stringResource(R.string.android_auto_target_playlist_desc))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(playlistLabels(targetPlaylist))
                            },
                            searchKey = "android_auto_target_playlist",
                            onClick = { showTargetPlaylistDialog = true },
                        ),
                    ),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item(key = "mixes") {
                Material3SettingsGroup(
                    title = stringResource(R.string.mixes),
                    items = listOf(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.queue_music),
                            title = { Text(stringResource(R.string.android_auto_youtube_playlists)) },
                            description = {
                                Text(stringResource(R.string.android_auto_youtube_playlists_desc))
                            },
                            searchKey = "android_auto_youtube_playlists",
                            trailingContent = {
                                Switch(
                                    checked = youtubePlaylistsEnabled,
                                    onCheckedChange = onYoutubePlaylistsChange,
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                if (youtubePlaylistsEnabled) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    },
                                )
                            },
                            onClick = { onYoutubePlaylistsChange(!youtubePlaylistsEnabled) },
                        ),
                    ),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item(key = "search_options") {
                val limitValues = remember { listOf(10, 25, 50, 75, 100, 150, 200, -1) }
                Material3SettingsGroup(
                    title = stringResource(R.string.android_auto_search_options),
                    items = listOf(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.manage_search),
                            title = { Text(stringResource(R.string.android_auto_search_local_songs_limit)) },
                            searchKey = "android_auto_search_local_songs_limit",
                            description = {
                                Column {
                                    Text(stringResource(R.string.android_auto_search_local_songs_limit_desc))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text =
                                            when (androidAutoSearchLocalLimit) {
                                                -1 -> stringResource(R.string.unlimited)
                                                else -> "$androidAutoSearchLocalLimit ${stringResource(R.string.songs)}"
                                            },
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Slider(
                                        value = limitValues.indexOf(androidAutoSearchLocalLimit).toFloat()
                                            .coerceAtLeast(0f),
                                        enabled = true,
                                        onValueChange = {
                                            val newValue = limitValues[it.roundToInt()]
                                            onAndroidAutoSearchLocalLimitChange(newValue)
                                        },
                                        steps = limitValues.size - 2,
                                        valueRange = 0f..(limitValues.size - 1).toFloat(),
                                    )
                                }
                            },
                        ),
                    ),
                )
            }
        }

        AuraTopBar(
            title = { Text(stringResource(R.string.android_auto)) },
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
}
