/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.UseLoginForBrowse
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.InfoLabel
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.component.aura.AuraOutlinedButton
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
@Suppress("UNUSED_PARAMETER")
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String,
) {
    val context = LocalContext.current

    val (accountNamePref, _) = rememberPreference(AccountNameKey, "")
    val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, _) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, _) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, _) = rememberPreference(DataSyncIdKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showTokenReplaceConfirm by remember { mutableStateOf(false) }
    var pendingTokenCookie by remember { mutableStateOf("") }
    var pendingTokenVisitorData by remember { mutableStateOf("") }
    var pendingTokenDataSyncId by remember { mutableStateOf("") }
    var pendingTokenAccountName by remember { mutableStateOf("") }
    var pendingTokenAccountEmail by remember { mutableStateOf("") }
    var pendingTokenAccountChannelHandle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // latestVersionName retained for dialog call-site compatibility (profile-only sheet).

    fun navigateAndClose(route: String) {
        onClose()
        navController.navigate(route)
    }

    fun applyPendingTokenAndRestart() {
        accountSettingsViewModel.saveTokenAndRestart(
            context = context,
            cookie = pendingTokenCookie,
            visitorData = pendingTokenVisitorData,
            dataSyncId = pendingTokenDataSyncId,
            accountName = pendingTokenAccountName,
            accountEmail = pendingTokenAccountEmail,
            accountChannelHandle = pendingTokenAccountChannelHandle,
        )
    }

    if (showTokenReplaceConfirm) {
        DefaultDialog(
            onDismiss = { showTokenReplaceConfirm = false },
            title = { Text(stringResource(R.string.token_replace_confirm_title)) },
            content = {
                Text(
                    text = stringResource(R.string.token_replace_confirm_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(onClick = { showTokenReplaceConfirm = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                AuraSecondaryAction(
                    onClick = {
                        showTokenReplaceConfirm = false
                        applyPendingTokenAndRestart()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showLogoutDialog) {
        DefaultDialog(
            onDismiss = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout_dialog_title)) },
            content = {
                Text(
                    text = stringResource(R.string.logout_dialog_message),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                AuraSecondaryAction(
                    onClick = {
                        Timber.d("[LOGOUT_CLEAR] User chose to clear data")
                        scope.launch {
                            try {
                                Timber.d("[LOGOUT_CLEAR] Starting clear and logout process")
                                // Forget account first (stops all sync), then clear data.
                                // This prevents background syncs from re-adding songs.
                                accountSettingsViewModel.logoutAndClearLibraryData(context)
                                Timber.d("[LOGOUT_CLEAR] Library data cleared and account forgotten")
                            } catch (e: Exception) {
                                Timber.e(e, "[LOGOUT_CLEAR] Error clearing library data, proceeding with logout")
                                reportException(e)
                            }
                            onInnerTubeCookieChange("")
                            Timber.d("[LOGOUT_CLEAR] Logout complete")
                            showLogoutDialog = false
                            onClose()
                        }
                    },
                ) {
                    Text(stringResource(R.string.logout_clear))
                }
                AuraSecondaryAction(
                    onClick = {
                        Timber.d("[LOGOUT_KEEP] User chose to keep data")
                        scope.launch {
                            Timber.d("[LOGOUT_KEEP] Starting logout process (keeping data)")
                            accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                            Timber.d("[LOGOUT_KEEP] Logout complete")
                            showLogoutDialog = false
                            onClose()
                        }
                    },
                ) {
                    Text(stringResource(R.string.logout_keep))
                }
            },
        )
    }

    if (showTokenEditor) {
        val text =
            """
            ***INNERTUBE COOKIE*** =$innerTubeCookie
            ***VISITOR DATA*** =$visitorData
            ***DATASYNC ID*** =$dataSyncId
            ***ACCOUNT NAME*** =$accountNamePref
            ***ACCOUNT EMAIL*** =$accountEmail
            ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
            """.trimIndent()

        TextFieldDialog(
            initialTextFieldValue = TextFieldValue(text),
            onDone = { data ->
                var cookie = ""
                var visitorDataValue = ""
                var dataSyncIdValue = ""
                var accountNameValue = ""
                var accountEmailValue = ""
                var accountChannelHandleValue = ""

                data.split("\n").forEach {
                    when {
                        it.startsWith("***INNERTUBE COOKIE*** =") -> cookie = it.substringAfter("=")
                        it.startsWith("***VISITOR DATA*** =") -> visitorDataValue = it.substringAfter("=")
                        it.startsWith("***DATASYNC ID*** =") -> dataSyncIdValue = it.substringAfter("=")
                        it.startsWith("***ACCOUNT NAME*** =") -> accountNameValue = it.substringAfter("=")
                        it.startsWith("***ACCOUNT EMAIL*** =") -> accountEmailValue = it.substringAfter("=")
                        it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") ->
                            accountChannelHandleValue = it.substringAfter("=")
                    }
                }
                pendingTokenCookie = cookie
                pendingTokenVisitorData = visitorDataValue
                pendingTokenDataSyncId = dataSyncIdValue
                pendingTokenAccountName = accountNameValue
                pendingTokenAccountEmail = accountEmailValue
                pendingTokenAccountChannelHandle = accountChannelHandleValue
                if (isLoggedIn) {
                    showTokenEditor = false
                    showTokenReplaceConfirm = true
                } else {
                    // Write all credentials atomically to DataStore and wait for completion
                    // before restarting, preventing the race condition where the process
                    // would be killed before async DataStore coroutines finished writing.
                    applyPendingTokenAndRestart()
                }
            },
            onDismiss = { showTokenEditor = false },
            singleLine = false,
            maxLines = 20,
            isInputValid = { fullText ->
                // Extract the cookie value from the formatted template line,
                // then validate it separately — avoids the bug where parseCookieString
                // received the entire multi-line template and failed to find "SAPISID"
                // as a key because the "***INNERTUBE COOKIE*** =" prefix shadowed it.
                val cookieLine =
                    fullText.lines()
                        .find { it.startsWith("***INNERTUBE COOKIE*** =") }
                val cookieValue =
                    cookieLine?.substringAfter("***INNERTUBE COOKIE*** =")?.trim() ?: ""
                cookieValue.isNotEmpty() && "SAPISID" in parseCookieString(cookieValue)
            },
            extraContent = {
                Spacer(Modifier.height(8.dp))
                InfoLabel(text = stringResource(R.string.token_adv_login_description))
            },
        )
    }

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
        }

        Spacer(Modifier.height(12.dp))

        Material3SettingsGroup(
            items =
                listOf(
                    Material3SettingsItem(
                        title = {
                            Text(
                                text = if (isLoggedIn) accountName else stringResource(R.string.login),
                            )
                        },
                        description =
                            if (isLoggedIn) {
                                { Text(stringResource(R.string.your_profile)) }
                            } else {
                                null
                            },
                        leadingContent =
                            if (isLoggedIn && accountImageUrl != null) {
                                {
                                    AsyncImage(
                                        model = accountImageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .size(40.dp)
                                                .clip(CircleShape),
                                    )
                                }
                            } else {
                                null
                            },
                        icon =
                            if (!isLoggedIn || accountImageUrl == null) {
                                painterResource(if (isLoggedIn) R.drawable.person else R.drawable.login)
                            } else {
                                null
                            },
                        trailingContent = {
                            if (isLoggedIn) {
                                AuraOutlinedButton(
                                    onClick = {
                                        Timber.d("[LOGOUT] User clicked logout button, showing dialog")
                                        showLogoutDialog = true
                                    },
                                ) {
                                    Text(stringResource(R.string.action_logout))
                                }
                            }
                        },
                        onClick = {
                            if (isLoggedIn) {
                                navigateAndClose("account")
                            } else {
                                navigateAndClose("login")
                            }
                        },
                    ),
                ),
            useLowContrast = true,
        )

        Spacer(modifier.height(12.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_account),
            items =
                listOf(
                    Material3SettingsItem(
                        title = {
                            Text(
                                when {
                                    !isLoggedIn -> stringResource(R.string.advanced_login)
                                    showToken -> stringResource(R.string.token_shown)
                                    else -> stringResource(R.string.token_hidden)
                                },
                            )
                        },
                        icon = painterResource(R.drawable.token),
                        onClick = {
                            if (!isLoggedIn) {
                                showTokenEditor = true
                            } else if (!showToken) {
                                showToken = true
                            } else {
                                showTokenEditor = true
                            }
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.more_content)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Switch(
                                enabled = isLoggedIn,
                                checked = useLoginForBrowse,
                                onCheckedChange = {
                                    YouTube.useLoginForBrowse = it
                                    onUseLoginForBrowseChange(it)
                                },
                                thumbContent = {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id = if (useLoginForBrowse) R.drawable.check else R.drawable.close,
                                            ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        },
                        enabled = isLoggedIn,
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Switch(
                                enabled = isLoggedIn,
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange,
                                thumbContent = {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id = if (ytmSync) R.drawable.check else R.drawable.close,
                                            ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        },
                        enabled = isLoggedIn,
                    ),
                ),
            useLowContrast = true,
        )

        Spacer(Modifier.height(8.dp))
    }
}
