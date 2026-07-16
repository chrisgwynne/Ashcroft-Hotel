package com.ashcroft.ripple.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AshcroftDark =
    darkColorScheme(
        primary = AshcroftPalette.MutedGold,
        onPrimary = AshcroftPalette.Ink,
        secondary = AshcroftPalette.HotelGreen,
        onSecondary = AshcroftPalette.CreamStone,
        tertiary = AshcroftPalette.Tungsten,
        background = AshcroftPalette.Ink,
        onBackground = AshcroftPalette.CreamStone,
        surface = AshcroftPalette.DarkWood,
        onSurface = AshcroftPalette.CreamStone,
        surfaceVariant = AshcroftPalette.DarkWoodSoft,
        onSurfaceVariant = AshcroftPalette.CreamPaper,
        outline = AshcroftPalette.MutedGoldDeep,
    )

private val AshcroftLight =
    lightColorScheme(
        primary = AshcroftPalette.MutedGoldDeep,
        onPrimary = AshcroftPalette.Parchment,
        secondary = AshcroftPalette.HotelGreen,
        onSecondary = AshcroftPalette.Parchment,
        tertiary = AshcroftPalette.Burgundy,
        background = AshcroftPalette.Parchment,
        onBackground = AshcroftPalette.DarkWood,
        surface = AshcroftPalette.CreamStone,
        onSurface = AshcroftPalette.DarkWood,
        surfaceVariant = AshcroftPalette.CreamPaper,
        onSurfaceVariant = AshcroftPalette.DarkWoodSoft,
        outline = AshcroftPalette.MutedGoldDeep,
    )

private val AshcroftShapes =
    Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(14.dp),
    )

private val AshcroftTypography =
    Typography(
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 0.5.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 0.3.sp),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.8.sp),
    )

/**
 * Root theme for Ripple. Defaults to the warm dark "evening" scheme, which
 * best suits the isometric hotel view, but honours the system light setting.
 */
@Composable
fun RippleTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) AshcroftDark else AshcroftLight,
        typography = AshcroftTypography,
        shapes = AshcroftShapes,
        content = content,
    )
}
