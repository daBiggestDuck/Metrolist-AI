/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.MetroDjChatSheet
import com.metrolist.music.ui.component.MetroDjMessage
import com.metrolist.music.ui.component.NanoDjChatHistory
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraHairline
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import kotlinx.coroutines.launch

/** A single archived Metro DJ conversation, kept in memory for the recent-chats sidebar. */
data class DjChatRecord(
    val title: String,
    val preview: String,
    val messages: List<MetroDjMessage>,
)

/** In-memory store of recent Metro DJ chats (titles + content) for the dedicated DJ page. */
object DjChatHistoryStore {
    val chats = mutableStateListOf<DjChatRecord>()

    /** Archives the current live conversation when it has at least one listener message. */
    fun archiveCurrent() {
        val snapshot = NanoDjChatHistory.messages.toList()
        val firstUser = snapshot.firstOrNull { !it.fromDj } ?: return
        val title = firstUser.text.trim().take(40).ifBlank { return }
        val preview = snapshot.lastOrNull()?.text?.trim()?.take(80).orEmpty()
        chats.removeAll { it.title == title }
        chats.add(0, DjChatRecord(title = title, preview = preview, messages = snapshot))
        while (chats.size > 50) chats.removeAt(chats.lastIndex)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjScreen(navController: NavController) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val welcomeText = stringResource(R.string.nano_dj_chat_welcome)

    // Archive the conversation when leaving the page so it shows up under Recent chats.
    DisposableEffect(Unit) {
        onDispose { DjChatHistoryStore.archiveCurrent() }
    }

    val filteredChats =
        remember(searchQuery) {
            if (searchQuery.isBlank()) {
                DjChatHistoryStore.chats.toList()
            } else {
                val q = searchQuery.trim()
                DjChatHistoryStore.chats.filter { record ->
                    record.title.contains(q, ignoreCase = true) ||
                        record.preview.contains(q, ignoreCase = true) ||
                        record.messages.any { it.text.contains(q, ignoreCase = true) }
                }
            }
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AuraElevated,
                drawerContentColor = Color.White,
                modifier = Modifier.fillMaxWidth(0.82f),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
                            .padding(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dj_page_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { scope.launch { drawerState.close() } },
                            onLongClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                            )
                        }
                    }

                    // New chat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(AuraSpotifyGreen.copy(alpha = 0.16f))
                                .clickable {
                                    DjChatHistoryStore.archiveCurrent()
                                    NanoDjChatHistory.messages.clear()
                                    NanoDjChatHistory.messages +=
                                        MetroDjMessage(true, welcomeText)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = AuraSpotifyGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.dj_new_chat),
                            color = AuraSpotifyGreen,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Search previous chats by title or content.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .auraFloatingIsland(
                                    shape = RoundedCornerShape(50),
                                    color = AuraPlayerCanvas,
                                    elevation = 0.dp,
                                )
                                .padding(horizontal = 14.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(AuraSpotifyGreen),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.dj_search_chats),
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 15.sp,
                                    )
                                }
                                inner()
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.dj_recent_chats),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 6.dp),
                    )

                    if (filteredChats.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dj_no_recent_chats),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredChats, key = { it.title }) { record ->
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                NanoDjChatHistory.messages.clear()
                                                NanoDjChatHistory.messages.addAll(record.messages)
                                                scope.launch { drawerState.close() }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = record.title,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (record.preview.isNotBlank()) {
                                        Text(
                                            text = record.preview,
                                            color = Color.White.copy(alpha = 0.55f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp)
                                        .height(1.dp)
                                        .background(AuraHairline),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(AuraPlayerCanvas)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .imePadding(),
        ) {
            // Top bar: sidebar (recent chats) toggle on the left, Gemini-style.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.history),
                        contentDescription = stringResource(R.string.dj_recent_chats),
                    )
                }
                Text(
                    text = stringResource(R.string.dj_page_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                MetroDjChatSheet(
                    onDismiss = navController::navigateUp,
                    fullScreen = true,
                )
            }
        }
    }
}
