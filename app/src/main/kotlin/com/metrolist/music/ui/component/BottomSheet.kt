/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.NavigationBarAnimationSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 *
 * Perf: continuous sheet position is only read inside [graphicsLayer] / [snapshotFlow].
 * Discrete [anchor] drives MainActivity insets and BackHandler so drag frames do not
 * recompose the scaffold / player tree.
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    background: @Composable (BoxScope.() -> Unit) = { },
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    isExpandable: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    // Threshold visibility — updates only when crossing, not every drag pixel.
    var showExpandedContent by remember(state) {
        mutableStateOf(state.progressValue() > 0.02f)
    }
    var showCollapsedContent by remember(state) {
        mutableStateOf(state.progressValue() < 0.98f && !state.isDismissed)
    }

    LaunchedEffect(state) {
        snapshotFlow { state.progressValue() }
            .map { progress ->
                val showExpanded = progress > 0.02f
                val showCollapsed = progress < 0.98f && state.anchor != dismissedAnchor
                showExpanded to showCollapsed
            }
            .distinctUntilChanged()
            .collect { (expanded, collapsed) ->
                showExpandedContent = expanded
                showCollapsedContent = collapsed
            }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                // background fades during about 10%-61% progress
                alpha = (1.4f * (state.progressValue().coerceAtLeast(0.1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)
            }
            .fillMaxSize(),
        content = background
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            // Use graphicsLayer for offset to ensure hardware acceleration and 120Hz support
            .graphicsLayer {
                val y = (state.expandedBound - state.valueDp())
                    .toPx()
                    .coerceAtLeast(0f)
                translationY = y
            }
            .pointerInput(state, isExpandable) {
                if (!isExpandable) return@pointerInput
                val velocityTracker = VelocityTracker()

                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        velocityTracker.addPointerInputChange(change)
                        state.dispatchRawDelta(dragAmount)
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        state.snapTo(state.collapsedBound)
                    },
                    onDragEnd = {
                        val velocity = -velocityTracker.calculateVelocity().y
                        velocityTracker.resetTracking()
                        state.performFling(velocity, onDismiss)
                    }
                )
            }
            .graphicsLayer {
                val cornerRadius = if (state.progressValue() < 0.99f) 16.dp.toPx() else 0f
                shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                clip = true
            }
    ) {
        if (showExpandedContent && !state.isDismissed) {
            PredictiveBackHandler { progress ->
                val initialValue = state.valueDp()
                try {
                    val range = initialValue - state.collapsedBound
                    progress.collect { event ->
                        state.snapToAndWait(
                            initialValue - range * event.progress.coerceIn(0f, 1f)
                        )
                    }
                    state.collapseSoft()
                } catch (_: CancellationException) {
                    state.expandSoft()
                }
            }
        }

        // main content — mounted only while sheet is meaningfully open (not every drag frame)
        if (showExpandedContent) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ((state.progressValue() - 0.15f) * 4).coerceIn(0f, 1f)
                    },
                content = content
            )
        }

        if (showCollapsedContent && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                Modifier
                    .graphicsLayer {
                        alpha = 1f - (state.progressValue() * 4).coerceAtMost(1f)
                    }.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isExpandable) state.expandSoft() },
                    )
                    .focusable(false)
                    .fillMaxWidth()
                    .height(state.collapsedBound),
                content = collapsedContent,
            )
        }
    }
}

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
    initialAnchor: Int,
) : DraggableState by draggableState {
    /**
     * Discrete settled/target anchor. Composition (insets, BackHandler) should read this —
     * never the continuous animatable position — so drag frames stay cheap.
     */
    var anchor by mutableIntStateOf(initialAnchor)
        private set

    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    /** Compose State for continuous position — read only inside graphicsLayer / snapshotFlow. */
    private val valueState = animatable.asState()

    val isDismissed: Boolean
        get() = anchor == dismissedAnchor

    val isCollapsed: Boolean
        get() = anchor == collapsedAnchor

    val isExpanded: Boolean
        get() = anchor == expandedAnchor

    /** Continuous progress 0..1 for draw / snapshotFlow (not for scaffold composition). */
    fun progressValue(): Float {
        val upper = animatable.upperBound ?: return 0f
        val current = valueState.value
        val spanPx = upper.value - collapsedBound.value
        if (spanPx == 0f) return if (current >= upper) 1f else 0f
        return (1f - (upper.value - current.value) / spanPx).coerceIn(0f, 1f)
    }

    /** Continuous sheet Y for draw only. */
    fun valueDp(): Dp = valueState.value

    /** @deprecated Use [progressValue] inside graphicsLayer. Kept for call-site compatibility. */
    val progress: Float
        get() = progressValue()

    /** @deprecated Use [valueDp] inside graphicsLayer. */
    val value: Dp
        get() = valueDp()

    private fun updateAnchor(newAnchor: Int) {
        if (anchor != newAnchor) {
            anchor = newAnchor
            onAnchorChanged(newAnchor)
        }
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        // Mark the sheet collapsed before the motion starts so the mini-player can mount
        // immediately instead of waiting for the full travel animation to finish.
        updateAnchor(collapsedAnchor)
        coroutineScope.launch {
            animatable.animateTo(collapsedBound, animationSpec)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        // Show expanded chrome immediately; animate sheet up.
        updateAnchor(expandedAnchor)
        coroutineScope.launch {
            animatable.animateTo(animatable.upperBound!!, animationSpec)
        }
    }

    private fun collapse() {
        collapse(SpringSpec())
    }

    private fun expand() {
        expand(SpringSpec())
    }

    fun collapseSoft() {
        collapse(spring(stiffness = Spring.StiffnessMediumLow))
    }

    fun expandSoft() {
        expand(spring(stiffness = Spring.StiffnessMediumLow))
    }

    fun dismiss() {
        coroutineScope.launch {
            animatable.animateTo(animatable.lowerBound!!)
            updateAnchor(dismissedAnchor)
        }
    }

    suspend fun dismissAndWait() {
        animatable.animateTo(animatable.lowerBound!!)
        updateAnchor(dismissedAnchor)
    }

    fun snapTo(value: Dp) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value)
        }
    }

    suspend fun snapToAndWait(value: Dp) {
        animatable.snapTo(value)
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
        val current = valueDp()
        if (velocity > 250) {
            expand()
        } else if (velocity < -250) {
            if (current < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse()
            }
        } else {
            val l0 = dismissedBound
            val l1 = (collapsedBound - dismissedBound) / 2
            val l2 = (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (current) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse()
                    }
                }

                in l1..l2 -> collapse()
                in l2..l3 -> expand()
                else -> Unit
            }
        }
    }

    // Stable instance — recreating per access restarted nested-scroll every recomposition.
    val preUpPostDownNestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            var isTopReached = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isExpanded && available.y < 0) {
                    isTopReached = false
                }

                return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isTopReached) {
                    isTopReached = consumed.y == 0f && available.y > 0
                }

                return if (isTopReached && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (isTopReached) {
                    val velocity = -available.y
                    performFling(velocity, null)

                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isTopReached = false
                return Velocity.Zero
            }
        }
}

const val expandedAnchor = 2
const val collapsedAnchor = 1
const val dismissedAnchor = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = dismissedAnchor,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable = remember {
        Animatable(0.dp, Dp.VectorConverter)
    }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
        val initialValue = when (previousAnchor) {
            expandedAnchor -> expandedBound
            collapsedAnchor -> collapsedBound
            dismissedAnchor -> dismissedBound
            else -> error("Unknown BottomSheet anchor")
        }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch {
            animatable.animateTo(initialValue, NavigationBarAnimationSpec)
        }

        BottomSheetState(
            draggableState = DraggableState { delta ->
                // UNDISPATCHED: apply snap on this frame instead of scheduling a job per pixel.
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                }
            },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound,
            initialAnchor = previousAnchor,
        )
    }
}
