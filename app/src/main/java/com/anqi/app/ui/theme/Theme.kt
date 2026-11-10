package com.anqi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class AnqiPalette(
    val canvas: Color,
    val surface: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val signal: Color,
    val signalSoft: Color,
    val onSignal: Color,
    val warning: Color
)

private val DarkPalette = AnqiPalette(
    canvas = Color(0xFF171210),
    surface = Color(0xFF211A17),
    raised = Color(0xFF2C221E),
    ink = Color(0xFFFBF4EB),
    muted = Color(0xFFB3A196),
    line = Color(0xFF3D312B),
    signal = Color(0xFFFF6B5E),
    signalSoft = Color(0xFF3D211D),
    onSignal = Color(0xFFFFFFFF),
    warning = Color(0xFFE8B35C)
)

private val LightPalette = AnqiPalette(
    canvas = Color(0xFFF7F1E8),
    surface = Color(0xFFFFFDF9),
    raised = Color(0xFFF1E7DB),
    ink = Color(0xFF2B201C),
    muted = Color(0xFF8C796D),
    line = Color(0xFFE7D9CB),
    signal = Color(0xFFE95146),
    signalSoft = Color(0xFFFBE6E1),
    onSignal = Color(0xFFFFFFFF),
    warning = Color(0xFF9A6B12)
)

val LocalAnqiPalette = staticCompositionLocalOf { DarkPalette }

private val AnqiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 76.sp,
        lineHeight = 76.sp,
        letterSpacing = (-4).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 54.sp,
        lineHeight = 56.sp,
        letterSpacing = (-2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp
    )
)

@Composable
fun AnqiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.signal,
            onPrimary = palette.onSignal,
            secondary = palette.ink,
            onSecondary = palette.canvas,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.raised,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.signal,
            onError = palette.onSignal
        )
    } else {
        lightColorScheme(
            primary = palette.signal,
            onPrimary = palette.onSignal,
            secondary = palette.ink,
            onSecondary = palette.canvas,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.raised,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.signal,
            onError = palette.onSignal
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAnqiPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AnqiTypography,
            content = content
        )
    }
}

object Anqi {
    val palette: AnqiPalette
        @Composable get() = LocalAnqiPalette.current
}
