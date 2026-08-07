/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.SavedStateHandle
import androidx.compose.material3.SnackbarHostState
import com.metrolist.music.ui.screens.HomeScreen
import com.metrolist.music.ui.screens.library.LibraryScreen
import com.metrolist.music.ui.screens.search.SearchScreen
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas

/**
 * The primary pages live in one moving strip. Only the settled page and the destination page are
 * composed while travelling; the other slots remain part of the same strip but do not keep their
 * expensive ViewModels, image shelves, and database collectors active on every frame.
 */
@Composable
fun MainTabContainer(
    position: () -> Float,
    targetPage: Int,
    settledPage: Int,
    playerConnectionAvailable: Boolean,
    pureBlack: Boolean,
    snackbarHostState: SnackbarHostState,
    searchSavedStateHandle: SavedStateHandle,
    modifier: Modifier = Modifier,
) {
    // MainActivity updates settledPage only after the same Animatable completes. Wait one frame
    // before mounting a destination so the first animation frame is not spent composing its full
    // ViewModel/database tree.
    var readyPage by rememberSaveable { mutableIntStateOf(settledPage) }
    LaunchedEffect(targetPage) {
        if (targetPage != settledPage) {
            withFrameNanos { }
            readyPage = targetPage
        } else {
            readyPage = targetPage
        }
    }
    val mountedPages = remember(settledPage, readyPage) {
        setOf(settledPage, readyPage)
    }
    val saveableStateHolder = rememberSaveableStateHolder()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RectangleShape),
    ) {
        val pageWidth: Dp = maxWidth
        val pageWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { pageWidth.toPx() }

        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(pageWidth * 3)
                .graphicsLayer {
                    // This is the same draw-phase position consumed by the bottom-nav indicator.
                    translationX = -position() * pageWidthPx
                },
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .width(pageWidth)
                        .fillMaxHeight()
                        // An omitted page remains a stable slot in the strip. At rest, only the
                        // settled/active page is drawn; during travel, exactly two pages are drawn.
                        .graphicsLayer {
                            alpha = if (index in mountedPages) 1f else 0f
                        },
                ) {
                    if (playerConnectionAvailable && index in mountedPages) {
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
        }
        if (!playerConnectionAvailable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AuraPlayerCanvas),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
