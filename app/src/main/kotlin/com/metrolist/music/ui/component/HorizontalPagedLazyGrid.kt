/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider

/**
 * Horizontal multi-row list grid that page-snaps when flinging sideways.
 *
 * Used for Quick Picks / Trending-style carousels where each "page" is one
 * viewport of song columns — scrolling cannot rest between pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalPagedLazyGrid(
    rows: Int,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    val snapLayoutInfoProvider =
        remember(state) {
            SnapLayoutInfoProvider(lazyGridState = state)
        }
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = false,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

/** Preferred page width factor for paged song grids (one page ≈ one column of songs). */
fun horizontalPagedItemWidthFactor(maxWidth: Dp): Float =
    if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
