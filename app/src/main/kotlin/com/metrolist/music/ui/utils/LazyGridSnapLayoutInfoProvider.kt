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

/**
 * Snap provider for horizontal LazyGrids (e.g. Quick Picks).
 *
 * Snaps per column to the content start (or a custom [positionInLayout] offset within
 * the padded content area). Always settles on the nearest column when fling velocity is low,
 * so slow drags do not stop mid-card.
 */
@ExperimentalFoundationApi
fun SnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { _, _ -> 0f },
    velocityThreshold: Float = 1000f,
): SnapLayoutInfoProvider = object : SnapLayoutInfoProvider {
    private val layoutInfo: LazyGridLayoutInfo
        get() = lazyGridState.layoutInfo

    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

    override fun calculateSnapOffset(velocity: Float): Float {
        val bounds = calculateSnappingOffsetBounds()

        // Low / zero velocity: always settle on the nearest snap point (no free-scroll mid-card).
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

        // Multi-row horizontal grids share the same x for each column — dedupe by offset.
        val seenOffsets = HashSet<Int>()
        layoutInfo.visibleItemsInfo.fastForEach { item ->
            if (!seenOffsets.add(item.offset.x)) return@fastForEach

            val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

            if (offset <= 0 && offset > lowerBoundOffset) {
                lowerBoundOffset = offset
            }

            if (offset >= 0 && offset < upperBoundOffset) {
                upperBoundOffset = offset
            }
        }

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
