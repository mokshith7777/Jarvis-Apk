package com.mokshith.jarvis.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object JarvisColors {
    val OledBlack = Color(0xFF000000)
    val CharcoalSurface = Color(0xFF0D1117)
    val CharcoalElevated = Color(0xFF161B22)
    val PanelBorder = Color(0xFF233042)
    val CyanGlow = Color(0xFF00E5FF)
    val CyanDim = Color(0xFF0A4F5C)
    val CrimsonAccent = Color(0xFFFF2A5F)
    val EmeraldOk = Color(0xFF00E676)
    val AmberWarn = Color(0xFFFFB300)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
}

private val JarvisDarkScheme = darkColorScheme(
    primary = JarvisColors.CyanGlow,
    onPrimary = Color(0xFF00141A),
    primaryContainer = JarvisColors.CyanDim,
    onPrimaryContainer = JarvisColors.TextPrimary,
    secondary = JarvisColors.CrimsonAccent,
    onSecondary = Color.White,
    background = JarvisColors.OledBlack,
    onBackground = JarvisColors.TextPrimary,
    surface = JarvisColors.CharcoalSurface,
    onSurface = JarvisColors.TextPrimary,
    surfaceVariant = JarvisColors.CharcoalElevated,
    onSurfaceVariant = JarvisColors.TextSecondary,
    outline = JarvisColors.PanelBorder,
    error = JarvisColors.CrimsonAccent
)

private val JarvisTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.8.sp,
        color = JarvisColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.5.sp,
        color = JarvisColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = JarvisColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        color = JarvisColors.TextSecondary
    )
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkScheme,
        typography = JarvisTypography,
        content = content
    )
}
