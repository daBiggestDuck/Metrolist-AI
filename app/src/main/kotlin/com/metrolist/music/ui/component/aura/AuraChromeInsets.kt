/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.aura

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalPlayerAwareWindowInsets

/**
 * Top padding matching Aura top chrome (status bar + app bar).
 * Use on sticky / floating filter rows so they never sit under the status bar.
 */
@Composable
fun Modifier.auraBelowTopChrome(): Modifier {
    val top =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Top)
            .asPaddingValues()
            .calculateTopPadding()
    return this.padding(top = top)
}

/** Content padding for lists whose first sticky/header already applies [auraBelowTopChrome]. */
@Composable
fun auraContentPaddingBelowChrome(): PaddingValues {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = insets.calculateLeftPadding(layoutDirection),
        end = insets.calculateRightPadding(layoutDirection),
        bottom = insets.calculateBottomPadding(),
        top = 0.dp,
    )
}

@Composable
fun Modifier.auraStickyChromeBackground(color: Color = AuraPlayerCanvas): Modifier =
    this.background(color).auraBelowTopChrome()
