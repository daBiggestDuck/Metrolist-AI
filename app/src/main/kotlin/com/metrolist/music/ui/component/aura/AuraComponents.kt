/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.aura

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.ui.component.IconButton

/** Explicit Spotify green — stays brand-like even when theme primary shifts in light mode. */
val AuraSpotifyGreen = Color(0xFF1DB954)
val AuraSpotifyOnGreen = Color(0xFF000000)
/** Dark circular control fill (#282828) — secondary FABs / mic, not M3 tonal purple. */
val AuraSpotifyDark = Color(0xFF282828)
val AuraSpotifyOnDark = Color.White
private val AuraNearBlack = Color(0xFF121212)
private val AuraDividerColor = Color(0xFF282828)
private val AuraHeroTop = Color(0xFF1A3A28)
private val AuraHeroBottom = Color(0xFF121212)
private val AuraMutedPill = AuraSpotifyDark
private val AuraHeroMutedText = Color(0xFFB3B3B3)
private val AuraPillShape = RoundedCornerShape(percent = 50)
private val AuraBannerShape = RoundedCornerShape(8.dp)
private val AuraHeroShape = RoundedCornerShape(12.dp)
private val AuraButtonContentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
private val AuraSecondaryContentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
val AuraHeroBrush = Brush.verticalGradient(listOf(AuraHeroTop, AuraHeroBottom))

/**
 * Default Aura top chrome is optically transparent — islands float over the black canvas.
 * Call sites that previously painted a glued #181818 bar get floating organization instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val AuraDefaultTopBarColors =
    TopAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = Color.White,
        titleContentColor = Color.White,
        actionIconContentColor = Color.White,
    )

/**
 * Full-screen near-black column with top inset + optional scroll. Replaces M3 Scaffold chrome
 * for Spotify / Metro DJ surfaces.
 */
@Composable
fun AuraScreen(
    screenModifier: Modifier = Modifier,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Settings and integration routes must remain opaque while nested navigation transitions
    // expose the destination below them. Keep custom/light themes intact, but never allow a
    // transparent or unspecified theme token to reveal the screen underneath.
    val themeBackground = MaterialTheme.colorScheme.background
    val bg =
        themeBackground.takeIf {
            it != Color.Unspecified && it.alpha > 0f
        } ?: AuraPlayerCanvas
    Column(
        screenModifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .then(
                if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
            )
            .padding(contentPadding),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )
        content()
    }
}

/**
 * Large title + optional subtitle with a plain back icon — no TopAppBar elevation / surface.
 */
@Composable
fun AuraHeader(
    title: String,
    onBack: () -> Unit,
    onBackLongClick: (() -> Unit)? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        IconButton(
            onClick = onBack,
            onLongClick = onBackLongClick ?: {},
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = if (subtitle == null) 16.dp else 4.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }
}

/**
 * Floating Aura chrome — replaces Material3 TopAppBar organization.
 *
 * Layout language: detached islands over a transparent strip (no glued full-width bar).
 * Left: optional nav control + title/greeting pill. Right: action circles with air between.
 * Honors [scrollBehavior] collapse via graphicsLayer. Opaque [colors.containerColor] is ignored
 * for fill — islands carry their own #181818 / #282828 surfaces.
 *
 * Set [floatTitle] false when [title] is already its own island (e.g. Search field pill).
 *
 * Perf: do not read heightOffset / overlappedFraction in composition — only in graphicsLayer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraTopBar(
    title: @Composable () -> Unit,
    searchKey: String? = null,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = AppBarHeight,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    colors: TopAppBarColors = AuraDefaultTopBarColors,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    floatTitle: Boolean = true,
) {
    val density = LocalDensity.current
    val expandedHeightPx = with(density) { expandedHeight.toPx() }
    if (scrollBehavior != null) {
        SideEffect {
            if (scrollBehavior.state.heightOffsetLimit != -expandedHeightPx) {
                scrollBehavior.state.heightOffsetLimit = -expandedHeightPx
            }
        }
    }

    // Read heightOffset only inside graphicsLayer so scroll invalidates the layer, not composition.
    val collapseModifier =
        if (scrollBehavior != null && !scrollBehavior.isPinned) {
            Modifier.graphicsLayer {
                translationY = scrollBehavior.state.heightOffset
            }
        } else {
            Modifier
        }

    Column(
        modifier
            .fillMaxWidth()
            .then(collapseModifier)
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(expandedHeight)
                    .padding(horizontal = AuraFloatingEdgeInset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuraFloatingIslandGap),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.navigationIconContentColor) {
                navigationIcon()
            }
            if (floatTitle) {
                // Hug title text (cap width so long artist names ellipsize); spacer keeps actions trailing.
                AuraFloatingTitleIsland(
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides colors.titleContentColor) {
                        title()
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CompositionLocalProvider(LocalContentColor provides colors.titleContentColor) {
                        title()
                    }
                }
            }
            CompositionLocalProvider(LocalContentColor provides colors.actionIconContentColor) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuraFloatingIslandGap),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/**
 * Selectable Spotify-style floating filter chip — white when selected, dark island otherwise.
 */
