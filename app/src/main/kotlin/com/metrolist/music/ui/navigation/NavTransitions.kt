/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val NESTED_ENTER_MS = 300
private const val NESTED_EXIT_MS = 280
private const val NESTED_FADE_MS = 180

fun AnimatedContentTransitionScope<*>.nestedScreenEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(NESTED_ENTER_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> fullWidth },
    ) + fadeIn(tween(NESTED_FADE_MS))

fun AnimatedContentTransitionScope<*>.nestedScreenExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(NESTED_EXIT_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> -fullWidth / 3 },
    ) + fadeOut(tween(NESTED_FADE_MS))

fun AnimatedContentTransitionScope<*>.nestedScreenPopEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(NESTED_ENTER_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> -fullWidth / 3 },
    ) + fadeIn(tween(NESTED_FADE_MS))

fun AnimatedContentTransitionScope<*>.nestedScreenPopExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(NESTED_EXIT_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth -> fullWidth },
    ) + fadeOut(tween(NESTED_FADE_MS))
