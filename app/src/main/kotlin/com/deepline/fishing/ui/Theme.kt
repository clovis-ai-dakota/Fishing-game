package com.deepline.fishing.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, after Material's dark theme guidance: dark navy surfaces (not pure black),
 * desaturated "200-tone" accents that keep at least 4.5:1 contrast with text, off-white text
 * (not pure white), and accents used sparingly: aqua for actions, coral and gold for rewards.
 */
private val DeepLineColors = darkColorScheme(
    primary = Color(0xFF7FDBEA),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF00505A),
    onPrimaryContainer = Color(0xFFA6F2FF),
    secondary = Color(0xFFFFB4A2),
    onSecondary = Color(0xFF5C1A0E),
    secondaryContainer = Color(0xFF6E2A1C),
    onSecondaryContainer = Color(0xFFFFDAD2),
    tertiary = Color(0xFFFFD580),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFE9B8),
    background = Color(0xFF0B1622),
    onBackground = Color(0xFFE3EAF2),
    surface = Color(0xFF0E1A28),
    onSurface = Color(0xFFE3EAF2),
    surfaceVariant = Color(0xFF1B2E42),
    onSurfaceVariant = Color(0xFFB0C2D4),
    surfaceTint = Color(0xFF7FDBEA),
    surfaceContainerLowest = Color(0xFF07101A),
    surfaceContainerLow = Color(0xFF0F1C2A),
    surfaceContainer = Color(0xFF142536),
    surfaceContainerHigh = Color(0xFF1A2D40),
    surfaceContainerHighest = Color(0xFF20354A),
    surfaceBright = Color(0xFF2A4058),
    surfaceDim = Color(0xFF0B1622),
    outline = Color(0xFF5D7186),
    outlineVariant = Color(0xFF2E4256),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

val SparkleGold = Color(0xFFFFE082)

@Composable
fun DeepLineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DeepLineColors, content = content)
}
