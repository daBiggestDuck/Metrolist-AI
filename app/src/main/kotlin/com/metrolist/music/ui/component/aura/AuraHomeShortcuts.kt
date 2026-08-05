/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.aura

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.metrolist.music.ui.utils.resize

/** Spotify Home shortcut tile — flat dark chip, no Material elevation/shadow. */
private val AuraShortcutBg = Color(0xFF2A2A2A)
private val AuraShortcutShape = RoundedCornerShape(4.dp)
private val AuraShortcutHeight = 48.dp
private val AuraShortcutThumb = 48.dp
private val AuraShortcutGap = 8.dp

/**
 * Lightweight Spotify-style Home shortcut cell (thumb + title).
 * Prefer over Material cards / soft-shadow grid items on the scroll path.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraShortcutTile(
    title: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    circularThumbnail: Boolean = false,
) {
    val context = LocalContext.current
    val thumbShape = if (circularThumbnail) CircleShape else RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
    val request =
        remember(thumbnailUrl) {
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl?.resize(112, 112))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        }

    Row(
        modifier =
            modifier
                .height(AuraShortcutHeight)
                .clip(AuraShortcutShape)
                .background(AuraShortcutBg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(AuraShortcutThumb)
                    .clip(thumbShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Fixed Spotify Home 4×2 shortcut grid (8 tiles, no pager, no section title).
 */
@Composable
fun AuraHomeShortcutGrid(
    items: List<AuraShortcutItem>,
    modifier: Modifier = Modifier,
) {
    val columns = 4
    val rows = remember(items) { items.chunked(columns) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(AuraShortcutGap),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AuraShortcutGap),
            ) {
                rowItems.forEach { item ->
                    AuraShortcutTile(
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        circularThumbnail = item.circularThumbnail,
                        onClick = item.onClick,
                        onLongClick = item.onLongClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep column alignment when a row has fewer than [columns] tiles.
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Stable callback payload for [AuraHomeShortcutGrid] — avoids capturing heavy lambdas in keys. */
data class AuraShortcutItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val circularThumbnail: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)
