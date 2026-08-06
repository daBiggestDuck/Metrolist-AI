/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraFloatingPillShape
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import com.metrolist.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val AuraNavUnselected = Color(0xFFB3B3B3)
private val AuraNavSelected = Color(0xFFFFFFFF)
/** Higher-contrast bubble so travel between tabs reads clearly. */
private val AuraNavIndicator = Color.White.copy(alpha = 0.5f)
private val AuraNavPillBg = AuraElevated
/** Soft overshoot so the bubble visibly “bounces” into the selected icon. */
private val AuraNavIndicatorTravel =
    spring<Float>(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow,
    )

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

@Composable
private fun rememberNavItemInteraction(
    isSearchItem: Boolean,
    isSelected: Boolean,
    screen: Screens,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchLongClick: (() -> Unit)?,
): MutableInteractionSource {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val currentIsSelected by rememberUpdatedState(isSelected)
    val interactionSource = remember { MutableInteractionSource() }

    if (isSearchItem && onSearchLongClick != null) {
        LaunchedEffect(interactionSource) {
            var isLongClick = false
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        isLongClick = false
                        delay(viewConfiguration.longPressTimeoutMillis)
                        isLongClick = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearchLongClick.invoke()
                    }
                    is PressInteraction.Release -> {
                        if (!isLongClick) {
                            onItemClick(screen, currentIsSelected)
                        }
                    }
                    is PressInteraction.Cancel -> {
                        isLongClick = false
                    }
                }
            }
        }
    }

    return interactionSource
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null
) {
    val containerColor = if (pureBlack) Color.Black else AuraNavPillBg
    val selectedIndex =
        remember(currentRoute, navigationItems) {
            navigationItems
                .indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }
                .takeIf { it >= 0 }
        }
    // Keep State — do NOT use `by` or spring frames recompose every tab icon.
    val animatedIndex =
        animateFloatAsState(
            targetValue = (selectedIndex ?: 0).toFloat(),
            animationSpec = AuraNavIndicatorTravel,
            label = "auraRailIndicator",
        )
    val showIndicator = selectedIndex != null

    Column(
        modifier =
            modifier
                .width(72.dp)
                .fillMaxHeight()
                .padding(vertical = 16.dp, horizontal = 8.dp)
                // elevation 0: soft shadows redraw every sheet-slide frame via graphicsLayer.
                .auraFloatingIsland(
                    shape = AuraFloatingPillShape,
                    color = containerColor,
                    elevation = 0.dp,
                )
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            val tabCount = navigationItems.size.coerceAtLeast(1)
            val itemHeight = maxHeight / tabCount
            val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
            if (showIndicator) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .width(48.dp)
                            .height(itemHeight)
                            .padding(4.dp)
                            .graphicsLayer {
                                translationY = animatedIndex.value * itemHeightPx
                            }
                            .clip(AuraFloatingPillShape)
                            .background(AuraNavIndicator),
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                navigationItems.forEach { screen ->
                    val isSelected =
                        remember(currentRoute, screen.route) {
                            isRouteSelected(currentRoute, screen.route, navigationItems)
                        }
                    val iconRes =
                        remember(isSelected, screen) {
                            if (isSelected) screen.iconIdActive else screen.iconIdInactive
                        }
                    val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                    val interactionSource =
                        rememberNavItemInteraction(
                            isSearchItem = isSearchItem,
                            isSelected = isSelected,
                            screen = screen,
                            onItemClick = onItemClick,
                            onSearchLongClick = onSearchLongClick,
                        )
                    val tint = if (isSelected) AuraSpotifyGreen else AuraNavUnselected

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (!isSearchItem) {
                                            onItemClick(screen, isSelected)
                                        }
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = stringResource(screen.titleId),
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating Spotify+Aura pill bottom nav — elevated dark capsule with hairline outline and a
 * sliding selection indicator. Not a flat Material NavigationBar.
 */
@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null
) {
    val containerColor = if (pureBlack) Color.Black else AuraNavPillBg
    val pillHeight = if (slimNav) 52.dp else 56.dp
    val selectedIndex =
        remember(currentRoute, navigationItems) {
            navigationItems
                .indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }
                .takeIf { it >= 0 }
        }
    // Keep State — reading `by animateFloatAsState` in composition recomposes all tabs every spring frame.
    val animatedIndex =
        animateFloatAsState(
            targetValue = (selectedIndex ?: 0).toFloat(),
            animationSpec = AuraNavIndicatorTravel,
            label = "auraNavIndicator",
        )
    val showIndicator = selectedIndex != null

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // Tight vertical inset so the pill docks under the mini-player (Spotify gap).
                .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(pillHeight)
                    // elevation 0: pill translates with the player sheet every frame — soft
                    // shadows here were a major overdraw source during expand/collapse.
                    .auraFloatingIsland(
                        shape = AuraFloatingPillShape,
                        color = containerColor,
                        elevation = 0.dp,
                    ),
        ) {
            val tabCount = navigationItems.size.coerceAtLeast(1)
            val tabWidth = maxWidth / tabCount
            val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }
            // Narrower bubble under the icon reads as a clear sliding pill, not a full-tab wash.
            val indicatorInset = 10.dp
            val indicatorWidth = (tabWidth - indicatorInset * 2).coerceAtLeast(36.dp)
            val indicatorOffsetPx = with(LocalDensity.current) { indicatorInset.toPx() }

            // Sliding indicator — read State.value only inside graphicsLayer (no tab recomposition).
            if (showIndicator) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .padding(vertical = 5.dp)
                            .graphicsLayer {
                                translationX =
                                    indicatorOffsetPx + animatedIndex.value * tabWidthPx
                            }
                            .clip(AuraFloatingPillShape)
                            .background(AuraNavIndicator),
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationItems.forEach { screen ->
                    val isSelected =
                        remember(currentRoute, screen.route) {
                            isRouteSelected(currentRoute, screen.route, navigationItems)
                        }
                    val iconRes =
                        remember(isSelected, screen) {
                            if (isSelected) screen.iconIdActive else screen.iconIdInactive
                        }
                    val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                    val interactionSource =
                        rememberNavItemInteraction(
                            isSearchItem = isSearchItem,
                            isSelected = isSelected,
                            screen = screen,
                            onItemClick = onItemClick,
                            onSearchLongClick = onSearchLongClick,
                        )
                    val iconTint = if (isSelected) AuraNavSelected else AuraNavUnselected
                    val labelTint = if (isSelected) AuraSpotifyGreen else AuraNavUnselected
                    val selectedProgress =
                        animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.62f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            label = "auraNavTabSelect",
                        )

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (!isSearchItem) {
                                            onItemClick(screen, isSelected)
                                        }
                                    },
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = stringResource(screen.titleId),
                            tint = iconTint,
                            modifier =
                                Modifier
                                    .size(if (slimNav) 22.dp else 24.dp)
                                    .graphicsLayer {
                                        val p = selectedProgress.value
                                        val scale = 1f + 0.12f * p
                                        scaleX = scale
                                        scaleY = scale
                                    },
                        )
                        if (!slimNav) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(screen.titleId),
                                color = labelTint,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 12.sp,
                                modifier =
                                    Modifier.graphicsLayer {
                                        alpha = 0.72f + 0.28f * selectedProgress.value
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
