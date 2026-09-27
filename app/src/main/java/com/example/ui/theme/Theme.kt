package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StealthGraphiteColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = TextPrimary,
    secondary = KeepEmerald,
    onSecondary = Color(0xFF041E15),
    secondaryContainer = KeepEmeraldContainer,
    onSecondaryContainer = KeepEmerald,
    tertiary = SpineCyan,
    onTertiary = Color(0xFF042026),
    tertiaryContainer = SpineCyanContainer,
    onTertiaryContainer = SpineCyan,
    error = DestructiveRed,
    onError = Color.White,
    errorContainer = DestructiveRedContainer,
    onErrorContainer = DestructiveRed,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderSlate,
    outlineVariant = SubtleSurface
)

@Composable
fun LuminaCleanTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StealthGraphiteColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    LuminaCleanTheme(content = content)
}
