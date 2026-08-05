/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ai.DjAiProvider
import com.metrolist.music.ai.GeminiNanoClient
import com.metrolist.music.ai.GeminiNanoStatus
import com.metrolist.music.constants.DjAiApiKey
import com.metrolist.music.constants.DjAiBaseUrlKey
import com.metrolist.music.constants.DjAiModelKey
import com.metrolist.music.constants.DjAiProviderKey
import com.metrolist.music.constants.EnableGeminiNanoKey
import com.metrolist.music.constants.NanoDjSpeakKey
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.component.aura.AuraOutlinedButton
import com.metrolist.music.ui.component.aura.AuraTopBar
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjSettings(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (enableGeminiNano, onEnableGeminiNanoChange) = rememberPreference(EnableGeminiNanoKey, true)
    var djAiProviderId by rememberPreference(DjAiProviderKey, DjAiProvider.NANO.id)
    var djAiApiKey by rememberPreference(DjAiApiKey, "")
    var djAiModel by rememberPreference(DjAiModelKey, "")
    var djAiBaseUrl by rememberPreference(DjAiBaseUrlKey, "")
    var openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    val (nanoDjSpeak, onNanoDjSpeakChange) = rememberPreference(NanoDjSpeakKey, true)
    val djAiProvider = DjAiProvider.fromId(djAiProviderId)
    var geminiStatus by rememberSaveable { mutableStateOf(GeminiNanoStatus.Unavailable) }
    var geminiBusy by rememberSaveable { mutableStateOf(false) }

    var showDjProviderDialog by rememberSaveable { mutableStateOf(false) }
    var showDjApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showDjModelDialog by rememberSaveable { mutableStateOf(false) }
    var showDjBaseUrlDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        enableGeminiNano,
        djAiProviderId,
        djAiApiKey,
        djAiModel,
        djAiBaseUrl,
        openRouterApiKey,
    ) {
        GeminiNanoClient.invalidate()
        if (!enableGeminiNano) {
            geminiStatus = GeminiNanoStatus.Unavailable
            return@LaunchedEffect
        }
        geminiBusy = true
        geminiStatus =
            runCatching { GeminiNanoClient.get(context).checkStatus() }
                .getOrElse {
                    Timber.w(it, "DJ AI status check failed")
                    GeminiNanoStatus.Error
                }
        geminiBusy = false
    }

    val djDefaultModel = GeminiNanoClient.defaultModelFor(djAiProvider)

    if (showDjProviderDialog) {
        EnumDialog(
            onDismiss = { showDjProviderDialog = false },
            onSelect = {
                djAiProviderId = it.id
                if (djAiModel.isBlank() || DjAiProvider.entries.any { p ->
                        GeminiNanoClient.defaultModelFor(p) == djAiModel
                    }
                ) {
                    djAiModel = GeminiNanoClient.defaultModelFor(it)
                }
                GeminiNanoClient.invalidate()
                showDjProviderDialog = false
            },
            title = stringResource(R.string.dj_ai_provider),
            current = djAiProvider,
            values = DjAiProvider.entries.toList(),
            valueText = { it.displayName },
        )
    }

    if (showDjApiKeyDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.dj_ai_api_key)) },
            icon = { Icon(painterResource(R.drawable.key), null) },
            initialTextFieldValue = TextFieldValue(text = djAiApiKey),
            onDone = {
                djAiApiKey = it.trim()
                GeminiNanoClient.invalidate()
                showDjApiKeyDialog = false
            },
            onDismiss = { showDjApiKeyDialog = false },
        )
    }

    if (showDjModelDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.dj_ai_model)) },
            icon = { Icon(painterResource(R.drawable.discover_tune), null) },
            initialTextFieldValue =
                TextFieldValue(text = djAiModel.ifBlank { djDefaultModel }),
            onDone = {
                djAiModel = it.trim()
                GeminiNanoClient.invalidate()
                showDjModelDialog = false
            },
            onDismiss = { showDjModelDialog = false },
        )
    }

    if (showDjBaseUrlDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.dj_ai_base_url)) },
            icon = { Icon(painterResource(R.drawable.link), null) },
            initialTextFieldValue = TextFieldValue(text = djAiBaseUrl),
            onDone = {
                djAiBaseUrl = it.trim()
                GeminiNanoClient.invalidate()
                showDjBaseUrlDialog = false
            },
            onDismiss = { showDjBaseUrlDialog = false },
        )
    }

    androidx.compose.foundation.layout.Column(
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
            text = stringResource(R.string.dj_settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.dj_settings_section_ai),
            items =
                buildList {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.discover_tune),
                            title = { Text(stringResource(R.string.gemini_nano_enable)) },
                            description = { Text(stringResource(R.string.gemini_nano_enable_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = enableGeminiNano,
                                    onCheckedChange = onEnableGeminiNanoChange,
                                )
                            },
                            onClick = { onEnableGeminiNanoChange(!enableGeminiNano) },
                        ),
                    )
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.explore_outlined),
                            title = { Text(stringResource(R.string.dj_ai_provider)) },
                            description = {
                                Text(
                                    "${djAiProvider.displayName} — ${stringResource(R.string.dj_ai_provider_desc)}",
                                )
                            },
                            onClick = { showDjProviderDialog = true },
                        ),
                    )
                    if (djAiProvider == DjAiProvider.NANO) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.info),
                                title = { Text(stringResource(R.string.gemini_nano_status)) },
                                description = {
                                    Text(
                                        when (geminiStatus) {
                                            GeminiNanoStatus.Unavailable ->
                                                stringResource(R.string.gemini_nano_status_unavailable)
                                            GeminiNanoStatus.Downloadable ->
                                                stringResource(R.string.gemini_nano_status_downloadable)
                                            GeminiNanoStatus.Downloading ->
                                                stringResource(R.string.gemini_nano_status_downloading)
                                            GeminiNanoStatus.Available ->
                                                stringResource(R.string.gemini_nano_status_available)
                                            GeminiNanoStatus.Error ->
                                                stringResource(R.string.gemini_nano_status_error)
                                        },
                                    )
                                },
                                trailingContent = {
                                    if (enableGeminiNano && geminiStatus == GeminiNanoStatus.Downloadable) {
                                        AuraOutlinedButton(
                                            enabled = !geminiBusy,
                                            onClick = {
                                                scope.launch {
                                                    geminiBusy = true
                                                    geminiStatus = GeminiNanoStatus.Downloading
                                                    runCatching {
                                                        GeminiNanoClient.get(context).download()
                                                    }.onFailure {
                                                        Timber.w(it, "Gemini Nano download failed")
                                                        geminiStatus = GeminiNanoStatus.Error
                                                    }.onSuccess {
                                                        geminiStatus =
                                                            runCatching {
                                                                GeminiNanoClient.get(context).checkStatus()
                                                            }.getOrDefault(GeminiNanoStatus.Error)
                                                    }
                                                    geminiBusy = false
                                                }
                                            },
                                        ) {
                                            Text(stringResource(R.string.gemini_nano_download))
                                        }
                                    }
                                },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.info),
                                title = { Text(stringResource(R.string.gemini_nano_privacy)) },
                            ),
                        )
                    } else {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.info),
                                title = { Text(stringResource(R.string.gemini_nano_status)) },
                                description = {
                                    Text(
                                        when {
                                            geminiStatus == GeminiNanoStatus.Available ->
                                                stringResource(R.string.dj_ai_status_ready)
                                            djAiApiKey.isBlank() &&
                                                !(
                                                    djAiProvider == DjAiProvider.OPENROUTER &&
                                                        openRouterApiKey.isNotBlank()
                                                ) ->
                                                stringResource(R.string.dj_ai_status_needs_key)
                                            else ->
                                                stringResource(R.string.gemini_nano_status_unavailable)
                                        },
                                    )
                                },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.key),
                                title = { Text(stringResource(R.string.dj_ai_api_key)) },
                                description = {
                                    Text(
                                        if (djAiApiKey.isNotEmpty()) {
                                            "•".repeat(minOf(djAiApiKey.length, 8))
                                        } else if (
                                            djAiProvider == DjAiProvider.OPENROUTER &&
                                            openRouterApiKey.isNotEmpty()
                                        ) {
                                            stringResource(R.string.ai_api_key) + " (lyrics)"
                                        } else {
                                            stringResource(R.string.dj_ai_api_key_desc)
                                        },
                                    )
                                },
                                onClick = { showDjApiKeyDialog = true },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.discover_tune),
                                title = { Text(stringResource(R.string.dj_ai_model)) },
                                description = {
                                    Text(djAiModel.ifBlank { djDefaultModel }.ifBlank { stringResource(R.string.not_set) })
                                },
                                onClick = { showDjModelDialog = true },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.link),
                                title = { Text(stringResource(R.string.dj_ai_base_url)) },
                                description = {
                                    Text(
                                        djAiBaseUrl.ifBlank {
                                            stringResource(R.string.dj_ai_base_url_desc)
                                        },
                                    )
                                },
                                onClick = { showDjBaseUrlDialog = true },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.info),
                                title = { Text(stringResource(R.string.dj_ai_cloud_help)) },
                            ),
                        )
                    }
                },
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.dj_settings_section_playback),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.mic),
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

        Spacer(modifier = Modifier.height(16.dp))
    }

    AuraTopBar(
        title = { Text(stringResource(R.string.dj_settings_title)) },
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
