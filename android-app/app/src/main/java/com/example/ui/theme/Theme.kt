package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = AegeanPrimaryLight,
    onPrimary = OnAegeanPrimaryLight,
    primaryContainer = AegeanPrimaryContainerLight,
    onPrimaryContainer = OnAegeanPrimaryContainerLight,
    secondary = TerracottaSecondaryLight,
    onSecondary = OnTerracottaSecondaryLight,
    secondaryContainer = TerracottaSecondaryContainerLight,
    onSecondaryContainer = OnTerracottaSecondaryContainerLight,
    tertiary = BrassTertiaryLight,
    onTertiary = OnBrassTertiaryLight,
    tertiaryContainer = BrassTertiaryContainerLight,
    onTertiaryContainer = OnBrassTertiaryContainerLight,
    background = AlabasterBackgroundLight,
    onBackground = OnAlabasterBackgroundLight,
    surface = LimestoneSurfaceLight,
    onSurface = OnAlabasterBackgroundLight,
    surfaceVariant = LimestoneSurfaceVariantLight,
    onSurfaceVariant = OnLimestoneSurfaceVariantLight,
    surfaceContainerLowest = LimestoneSurfaceContainerLowestLight,
    surfaceContainerLow = LimestoneSurfaceContainerLowLight,
    surfaceContainer = LimestoneSurfaceContainerLight,
    surfaceContainerHigh = LimestoneSurfaceContainerHighLight,
    outline = OutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AegeanPrimaryDark,
    onPrimary = OnAegeanPrimaryDark,
    primaryContainer = AegeanPrimaryContainerDark,
    onPrimaryContainer = OnAegeanPrimaryContainerDark,
    secondary = TerracottaSecondaryDark,
    onSecondary = OnTerracottaSecondaryDark,
    secondaryContainer = TerracottaSecondaryContainerDark,
    onSecondaryContainer = OnTerracottaSecondaryContainerDark,
    tertiary = BrassTertiaryDark,
    onTertiary = OnBrassTertiaryDark,
    tertiaryContainer = BrassTertiaryContainerDark,
    onTertiaryContainer = OnBrassTertiaryContainerDark,
    background = SlateBackgroundDark,
    onBackground = OnSlateBackgroundDark,
    surface = SlateSurfaceDark,
    onSurface = OnSlateBackgroundDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = OnSlateSurfaceVariantDark,
    surfaceContainerLowest = SlateSurfaceContainerLowestDark,
    surfaceContainerLow = SlateSurfaceContainerLowDark,
    surfaceContainer = SlateSurfaceContainerDark,
    surfaceContainerHigh = SlateSurfaceContainerHighDark,
    outline = OutlineDark
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HotelPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    HotelPulseTheme(darkTheme = darkTheme, content = content)
}
