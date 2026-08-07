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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.SavedStateHandle
import com.metrolist.music.ui.screens.HomeScreen
import com.metrolist.music.ui.screens.library.LibraryScreen
import com.metrolist.music.ui.screens.search.SearchScreen

/**
 * The three primary pages intentionally live in one moving strip. Keeping the pages in the same
 * layout is important: the selected tab and the visible page can be driven by exactly one motion
 * value instead of two unrelated NavHost/indicator animations.
 */
@Composable
fun MainTabContainer(
    position: () -> Float,
    pureBlack: Boolean,
    snackbarHostState: SnackbarHostState,
    searchSavedStateHandle: SavedStateHandle,
    modifier: Modifier = Modifier,
) {
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
                    // This is the only page movement. The bottom bubble receives the same
                    // `position` from MainActivity, so both finish on the same frame.
                    translationX = -position() * pageWidthPx
                },
        ) {
            Box(Modifier.width(pageWidth).fillMaxHeight()) {
                HomeScreen(snackbarHostState = snackbarHostState)
            }
            Box(Modifier.width(pageWidth).fillMaxHeight()) {
                SearchScreen(
                    pureBlack = pureBlack,
                    savedStateHandle = searchSavedStateHandle,
                )
            }
            Box(Modifier.width(pageWidth).fillMaxHeight()) {
                LibraryScreen()
            }
        }
    }
}
