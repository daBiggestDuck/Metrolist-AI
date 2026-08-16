/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.metrolist.music.constants.UseFloatingNavigationBarKey
import com.metrolist.music.utils.rememberPreference
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val AuraNavUnselected = Color(0xFFB3B3B3)
private val AuraNavSelected = Color(0xFFFFFFFF)
/** Higher-contrast bubble so travel between tabs reads clearly. */
private val AuraNavIndicator = Color.White.copy(alpha = 0.72f)
private val AuraNavPillBg = AuraElevated

/**
 * Short, deterministic travel specs keep the selected bubble and page movement visible
 * without the long spring tail that used to get lost during heavy screen recomposition.
 */
val AuraTabTravelSpring =
    tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)

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

/** Returns the top-level tab represented by a route, including nested/search routes. */
fun navigationTabIndex(currentRoute: String?, navigationItems: List<Screens>): Int? =
    navigationItems
        .indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }
        .takeIf { it >= 0 }

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
    indicatorPosition: (() -> Float)? = null,
    onSearchLongClick: (() -> Unit)? = null
) {
    val containerColor = if (pureBlack) Color.Black else AuraNavPillBg
    val resolvedSelectedIndex = remember(currentRoute, navigationItems) {
        navigationTabIndex(currentRoute, navigationItems)
    }
    // Keep the indicator visible while entering nested routes or while NavController settles.
    val lastSelectedIndex = remember { mutableIntStateOf(0) }
    LaunchedEffect(resolvedSelectedIndex) {
        resolvedSelectedIndex?.let { lastSelectedIndex.intValue = it }
    }
    val selectedIndex = resolvedSelectedIndex ?: lastSelectedIndex.intValue
    val animatedIndex = remember { Animatable(selectedIndex.toFloat()) }
    if (indicatorPosition == null) {
        // Only the shared strip/bubble Animatable should drive travel; a second per-bar
        // spring here competed for frames on every tab switch.
        LaunchedEffect(selectedIndex) {
            animatedIndex.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = AuraTabTravelSpring,
            )
        }
    }
    val animatedPosition = indicatorPosition ?: { animatedIndex.value }

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
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                            .width(48.dp)
                            .height(itemHeight)
                            .padding(4.dp)
                            .graphicsLayer {
                                translationY = animatedPosition() * itemHeightPx
                            }
                            .clip(AuraFloatingPillShape)
                        .background(AuraNavIndicator),
            )
            Column(modifier = Modifier.fillMaxSize()) {
                navigationItems.forEachIndexed { index, screen ->
                    val isSelected = selectedIndex == index
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
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        // Track bubble travel — one spring drives icon scale (no per-tab springs).
                                        val proximity =
                                            (1f - abs(animatedPosition() - index)).coerceIn(0f, 1f)
                                        val scale = 1f + 0.10f * proximity
                                        scaleX = scale
                                        scaleY = scale
                                    },
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
    selectedIndexOverride: Int? = null,
    indicatorPosition: (() -> Float)? = null,
    onSearchLongClick: (() -> Unit)? = null
) {
    val containerColor = if (pureBlack) Color.Black else AuraNavPillBg
    val useFloatingNavigationBar by rememberPreference(UseFloatingNavigationBarKey, defaultValue = true)
    val pillHeight = if (slimNav) 52.dp else 56.dp
    val resolvedSelectedIndex = remember(currentRoute, navigationItems) {
        navigationTabIndex(currentRoute, navigationItems)
    }
    // Keep the indicator visible while entering nested routes or while NavController settles.
    val lastSelectedIndex = remember { mutableIntStateOf(0) }
    LaunchedEffect(resolvedSelectedIndex) {
        resolvedSelectedIndex?.let { lastSelectedIndex.intValue = it }
    }
    val selectedIndex = selectedIndexOverride ?: resolvedSelectedIndex ?: lastSelectedIndex.intValue
    val animatedIndex = remember { Animatable(selectedIndex.toFloat()) }
    if (indicatorPosition == null) {
        // Only the shared strip/bubble Animatable should drive travel; a second per-bar
        // spring here competed for frames on every tab switch.
        LaunchedEffect(selectedIndex) {
            animatedIndex.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = AuraTabTravelSpring,
            )
        }
    }
    val animatedPosition = indicatorPosition ?: { animatedIndex.value }

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
                    .then(
                        if (useFloatingNavigationBar) {
                            Modifier.auraFloatingIsland(
                                shape = AuraFloatingPillShape,
                                color = containerColor,
                                elevation = 0.dp,
                            )
                        } else {
                            Modifier.background(containerColor)
                        },
                    ),
        ) {
            val tabCount = navigationItems.size.coerceAtLeast(1)
            val tabWidth = maxWidth / tabCount
            val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }
            // Narrower bubble under the icon — clear sliding pill without washing the whole tab.
            val indicatorInset = 12.dp
            val indicatorWidth = (tabWidth - indicatorInset * 2).coerceAtLeast(36.dp)
            val indicatorOffsetPx = with(LocalDensity.current) { indicatorInset.toPx() }

            // Sliding indicator — read State.value only inside graphicsLayer (no tab recomposition).
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .padding(vertical = 7.dp)
                            .graphicsLayer {                                    translationX =
                                    indicatorOffsetPx + animatedPosition() * tabWidthPx
                            }
                            .clip(AuraFloatingPillShape)
                        .background(AuraNavIndicator),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationItems.forEachIndexed { index, screen ->
                    val isSelected = selectedIndex == index
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
                    val labelTint = if (isSelected) AuraNavSelected else AuraNavUnselected

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
                                        // Same spring as the bubble — icons light up as it travels.
                                        val proximity =
                                            (1f - abs(animatedPosition() - index)).coerceIn(0f, 1f)
                                        val scale = 1f + 0.12f * proximity
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
                                        val proximity =
                                            (1f - abs(animatedPosition() - index)).coerceIn(0f, 1f)
                                        alpha = 0.72f + 0.28f * proximity
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
