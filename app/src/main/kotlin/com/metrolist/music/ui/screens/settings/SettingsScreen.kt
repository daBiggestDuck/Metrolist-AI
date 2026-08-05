/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalChangelogState
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.ui.component.AccountSettingsDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.AuraTopBar
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.rememberPreference

private data class SettingsHubEntry(
    val title: String,
    val icon: Painter,
    val showBadge: Boolean = false,
    val description: String? = null,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val showChangelog = LocalChangelogState.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasAndroidAuto =
        remember {
            try {
                context.packageManager.getPackageInfo(
                    "com.google.android.projection.gearhead",
                    0,
                )
                true
            } catch (_: Exception) {
                false
            }
        }

    val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    var searchQuery by remember { mutableStateOf("") }
    var showAccountDialog by remember { mutableStateOf(false) }
    val query = searchQuery.trim()

    fun matches(title: String, extra: String? = null): Boolean {
        if (query.isEmpty()) return true
        return title.contains(query, ignoreCase = true) ||
            (extra?.contains(query, ignoreCase = true) == true)
    }

    fun matchesDeep(vararg keywords: String): Boolean {
        if (query.isEmpty()) return true
        return keywords.any { it.contains(query, ignoreCase = true) }
    }

    val nestedAppearance = listOf("dark theme", "pure black", "dynamic theme", "player background", "theme", "appearance")
    val nestedContent = listOf("content language", "content country", "explicit", "quick picks", "proxy", "randomize")
    val nestedPlayer = listOf("audio quality", "persistent queue", "skip silence", "normalization", "lyrics", "player")
    val nestedPrivacy = listOf("listen history", "search history", "privacy")
    val nestedDj = listOf("nano dj", "gemini nano", "openai", "anthropic", "openrouter", "groq", "hack club", "dj")
    val nestedAi = listOf("lyrics translation", "ai provider", "deepl", "translate")
    val nestedIntegrations = listOf("discord", "lastfm", "spotify", "exportify", "taste", "listen together")
    val nestedBackup = listOf("backup", "restore", "csv", "exportify", "import")
    val nestedStorage = listOf("cache", "storage", "clear song cache", "image cache")

    val updateAvailable =
        BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.VERSION_NAME

    val accountTitle = stringResource(R.string.account)
    val loginTitle = stringResource(R.string.login)
    val integrationsTitle = stringResource(R.string.integrations)
    val appearanceTitle = stringResource(R.string.appearance)
    val contentTitle = stringResource(R.string.content)
    val aiLyricsTitle = stringResource(R.string.ai_lyrics_translation)
    val nanoDjTitle = stringResource(R.string.dj_settings_title)
    val androidAutoTitle = stringResource(R.string.android_auto)
    val playerTitle = stringResource(R.string.player_and_audio)
    val streamSourcesTitle = stringResource(R.string.stream_sources)
    val privacyTitle = stringResource(R.string.privacy)
    val updaterTitle = stringResource(R.string.updater)
    val storageTitle = stringResource(R.string.storage)
    val backupTitle = stringResource(R.string.backup_restore)
    val defaultLinksTitle = stringResource(R.string.default_links)
    val changelogTitle = stringResource(R.string.changelog)
    val aboutTitle = stringResource(R.string.about)
    val newVersionTitle = stringResource(R.string.new_version_available)

    val accountSectionTitle = stringResource(R.string.settings_section_account)
    val contentDisplaySectionTitle = stringResource(R.string.settings_section_content_display)
    val playbackSectionTitle = stringResource(R.string.settings_section_playback)
    val privacySocialSectionTitle = stringResource(R.string.settings_section_privacy_social)
    val notificationsSectionTitle = stringResource(R.string.settings_section_notifications)
    val aboutDataSectionTitle = stringResource(R.string.settings_section_about_data)

    val personIcon = painterResource(R.drawable.person)
    val loginIcon = painterResource(R.drawable.login)
    val integrationIcon = painterResource(R.drawable.integration)
    val paletteIcon = painterResource(R.drawable.palette)
    val languageIcon = painterResource(R.drawable.language)
    val translateIcon = painterResource(R.drawable.translate)
    val nanoDjIcon = painterResource(R.drawable.radio)
    val androidAutoIcon = painterResource(R.drawable.ic_android_auto)
    val playIcon = painterResource(R.drawable.play)
    val radioIcon = painterResource(R.drawable.radio)
    val securityIcon = painterResource(R.drawable.security)
    val updateIcon = painterResource(R.drawable.update)
    val storageIcon = painterResource(R.drawable.storage)
    val restoreIcon = painterResource(R.drawable.restore)
    val linkIcon = painterResource(R.drawable.link)
    val newspaperIcon = painterResource(R.drawable.newspaper)
    val infoIcon = painterResource(R.drawable.info)

    val accountItems =
        buildList {
            val title = if (isLoggedIn) accountTitle else loginTitle
            if (matches(title, accountSectionTitle)) {
                add(
                    SettingsHubEntry(
                        title = title,
                        icon = if (isLoggedIn) personIcon else loginIcon,
                        onClick = { showAccountDialog = true },
                    ),
                )
            }
            if (matches(integrationsTitle, accountSectionTitle) || matchesDeep(*nestedIntegrations.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = integrationsTitle,
                        icon = integrationIcon,
                        onClick = { navController.navigate("settings/integrations") },
                    ),
                )
            }
        }

    val contentDisplayItems =
        buildList {
            if (matches(appearanceTitle, contentDisplaySectionTitle) || matchesDeep(*nestedAppearance.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = appearanceTitle,
                        icon = paletteIcon,
                        onClick = { navController.navigate("settings/appearance") },
                    ),
                )
            }
            if (matches(contentTitle, contentDisplaySectionTitle) || matchesDeep(*nestedContent.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = contentTitle,
                        icon = languageIcon,
                        onClick = { navController.navigate("settings/content") },
                    ),
                )
            }
            if (matches(aiLyricsTitle, contentDisplaySectionTitle) || matchesDeep(*nestedAi.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = aiLyricsTitle,
                        icon = translateIcon,
                        onClick = { navController.navigate("settings/ai") },
                    ),
                )
            }
            if (hasAndroidAuto && matches(androidAutoTitle, contentDisplaySectionTitle)) {
                add(
                    SettingsHubEntry(
                        title = androidAutoTitle,
                        icon = androidAutoIcon,
                        onClick = { navController.navigate("settings/android_auto") },
                    ),
                )
            }
        }

    val playbackItems =
        buildList {
            if (matches(playerTitle, playbackSectionTitle) || matchesDeep(*nestedPlayer.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = playerTitle,
                        icon = playIcon,
                        onClick = { navController.navigate("settings/player") },
                    ),
                )
            }
            if (matches(nanoDjTitle, playbackSectionTitle) || matchesDeep(*nestedDj.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = nanoDjTitle,
                        icon = nanoDjIcon,
                        description = stringResource(R.string.dj_settings_desc),
                        onClick = { navController.navigate("settings/dj") },
                    ),
                )
            }
            if (matches(streamSourcesTitle, playbackSectionTitle)) {
                add(
                    SettingsHubEntry(
                        title = streamSourcesTitle,
                        icon = radioIcon,
                        onClick = { navController.navigate("settings/stream_sources") },
                    ),
                )
            }
        }

    val privacyItems =
        buildList {
            if (matches(privacyTitle, privacySocialSectionTitle) || matchesDeep(*nestedPrivacy.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = privacyTitle,
                        icon = securityIcon,
                        onClick = { navController.navigate("settings/privacy") },
                    ),
                )
            }
        }

    val notificationIcon = painterResource(R.drawable.notification)
    val systemNotificationsTitle = stringResource(R.string.settings_system_notifications)
    val systemNotificationsDesc = stringResource(R.string.settings_system_notifications_desc)

    val notificationItems =
        buildList {
            if (matches(systemNotificationsTitle, notificationsSectionTitle) ||
                matches(systemNotificationsDesc, notificationsSectionTitle)
            ) {
                add(
                    SettingsHubEntry(
                        title = systemNotificationsTitle,
                        icon = notificationIcon,
                        description = systemNotificationsDesc,
                        onClick = {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent =
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = "package:${context.packageName}".toUri()
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.open_app_settings_error,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                            }
                        },
                    ),
                )
            }
            if (BuildConfig.UPDATER_AVAILABLE && matches(updaterTitle, notificationsSectionTitle)) {
                add(
                    SettingsHubEntry(
                        title = updaterTitle,
                        icon = updateIcon,
                        onClick = { navController.navigate("settings/updater") },
                    ),
                )
            }
        }

    val aboutDataItems =
        buildList {
            if (matches(storageTitle, aboutDataSectionTitle) || matchesDeep(*nestedStorage.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = storageTitle,
                        icon = storageIcon,
                        onClick = { navController.navigate("settings/storage") },
                    ),
                )
            }
            if (matches(backupTitle, aboutDataSectionTitle) || matchesDeep(*nestedBackup.toTypedArray())) {
                add(
                    SettingsHubEntry(
                        title = backupTitle,
                        icon = restoreIcon,
                        onClick = { navController.navigate("settings/backup_restore") },
                    ),
                )
            }
            if (isAndroid12OrLater && matches(defaultLinksTitle, aboutDataSectionTitle)) {
                add(
                    SettingsHubEntry(
                        title = defaultLinksTitle,
                        icon = linkIcon,
                        onClick = {
                            try {
                                val intent =
                                    Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        "package:${context.packageName}".toUri(),
                                    )
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                when (e) {
                                    is ActivityNotFoundException,
                                    is SecurityException,
                                    -> {
                                        Toast
                                            .makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    }
                                    else -> {
                                        Toast
                                            .makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    }
                                }
                            }
                        },
                    ),
                )
            }
            if (matches(changelogTitle, aboutDataSectionTitle)) {
                add(
                    SettingsHubEntry(
                        title = changelogTitle,
                        icon = newspaperIcon,
                        onClick = { showChangelog.value = true },
                    ),
                )
            }
            if (matches(aboutTitle, aboutDataSectionTitle)) {
                add(
                    SettingsHubEntry(
                        title = aboutTitle,
                        icon = infoIcon,
                        onClick = { navController.navigate("settings/about") },
                    ),
                )
            }
            if (updateAvailable && matches(newVersionTitle, aboutDataSectionTitle)) {
                val releaseInfo = Updater.getCachedLatestRelease()
                val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }
                if (downloadUrl != null) {
                    add(
                        SettingsHubEntry(
                            title = newVersionTitle,
                            icon = updateIcon,
                            showBadge = true,
                            description = latestVersionName,
                            onClick = { uriHandler.openUri(downloadUrl) },
                        ),
                    )
                }
            }
        }

    fun toMaterialItems(entries: List<SettingsHubEntry>) =
        entries.map { entry ->
            Material3SettingsItem(
                icon = entry.icon,
                title = { Text(entry.title) },
                description =
                    entry.description?.let { desc ->
                        {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                showBadge = entry.showBadge,
                onClick = entry.onClick,
            )
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(AuraPlayerCanvas),
    ) {
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
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top,
                ),
            ),
        )

        Spacer(modifier = Modifier.height(56.dp))

        SettingsSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val hasAnyResults =
            accountItems.isNotEmpty() ||
                contentDisplayItems.isNotEmpty() ||
                playbackItems.isNotEmpty() ||
                privacyItems.isNotEmpty() ||
                notificationItems.isNotEmpty() ||
                aboutDataItems.isNotEmpty()

        if (!hasAnyResults) {
            Text(
                text = stringResource(R.string.settings_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp),
            )
        }

        if (accountItems.isNotEmpty()) {
            Material3SettingsGroup(
                title = accountSectionTitle,
                items = toMaterialItems(accountItems),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (contentDisplayItems.isNotEmpty()) {
            Material3SettingsGroup(
                title = contentDisplaySectionTitle,
                items = toMaterialItems(contentDisplayItems),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (playbackItems.isNotEmpty()) {
            Material3SettingsGroup(
                title = playbackSectionTitle,
                items = toMaterialItems(playbackItems),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (privacyItems.isNotEmpty()) {
            Material3SettingsGroup(
                title = privacySocialSectionTitle,
                items = toMaterialItems(privacyItems),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (notificationItems.isNotEmpty()) {
            Material3SettingsGroup(
                title = notificationsSectionTitle,
                items = toMaterialItems(notificationItems),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (aboutDataItems.isNotEmpty()) {
            Material3SettingsGroup(
                title = aboutDataSectionTitle,
                items = toMaterialItems(aboutDataItems),
            )
        }

        if (updateAvailable) {
            Spacer(modifier = Modifier.height(16.dp))
            ReleaseNotesCard()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    AuraTopBar(
        title = { Text(stringResource(R.string.settings)) },
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

    if (showAccountDialog) {
        AccountSettingsDialog(
            onDismiss = { showAccountDialog = false },
            latestVersionName = latestVersionName,
        )
    }
    } // Box
}

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchPillShape = RoundedCornerShape(percent = 50)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .auraFloatingIsland(
                    shape = searchPillShape,
                    color = AuraElevated,
                    elevation = 5.dp,
                )
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle =
                TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                ),
            cursorBrush = SolidColor(AuraSpotifyGreen),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_settings),
                        style =
                            TextStyle(
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 16.sp,
                            ),
                    )
                }
                innerTextField()
            },
        )
    }
}
