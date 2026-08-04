/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.aura

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spotify card surface (#181818). */
val AuraElevated = Color(0xFF181818)

/** Interactive / chrome elevated (#282828) — already mirrored by [AuraSpotifyDark]. */
val AuraHighlight = AuraSpotifyDark

/**
 * Supabase-like hairline — 1px white at ~10% opacity on dark surfaces.
 * Prefer over Material outline tokens for Aura chrome.
 */
val AuraHairline = Color.White.copy(alpha = 0.10f)

/** Slightly stronger hairline for focused / selected floating controls. */
val AuraHairlineStrong = Color.White.copy(alpha = 0.16f)

/** Dimmed scrim for fingerprint-style bottom sheets. */
val AuraSheetScrim = Color.Black.copy(alpha = 0.55f)

/** Rounded top sheet — native Android biometric / system sheet vibe. */
val AuraSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

val AuraFloatingPillShape = RoundedCornerShape(percent = 50)

private val AuraSheetContainer = AuraElevated
private val AuraDragHandleColor = Color.White.copy(alpha = 0.28f)

/** Thin 1px outline on any shape — Supabase dark UI language. */
fun Modifier.auraHairlineBorder(
    shape: Shape,
    color: Color = AuraHairline,
    width: Dp = 1.dp,
): Modifier = border(width = width, color = color, shape = shape)

/**
 * Native-Android fingerprint sheet vibe: rounded top, dimmed scrim, elevated dark fill.
 * Prefer over centered Material dialogs for menus and panels.
 *
 * Perf: does not observe sheet progress in composition — ModalBottomSheet owns animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = AuraSheetContainer,
    scrimColor: Color = AuraSheetScrim,
    shape: Shape = AuraSheetShape,
    dragHandle: @Composable (() -> Unit)? = { AuraSheetDragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = {
        androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier =
            modifier.border(
                width = 1.dp,
                color = AuraHairline,
                shape = shape,
            ),
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            content = content,
        )
    }
}

@Composable
fun AuraSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(top = 10.dp, bottom = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AuraDragHandleColor),
    )
}

/**
 * Floating top-right action cluster — circular dark chips with hairline borders,
 * spaced like Spotify profile chrome but lifted off the flat app bar.
 */
@Composable
fun AuraFloatingActionCluster(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.padding(end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Single floating circular chrome control (history / stats / account). */
@Composable
fun AuraFloatingChromeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = AuraHighlight,
    contentColor: Color = Color.White,
    borderColor: Color = AuraHairline,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    AuraIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor,
        content = {
            if (contentDescription != null) {
                Box(
                    modifier =
                        Modifier.semantics {
                            this.contentDescription = contentDescription
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            } else {
                content()
            }
        },
    )
}

/** Remembered brush-safe outline color for lists / pills — avoids realloc in scroll. */
@Composable
fun rememberAuraHairline(): Color = remember { AuraHairline }
