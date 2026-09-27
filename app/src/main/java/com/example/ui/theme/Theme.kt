package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LuminaDarkColorScheme = darkColorScheme(
    primary = SpineEmerald,
    onPrimary = Color(0xFF041E15),
    primaryContainer = SpineEmeraldContainer,
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = SpineAmber,
    onSecondary = Color(0xFF231702),
    secondaryContainer = SpineAmberContainer,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = SpineViolet,
    onTertiary = Color.White,
    tertiaryContainer = SpineVioletContainer,
    onTertiaryContainer = Color(0xFFDDD6FE),
    error = SpineCoral,
    onError = Color.White,
    errorContainer = SpineCoralContainer,
    onErrorContainer = Color(0xFFFECDD3),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderSlate,
    outlineVariant = SubtleSurface
)

val LuminaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Dark-mode-first productivity interface with intentional category & spine colors
    MaterialTheme(
        colorScheme = LuminaDarkColorScheme,
        typography = Typography,
        shapes = LuminaShapes,
        content = content
    )
}