@Composable
fun AuraFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Painter? = null,
) {
    val bg = if (selected) Color.White else AuraMutedPill
    val fg =
        if (selected) {
            Color.Black
        } else {
            Color.White
        }
    val border = if (selected) Color.Transparent else AuraHairline
    Row(
        modifier
            .height(28.dp)
            .clip(AuraPillShape)
            .background(bg)
            .then(
                if (!selected) {
                    Modifier.border(1.dp, border, AuraPillShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small muted caps / letter-spaced section label. */
@Composable
fun AuraSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/**
 * Flat list row: title, optional subtitle, optional trailing (chevron / green text / custom).
 */
@Composable
fun AuraRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    searchKey: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    trailingText: String? = null,
    trailingTextColor: Color = AuraSpotifyGreen,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable =
        if (onClick != null && enabled) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    Row(
        modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            trailingContent != null -> trailingContent()
            trailingText != null -> {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) trailingTextColor else trailingTextColor.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            showChevron -> {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .size(18.dp),
                )
            }
        }
    }
}

/** Full-width #1DB954 pill CTA with black text. */
@Composable
fun AuraPrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AuraPrimaryButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) AuraSpotifyOnGreen else AuraSpotifyOnGreen.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Flat green pill — drop-in visual replacement for Material3 [androidx.compose.material3.Button].
 * No elevation / tonal overlay.
 */
@Composable
fun AuraPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AuraPillShape,
    containerColor: Color = AuraSpotifyGreen,
    contentColor: Color = AuraSpotifyOnGreen,
    contentPadding: PaddingValues = AuraButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val indication = remember { ripple() }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                interactionSource = resolvedInteractionSource,
                indication = indication,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = {
            val fg = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            CompositionLocalProvider(LocalContentColor provides fg) {
                content()
            }
        },
    )
}

/**
 * Outlined / ghost pill — replaces Material3 [androidx.compose.material3.OutlinedButton].
 */
@Composable
fun AuraOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AuraPillShape,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    borderColor: Color = AuraHairline,
    contentPadding: PaddingValues = AuraButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val indication = remember { ripple() }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                interactionSource = resolvedInteractionSource,
                indication = indication,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = {
            val fg = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            CompositionLocalProvider(LocalContentColor provides fg) {
                content()
            }
        },
    )
}

/**
 * Dark muted pill — replaces Material3 [androidx.compose.material3.FilledTonalButton].
 */
@Composable
fun AuraTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AuraPillShape,
    containerColor: Color = AuraSpotifyDark,
    contentColor: Color = AuraSpotifyOnDark,
    contentPadding: PaddingValues = AuraButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    AuraPrimaryButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Muted / white text action — pure clickable text, no Material [TextButton] chrome. */
