/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.MiniPlayerLikeDislikeSwipeKey
import com.metrolist.music.constants.MiniPlayerShowAddToPlaylistKey
import com.metrolist.music.constants.MiniPlayerShowDjButtonKey
import com.metrolist.music.constants.NanoDjHoldToVoiceKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.aura.AuraTopBar
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerSettings(navController: NavController) {
    val (likeDislikeSwipe, onLikeDislikeSwipeChange) = rememberPreference(MiniPlayerLikeDislikeSwipeKey, true)
    val (showAddToPlaylist, onShowAddToPlaylistChange) = rememberPreference(MiniPlayerShowAddToPlaylistKey, true)
    val (showDjButton, onShowDjButtonChange) = rememberPreference(MiniPlayerShowDjButtonKey, true)
    val (holdToVoice, onHoldToVoiceChange) = rememberPreference(NanoDjHoldToVoiceKey, true)

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )

        Text(
            text = stringResource(R.string.mini_player_settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.mini_player_settings_section),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.favorite),
                        title = { Text(stringResource(R.string.mini_player_like_dislike_swipe)) },
                        searchKey = "mini_player_like_dislike_swipe",
                        description = { Text(stringResource(R.string.mini_player_like_dislike_swipe_desc)) },
                        trailingContent = {
                            Switch(
                                checked = likeDislikeSwipe,
                                onCheckedChange = onLikeDislikeSwipeChange,
                            )
                        },
                        onClick = { onLikeDislikeSwipeChange(!likeDislikeSwipe) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.add),
                        title = { Text(stringResource(R.string.mini_player_show_add_to_playlist)) },
                        searchKey = "mini_player_show_add_to_playlist",
                        trailingContent = {
                            Switch(
                                checked = showAddToPlaylist,
                                onCheckedChange = onShowAddToPlaylistChange,
                            )
                        },
                        onClick = { onShowAddToPlaylistChange(!showAddToPlaylist) },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.mini_player_metro_dj_section),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.radio),
                        title = { Text(stringResource(R.string.mini_player_show_dj_button)) },
                        searchKey = "mini_player_show_dj_button",
                        description = { Text(stringResource(R.string.mini_player_show_dj_button_desc)) },
                        trailingContent = {
                            Switch(
                                checked = showDjButton,
                                onCheckedChange = onShowDjButtonChange,
                            )
                        },
                        onClick = { onShowDjButtonChange(!showDjButton) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.mic),
                        title = { Text(stringResource(R.string.nano_dj_hold_to_voice)) },
                        searchKey = "nano_dj_hold_to_voice",
                        description = { Text(stringResource(R.string.nano_dj_hold_to_voice_desc)) },
                        trailingContent = {
                            Switch(
                                checked = holdToVoice,
                                onCheckedChange = onHoldToVoiceChange,
                            )
                        },
                        onClick = { onHoldToVoiceChange(!holdToVoice) },
                    ),
                ),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    AuraTopBar(
        title = { Text(stringResource(R.string.mini_player_settings_title)) },
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
