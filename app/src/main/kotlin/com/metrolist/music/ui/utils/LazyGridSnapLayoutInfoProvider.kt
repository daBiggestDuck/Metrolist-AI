/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val SnapToContentStart: (Float, Float) -> Float = { _, _ -> 0f }

/**
 * Snap provider for horizontal LazyGrids (Quick Picks, Trending, Charts, …).
 *
 * Snaps per **page** of columns that fill the viewport (not mid-card / mid-page).
 * On phone-width layouts one column ≈ one page; on wide layouts several columns
 * share a page and flings settle on page boundaries only.
 */
@ExperimentalFoundationApi
fun SnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = SnapToContentStart,
    velocityThreshold: Float = 1000f,
): SnapLayoutInfoProvider =
    object : SnapLayoutInfoProvider {
        private val layoutInfo: LazyGridLayoutInfo
            get() = lazyGridState.layoutInfo

        override fun calculateApproachOffset(
            velocity: Float,
            decayOffset: Float,
        ): Float = 0f

        override fun calculateSnapOffset(velocity: Float): Float {
            val bounds = calculateSnappingOffsetBounds()

            // Low / zero velocity: always settle on the nearest page (no free-scroll mid-page).
            if (abs(velocity) < velocityThreshold) {
                return if (abs(bounds.start) <= abs(bounds.endInclusive)) {
                    bounds.start
                } else {
                    bounds.endInclusive
                }
            }

            return when {
                velocity < 0 -> bounds.start
                velocity > 0 -> bounds.endInclusive
                else -> 0f
            }
        }

        private fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
            var lowerBoundOffset = Float.NEGATIVE_INFINITY
            var upperBoundOffset = Float.POSITIVE_INFINITY

            val viewport =
                (
                    layoutInfo.singleAxisViewportSize -
                        layoutInfo.beforeContentPadding -
                        layoutInfo.afterContentPadding
                ).toFloat()
                    .coerceAtLeast(1f)

            // One representative item per column (row 0), ordered by column index.
            val columns = ArrayList<LazyGridItemInfo>()
            var lastColumn = Int.MIN_VALUE
            layoutInfo.visibleItemsInfo.fastForEach { item ->
                if (item.column == lastColumn) return@fastForEach
                lastColumn = item.column
                columns.add(item)
            }
            if (columns.isEmpty()) return 0f.rangeTo(0f)

            val stridePx =
                if (columns.size >= 2) {
                    abs(columns[1].offset.x - columns[0].offset.x).toFloat().coerceAtLeast(1f)
                } else {
                    columns[0].size.width.toFloat().coerceAtLeast(1f)
                }
            val columnsPerPage = max(1, (viewport / stridePx).roundToInt().coerceAtLeast(1))

            columns.fastForEach { item ->
                // Only page-start columns are valid snap targets.
                if (item.column % columnsPerPage != 0) return@fastForEach

                val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

                if (offset <= 0 && offset > lowerBoundOffset) {
                    lowerBoundOffset = offset
                }
                if (offset >= 0 && offset < upperBoundOffset) {
                    upperBoundOffset = offset
                }
            }

            // Fallback: if no page-start column is visible, snap to nearest column edge.
            if (lowerBoundOffset == Float.NEGATIVE_INFINITY && upperBoundOffset == Float.POSITIVE_INFINITY) {
                columns.fastForEach { item ->
                    val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)
                    if (offset <= 0 && offset > lowerBoundOffset) {
                        lowerBoundOffset = offset
                    }
                    if (offset >= 0 && offset < upperBoundOffset) {
                        upperBoundOffset = offset
                    }
                }
            }

            if (lowerBoundOffset == Float.NEGATIVE_INFINITY) lowerBoundOffset = 0f
            if (upperBoundOffset == Float.POSITIVE_INFINITY) upperBoundOffset = 0f

            return lowerBoundOffset.rangeTo(upperBoundOffset)
        }
    }

fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float,
): Float {
    val contentSize =
        layoutInfo.singleAxisViewportSize - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

    // Align within the padded content area (SnapPosition.Start-style when positionInLayout returns 0).
    val desiredDistance =
        layoutInfo.beforeContentPadding +
            positionInLayout(contentSize.toFloat(), item.size.width.toFloat())
    val itemCurrentPosition = item.offset.x.toFloat()

    return itemCurrentPosition - desiredDistance
}

private val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width
