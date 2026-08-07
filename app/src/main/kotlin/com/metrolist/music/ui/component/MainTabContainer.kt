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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The primary pages live in one moving strip. Every page whose slot can appear on screen during a
 * travel stays composed — from the settled page through the destination, extended by the live
 * animated position so a cancelled multi-page travel cannot expose an unmounted slot. At rest this
 * collapses back to a single page, so the other slots do not keep their expensive ViewModels,
 * image shelves, and database collectors active.
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
    // Mount the whole page range that can be visible during this travel. Mounting only the two
    // travel endpoints left the middle slot unmounted (alpha = 0), so a Home<->Library slide
    // dragged a black gap across the screen. Extending the range with the live position keeps the
    // middle page mounted even when a second tap cancels the first animation mid-flight. The range
    // only changes on integer boundaries, so this does not recompose on every animation frame.
    val mountedPages by remember(settledPage, targetPage) {
        derivedStateOf {
            val pos = position().coerceIn(0f, 2f)
            val low = minOf(settledPage, targetPage, floor(pos).toInt())
            val high = maxOf(settledPage, targetPage, ceil(pos).toInt())
            low..high
        }
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
                        // settled/active page is drawn; during travel, every page whose slot can
                        // appear on screen stays drawn so no black gap crosses the viewport.
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
