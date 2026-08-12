/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.lifecycle.SavedStateHandle
import androidx.compose.material3.SnackbarHostState
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas
import com.metrolist.music.ui.screens.HomeScreen
import com.metrolist.music.ui.screens.library.LibraryScreen
import com.metrolist.music.ui.screens.search.SearchScreen

/**
 * Keeps visited primary tabs composed so returning to Home does not recreate its shelves.
 * Main pages switch instantly; the only navigation travel animation is the bottom-bar bubble.
 */
@Composable
fun MainTabContainer(
    selectedPage: Int,
    playerConnectionAvailable: Boolean,
    pureBlack: Boolean,
    snackbarHostState: SnackbarHostState,
    searchSavedStateHandle: SavedStateHandle,
    modifier: Modifier = Modifier,
) {
    // Keep all primary pages composed from the first frame. Their ViewModels and scroll state
    // remain warm, so switching back to Home does not recreate the filter chrome or shelves.
    val visitedMask = rememberSaveable { 0b111 }

    Box(modifier = modifier.fillMaxSize()) {
        if (playerConnectionAvailable) {
            repeat(3) { index ->
                if (visitedMask and (1 shl index) != 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (index == selectedPage) 1f else 0f)
                            .graphicsLayer {
                                alpha = if (index == selectedPage) 1f else 0f
                            },
                    ) {
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
        } else {
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
