/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

/** Tighter corners than M3 expressive defaults — softens cards under Aura. */
private val AuraShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

/** Spotify green — default Aura seed (not wallpaper / M3 purple). */
val DefaultThemeColor = Color(0xFF1DB954)

/** Pre-Aura Metrolist default; treat as Aura so upgrades keep the new skin. */
private val LegacyDefaultThemeColor = Color(0xFFED5564)

fun Color.isAuraDefaultSeed(): Boolean =
    this == DefaultThemeColor || this == LegacyDefaultThemeColor

private val AuraBackground = Color(0xFF121212)
private val AuraSurface = Color(0xFF181818)
private val AuraSurfaceBright = Color(0xFF282828)
private val AuraSurfaceDim = Color(0xFF0A0A0A)
private val AuraOnSurface = Color(0xFFFFFFFF)
private val AuraMuted = Color(0xFFB3B3B3)
private val AuraOutline = Color(0xFF535353)
private val AuraPrimary = Color(0xFF1DB954)
private val AuraOnPrimary = Color(0xFF000000)
private val AuraError = Color(0xFFE91429)

/**
 * Handcrafted Spotify-inspired dark scheme — near-black canvas, green accent, white/muted text.
 * Avoids Material 3 tonal purple from wallpaper dynamic color.
 */
fun auraDarkColorScheme(
    pureBlack: Boolean = false,
): ColorScheme {
    val background = if (pureBlack) Color.Black else AuraBackground
    val surface = if (pureBlack) Color.Black else AuraSurface
    return darkColorScheme(
        primary = AuraPrimary,
        onPrimary = AuraOnPrimary,
        primaryContainer = Color(0xFF0E7A37),
        onPrimaryContainer = AuraOnSurface,
        secondary = AuraMuted,
        onSecondary = AuraOnPrimary,
        secondaryContainer = AuraSurfaceBright,
        onSecondaryContainer = AuraOnSurface,
        tertiary = AuraPrimary,
        onTertiary = AuraOnPrimary,
        tertiaryContainer = AuraSurfaceBright,
        onTertiaryContainer = AuraOnSurface,
        background = background,
        onBackground = AuraOnSurface,
        surface = surface,
        onSurface = AuraOnSurface,
        surfaceVariant = AuraSurfaceBright,
        onSurfaceVariant = AuraMuted,
        surfaceTint = AuraPrimary,
        inverseSurface = AuraOnSurface,
        inverseOnSurface = AuraBackground,
        inversePrimary = Color(0xFF0E7A37),
        outline = AuraOutline,
        outlineVariant = Color(0xFF3E3E3E),
        scrim = Color.Black,
        surfaceBright = AuraSurfaceBright,
        surfaceContainer = AuraSurfaceBright,
        surfaceContainerHigh = Color(0xFF2A2A2A),
        surfaceContainerHighest = Color(0xFF333333),
        surfaceContainerLow = if (pureBlack) Color.Black else Color(0xFF161616),
        surfaceContainerLowest = if (pureBlack) Color.Black else AuraSurfaceDim,
        surfaceDim = AuraSurfaceDim,
        error = AuraError,
        onError = AuraOnSurface,
        errorContainer = Color(0xFF5C0A12),
        onErrorContainer = Color(0xFFFFDAD6),
    )
}

/** Light companion for Aura when the user forces light mode with the default seed. */
fun auraLightColorScheme(): ColorScheme =
    lightColorScheme(
        // Darker green for readable primary text on light surfaces (~AA).
        primary = Color(0xFF0B7A32),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB6F5C9),
        onPrimaryContainer = Color(0xFF00210B),
        secondary = Color(0xFF535353),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8E8E8),
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary = Color(0xFF0B7A32),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF121212),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF121212),
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF535353),
        surfaceTint = Color(0xFF0B7A32),
        outline = Color(0xFFB3B3B3),
        outlineVariant = Color(0xFFD4D4D4),
        scrim = Color.Black,
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFEBEBEB),
        surfaceContainerHighest = Color(0xFFE0E0E0),
        surfaceContainerLow = Color(0xFFF7F7F7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFDEDEDE),
        error = AuraError,
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    // Default / legacy seed → handcrafted Aura palette (never wallpaper dynamic purple).
    val useAuraDefault = themeColor.isAuraDefaultSeed()

    val baseColorScheme =
        if (useAuraDefault) {
            if (darkTheme) auraDarkColorScheme(pureBlack = false) else auraLightColorScheme()
        } else {
            rememberDynamicColorScheme(
                seedColor = themeColor,
                isDark = darkTheme,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                style = PaletteStyle.TonalSpot,
            )
        }

    val colorScheme =
        remember(baseColorScheme, pureBlack, darkTheme, useAuraDefault) {
            when {
                useAuraDefault && darkTheme && pureBlack -> auraDarkColorScheme(pureBlack = true)
                darkTheme && pureBlack -> baseColorScheme.pureBlack(true)
                else -> baseColorScheme
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = if (useAuraDefault) AuraShapes else Shapes(),
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
