/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val SETTINGS_HIGHLIGHT_KEY = "settings_search_highlight"

/** Optional override; prefer [SettingsSearchHighlightStore] for search jumps. */
val LocalSettingsHighlightId = compositionLocalOf<String?> { null }

/**
 * Process-wide highlight target so search can flash a row after navigation.
 */
object SettingsSearchHighlightStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    private val _activeKey = MutableStateFlow<String?>(null)
    val activeKey: StateFlow<String?> = _activeKey.asStateFlow()

    fun activate(key: String, durationMs: Long = 1_200L) {
        clearJob?.cancel()
        _activeKey.value = key
        clearJob =
            scope.launch {
                delay(durationMs)
                if (_activeKey.value == key) {
                    _activeKey.value = null
                }
            }
    }

    fun clear() {
        clearJob?.cancel()
        _activeKey.value = null
    }
}

@Composable
fun rememberActiveSettingsHighlightId(): String? {
    val local = LocalSettingsHighlightId.current
    val store by SettingsSearchHighlightStore.activeKey.collectAsStateWithLifecycle()
    return local ?: store
}

fun NavController.navigateToSettingsSearchResult(route: String, highlightId: String) {
    SettingsSearchHighlightStore.activate(highlightId)
    val currentRoute = currentDestination?.route
    if (currentRoute != route) {
        navigate(route)
    }
    currentBackStackEntry?.savedStateHandle?.set(SETTINGS_HIGHLIGHT_KEY, highlightId)
}

@Composable
fun settingsSearchHighlightColor(searchKey: String?): Color {
    val active = rememberActiveSettingsHighlightId()
    val highlighted = !searchKey.isNullOrBlank() && searchKey == active
    val animated by animateColorAsState(
        targetValue =
            if (highlighted) {
                AuraSpotifyGreen.copy(alpha = 0.22f)
            } else {
                Color.Transparent
            },
        animationSpec = tween(durationMillis = 220),
        label = "settingsSearchHighlight",
    )
    return animated
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.settingsSearchAnchor(searchKey: String?): Modifier {
    val active = rememberActiveSettingsHighlightId()
    val highlighted = !searchKey.isNullOrBlank() && searchKey == active
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted, searchKey) {
        if (highlighted) {
            delay(120)
            requester.bringIntoView()
        }
    }
    return if (searchKey.isNullOrBlank()) {
        this
    } else {
        this.bringIntoViewRequester(requester)
    }
}
