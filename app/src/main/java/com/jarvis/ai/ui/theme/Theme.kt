package com.jarvis.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColors = darkColorScheme(
    primary = Color(0xFF00D9FF),
    secondary = Color(0xFFFF334F),
    background = Color(0xFF05070A),
    surface = Color(0xFF10151B),
    surfaceVariant = Color(0xFF18212A),
    onPrimary = Color(0xFF001015),
    onSecondary = Color.White,
    onBackground = Color(0xFFEAF5F8),
    onSurface = Color(0xFFEAF5F8)
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColors, content = content)
}
