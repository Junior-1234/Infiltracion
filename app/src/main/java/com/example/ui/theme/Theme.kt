package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = HydroTealDark,
    onPrimary = HydroTealOnDark,
    primaryContainer = HydroTealContainerDark,
    onPrimaryContainer = HydroTealOnContainerDark,
    secondary = LoamEarthDark,
    onSecondary = LoamEarthOnDark,
    secondaryContainer = LoamEarthContainerDark,
    onSecondaryContainer = LoamEarthOnContainerDark,
    tertiary = DarcyMossDark,
    onTertiary = DarcyMossOnDark,
    tertiaryContainer = DarcyMossContainerDark,
    onTertiaryContainer = DarcyMossOnContainerDark,
    background = SlateDarkBackground,
    onBackground = SlateDarkOnSurface,
    surface = SlateDarkSurface,
    onSurface = SlateDarkOnSurface,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = SlateDarkOnSurfaceVariant,
    outline = SlateDarkOutline,
    outlineVariant = SlateDarkOutlineVariant,
    error = AlertRedContainer,
    onError = OnAlertRedContainer
)

private val LightColorScheme = lightColorScheme(
    primary = HydroTealPrimary,
    onPrimary = HydroTealOnPrimary,
    primaryContainer = HydroTealContainer,
    onPrimaryContainer = HydroTealOnContainer,
    secondary = LoamEarthSecondary,
    onSecondary = LoamEarthOnSecondary,
    secondaryContainer = LoamEarthContainer,
    onSecondaryContainer = LoamEarthOnContainer,
    tertiary = DarcyMossTertiary,
    onTertiary = DarcyMossOnTertiary,
    tertiaryContainer = DarcyMossContainer,
    onTertiaryContainer = DarcyMossOnContainer,
    background = FieldPaperBackground,
    onBackground = FieldInkOnSurface,
    surface = FieldPaperCard,
    onSurface = FieldInkOnSurface,
    surfaceVariant = FieldPaperSurfaceVariant,
    onSurfaceVariant = FieldInkSecondary,
    outline = FieldOutline,
    outlineVariant = FieldOutlineVariant,
    error = AlertRed,
    onError = HydroTealOnPrimary,
    errorContainer = AlertRedContainer,
    onErrorContainer = OnAlertRedContainer
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun InfiltrometroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
