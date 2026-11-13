package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DuboraDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = InkPrimary,
    primaryContainer = DeepPlum,
    onPrimaryContainer = SoftLavender,
    secondary = NeonAmethyst,
    onSecondary = InkPrimary,
    secondaryContainer = CharcoalCard,
    onSecondaryContainer = LuminousIris,
    tertiary = CyanPulse,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = InkPrimary,
    surface = CharcoalSurface,
    onSurface = InkPrimary,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalCard,
    outline = CharcoalBorder,
    outlineVariant = ColorBorderSubtle(),
    error = RoseAlert,
    onError = InkPrimary
)

private fun ColorBorderSubtle() = androidx.compose.ui.graphics.Color(0xFF231F36)

val DuboraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DuboraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DuboraDarkColorScheme,
        typography = DuboraTypography,
        shapes = DuboraShapes,
        content = content
    )
}