@Composable
fun AuraSecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AuraSecondaryAction(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Content-slot secondary action — drop-in for Material3 [androidx.compose.material3.TextButton]
 * confirm/dismiss slots. Flat text/box, zero M3 button chrome.
 */
@Composable
fun AuraSecondaryAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = AuraSecondaryContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val indication = remember { ripple(bounded = true) }
    Box(
        modifier
            .clip(AuraPillShape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
                interactionSource = resolvedInteractionSource,
                indication = indication,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        val fg =
            if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        CompositionLocalProvider(LocalContentColor provides fg) {
            content()
        }
    }
}

/**
 * Flat filled icon control — replaces Material3 [FilledIconButton] / [OutlinedIconButton].
 * Supports pill shapes and weighted modifiers for player transport.
 */
@Composable
fun AuraIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    containerColor: Color = AuraSpotifyDark,
    contentColor: Color = AuraSpotifyOnDark,
    borderColor: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val indication = remember { ripple() }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(if (enabled) containerColor else containerColor.copy(alpha = 0.35f))
                .then(
                    if (borderColor != null) {
                        Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                    role = Role.Button,
                    interactionSource = resolvedInteractionSource,
                    indication = indication,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
        CompositionLocalProvider(LocalContentColor provides fg) {
            content()
        }
    }
}

/**
 * Extended FAB replacement: green (or custom) pill with optional leading icon + label.
 * Zero elevation — not Material [ExtendedFloatingActionButton].
 */
@Composable
fun AuraExtendedFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AuraSpotifyGreen,
    contentColor: Color = AuraSpotifyOnGreen,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .heightIn(min = 56.dp)
            .widthIn(min = 80.dp)
            .clip(AuraPillShape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (icon != null) icon()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AuraDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AuraDividerColor),
    )
}

/**
 * Dark gradient hero with title, subtitle, and a green circular play CTA.
 * Used for Metro DJ entry points.
 */
@Composable
fun AuraHeroPanel(
    title: String,
    subtitle: String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    playContentDescription: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AuraHeroShape)
            .background(AuraHeroBrush)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AuraHeroMutedText,
                maxLines = 4,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) AuraSpotifyGreen else AuraSpotifyGreen.copy(alpha = 0.35f),
                    )
                    .clickable(enabled = enabled, onClick = onPlayClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = playContentDescription,
                tint = AuraSpotifyOnGreen,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Muted disclaimer / info banner — flat box, not a Material Card. */
@Composable
fun AuraBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(AuraBannerShape)
            .background(AuraMutedPill)
            .padding(14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small muted text pill (for taste hints) — not SuggestionChip. */
@Composable
fun AuraHintPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(AuraPillShape)
            .background(AuraMutedPill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Circular avatar placeholder: colored circle + initial. */
@Composable
fun AuraArtistAvatar(
    name: String,
    modifier: Modifier = Modifier,
    color: Color = AuraSpotifyGreen,
) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Column(
        modifier = modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Flat circular control — no Material FAB elevation/shadow/tonal chrome.
 * Prefer [AuraFab] for primary green actions; use this for dark secondary controls (mic, etc.).
 */
@Composable
fun AuraCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    containerColor: Color = AuraSpotifyDark,
    contentColor: Color = AuraSpotifyOnDark,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
        } else {
            Modifier.semantics { role = Role.Button }
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (enabled) containerColor else containerColor.copy(alpha = 0.35f),
                )
                .then(semanticsModifier)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * Spotify-style primary FAB: solid green circle, black icon, zero elevation.
 * Drop-in visual replacement for Material3 [androidx.compose.material3.FloatingActionButton].
 */
@Composable
fun AuraFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    containerColor: Color = AuraSpotifyGreen,
    contentColor: Color = AuraSpotifyOnGreen,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    AuraCircleButton(
        onClick = onClick,
        modifier = modifier,
        size = size,
        containerColor = containerColor,
        contentColor = contentColor,
        enabled = enabled,
        contentDescription = contentDescription,
        content = content,
    )
}

/** Numbered playlist-style track row. */
@Composable
fun AuraTrackRow(
    index: Int,
    title: String,
    artist: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.isNullOrBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Full-player canvas — Spotify near-black (#121212). */
val AuraPlayerCanvas = AuraNearBlack

/** Mini-player / queue elevated chrome (#282828). */
val AuraPlayerChrome = AuraSpotifyDark

/** Flat icon-only transport — transparent fill, not Material IconButton. */
@Composable
fun AuraTransportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    AuraIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        enabled = enabled,
        containerColor = Color.Transparent,
        contentColor = tint,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Spotify-style green play/pause circle. */
@Composable
fun AuraPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    AuraFab(
        onClick = onClick,
        modifier = modifier,
        size = size,
        containerColor = AuraSpotifyGreen,
        contentColor = AuraSpotifyOnGreen,
        enabled = enabled,
        contentDescription = contentDescription,
        content = content,
    )
}
