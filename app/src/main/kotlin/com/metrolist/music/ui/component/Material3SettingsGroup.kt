/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraHairline
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.auraHairlineBorder
import com.metrolist.music.ui.screens.settings.rememberActiveSettingsHighlightId
import com.metrolist.music.ui.screens.settings.settingsSearchAnchor
import com.metrolist.music.ui.screens.settings.settingsSearchHighlightColor

private val AuraDividerColor = AuraHairline

/**
 * Spotify-style settings group — elevated #181818 island, uppercase section label, hairline rows.
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    searchKey: String? = null,
    items: List<Material3SettingsItem>,
    useLowContrast: Boolean = false,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredContrast = useLowContrast

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        title?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, top = 4.dp, start = 4.dp),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AuraElevated)
                    .auraHairlineBorder(RoundedCornerShape(12.dp))
                    .animateContentSize()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            items.forEachIndexed { index, item ->
                Material3SettingsItemRow(item = item)
                if (index < items.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AuraDividerColor),
                    )
                }
            }
        }
    }
}

/**
 * Individual settings item row with Aura flat styling.
 */
@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
) {
    val activeHighlight = rememberActiveSettingsHighlightId()
    val searchHighlighted =
        !item.searchKey.isNullOrBlank() && item.searchKey == activeHighlight
    val highlighted = item.isHighlighted || searchHighlighted
    val highlightBg = settingsSearchHighlightColor(item.searchKey)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .settingsSearchAnchor(item.searchKey)
                .background(highlightBg, RoundedCornerShape(8.dp))
                .clickable(
                    enabled = item.enabled && item.onClick != null,
                    onClick = { item.onClick?.invoke() },
                )
                .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(modifier = Modifier.width(16.dp))
        } else if (item.icon != null) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (highlighted) {
                                AuraSpotifyGreen.copy(alpha = 0.2f)
                            } else {
                                AuraDividerColor
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showBadge) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                            )
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint =
                                if (!item.enabled) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else if (highlighted) {
                                    AuraSpotifyGreen
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint =
                            if (!item.enabled) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else if (highlighted) {
                                AuraSpotifyGreen
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
        ) {
            ProvideTextStyle(
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (!item.enabled) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                ),
            ) {
                item.title()
            }

            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        color =
                            if (!item.enabled) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    ),
                ) {
                    desc()
                }
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Data class for settings item (kept name for call-site compatibility).
 */
data class Material3SettingsItem(
    val icon: Painter? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    /** Matches [com.metrolist.music.ui.screens.settings.SettingsSearchEntry.id] for search highlight. */
    val searchKey: String? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)
