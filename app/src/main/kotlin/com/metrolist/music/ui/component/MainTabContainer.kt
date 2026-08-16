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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.compose.material3.SnackbarHostState
import com.metrolist.music.ui.screens.HomeScreen
import com.metrolist.music.ui.screens.library.LibraryScreen
import com.metrolist.music.ui.screens.search.SearchScreen
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The primary pages slide as a horizontal strip. Every page whose slot can be visible during
 * travel stays composed, while nested destinations zero-size this container so they cannot leave
 * a duplicate page behind or intercept input.
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
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Mount the destination page one frame after the tap so the shared bubble/page
    // animation starts immediately. Composing the full destination ViewModel/database tree
    // in the tap frame used to stall the first animation frames and freeze the bottom-nav
    // bubble before it moved. The full travel range still mounts right after, so the slide
    // never drags a black gap across the middle page.
    var readyTarget by remember { mutableIntStateOf(settledPage) }
    LaunchedEffect(targetPage, settledPage) {
        if (targetPage == settledPage) {
            readyTarget = targetPage
        } else {
            withFrameNanos { }
            readyTarget = targetPage
        }
    }
    val mountedPages by remember(settledPage, readyTarget) {
        derivedStateOf {
            val pos = position().coerceIn(0f, 2f)
            val low = minOf(settledPage, readyTarget, floor(pos).toInt())
            val high = maxOf(settledPage, readyTarget, ceil(pos).toInt())
            low..high
        }
    }
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
            val mounted = index in mountedPages
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = position()
                        translationX = (index - p) * pageWidthPx
                        alpha = if (mounted) 1f else 0f
                    },
            ) {
                if (playerConnectionAvailable && mounted) {
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
