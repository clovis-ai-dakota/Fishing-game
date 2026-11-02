package com.deepline.fishing.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeepLineColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00283D),
    secondary = Color(0xFFFFCA28),
    onSecondary = Color(0xFF3A2A00),
    tertiary = Color(0xFF80DEEA),
    background = Color(0xFF04182B),
    onBackground = Color(0xFFE3F2FD),
    surface = Color(0xFF0B2A44),
    onSurface = Color(0xFFE3F2FD),
    surfaceVariant = Color(0xFF123A5A),
    onSurfaceVariant = Color(0xFFB3D4EA),
)

val SparkleGold = Color(0xFFFFE082)

@Composable
fun DeepLineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DeepLineColors, content = content)
}
