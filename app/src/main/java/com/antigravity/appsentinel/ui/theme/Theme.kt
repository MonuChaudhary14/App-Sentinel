package com.antigravity.appsentinel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val indigoSubtle: Color,
    val redSubtle: Color,
    val greenSubtle: Color,
    val onIndigo: Color,
    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        bg = BgDark,
        surface = SurfaceDark,
        surfaceElevated = SurfaceElevatedDark,
        border = BorderDark,
        textPrimary = TextPrimaryDark,
        textSecondary = TextSecondaryDark,
        textMuted = TextMutedDark,
        indigoSubtle = AccentIndigoSubtleDark,
        redSubtle = AccentRedSubtleDark,
        greenSubtle = AccentGreenSubtleDark,
        onIndigo = Color(0xFF1F242D),
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = AccentIndigo,
    onPrimary = Color(0xFF1F242D),
    primaryContainer = AccentIndigoSubtleDark,
    onPrimaryContainer = AccentIndigo,
    secondary = AccentBlue,
    onSecondary = Color(0xFF1F242D),
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = AccentIndigoLight,
    onPrimary = Color.White,
    primaryContainer = AccentIndigoSubtleLight,
    onPrimaryContainer = AccentIndigoLight,
    secondary = AccentBlue,
    onSecondary = Color.White,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight
)

@Composable
fun AppSentinelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) {
        AppColors(
            bg = BgDark,
            surface = SurfaceDark,
            surfaceElevated = SurfaceElevatedDark,
            border = BorderDark,
            textPrimary = TextPrimaryDark,
            textSecondary = TextSecondaryDark,
            textMuted = TextMutedDark,
            indigoSubtle = AccentIndigoSubtleDark,
            redSubtle = AccentRedSubtleDark,
            greenSubtle = AccentGreenSubtleDark,
            onIndigo = Color(0xFF1F242D),
            isDark = true
        )
    } else {
        AppColors(
            bg = BgLight,
            surface = SurfaceLight,
            surfaceElevated = SurfaceElevatedLight,
            border = BorderLight,
            textPrimary = TextPrimaryLight,
            textSecondary = TextSecondaryLight,
            textMuted = TextMutedLight,
            indigoSubtle = AccentIndigoSubtleLight,
            redSubtle = AccentRedSubtleLight,
            greenSubtle = AccentGreenSubtleLight,
            onIndigo = Color.White,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
