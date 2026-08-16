/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas
import com.metrolist.music.ui.screens.HomeScreen
import com.metrolist.music.ui.screens.library.LibraryScreen
import com.metrolist.music.ui.screens.search.SearchScreen

/**
 * The three primary pages live in one moving strip and stay composed for the lifetime of the
 * container. Keeping Home, Search, and Library mounted means a bottom-nav tap only slides the
 * strip (a draw-phase transform) instead of composing a fresh page tree (ViewModels, image
 * shelves, and database collectors) on the tap frame — the source of the tab-switch lag. The
 * container clip hides the off-screen slots; nested destinations zero-size this container so
 * they cannot leave a duplicate page behind or intercept input.
 */
@Composable
fun MainTabContainer(
    position: () -> Float,
    playerConnectionAvailable: Boolean,
    pureBlack: Boolean,
    snackbarHostState: SnackbarHostState,
    searchSavedStateHandle: SavedStateHandle,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    BoxWithConstraints(
        modifier = modifier
            .then(if (visible) Modifier.fillMaxSize() else Modifier.size(0.dp))
            .clip(RectangleShape)
            .background(if (pureBlack) Color.Black else AuraPlayerCanvas),
    ) {
        val pageWidth: Dp = maxWidth
        val pageWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { pageWidth.toPx() }

        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = position()
                        translationX = (index - p) * pageWidthPx
                    },
            ) {
                if (playerConnectionAvailable) {
                    saveableStateHolder.SaveableStateProvider("main_tab_$index") {
                        when (index) {
                            0 -> HomeScreen(snackbarHostState = snackbarHostState)
                            1 -> SearchScreen(
                                pureBlack = pureBlack,
                                savedStateHandle = searchSavedStateHandle,
                            )
                            2 -> LibraryScreen()
                        }
                    }
                }
            }
        }
        if (!playerConnectionAvailable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AuraPlayerCanvas),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
