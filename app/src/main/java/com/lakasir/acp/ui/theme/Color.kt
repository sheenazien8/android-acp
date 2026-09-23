package com.lakasir.acp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object AcpPalette {
    val Accent = Color(0xFF5FB3A3)
    val OnAccent = Color(0xFF0B1F1B)
    val AccentTextLight = Color(0xFF2E7D6F)

    val DarkBackground = Color(0xFF14161A)
    val DarkSurface = Color(0xFF1C1F24)
    val DarkSurfaceHigh = Color(0xFF23272D)
    val DarkBorder = Color(0xFF2C3037)
    val DarkTextPrimary = Color(0xFFE7E9EC)
    val DarkTextSecondary = Color(0xFF8B92A0)

    val LightBackground = Color(0xFFF5F6F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceHigh = Color(0xFFECEEF1)
    val LightBorder = Color(0xFFD9DCE1)
    val LightTextPrimary = Color(0xFF1A1D22)
    val LightTextSecondary = Color(0xFF5F6673)

    val DiffAdded = Color(0xFF4E9A6B)
    val DiffRemoved = Color(0xFFC1666B)
    val Warning = Color(0xFFC9A55B)
    val Error = Color(0xFFC1666B)
}

val DarkColorScheme = darkColorScheme(
    primary = AcpPalette.Accent,
    onPrimary = AcpPalette.OnAccent,
    primaryContainer = AcpPalette.DarkSurfaceHigh,
    onPrimaryContainer = AcpPalette.Accent,
    secondary = AcpPalette.DarkTextSecondary,
    onSecondary = AcpPalette.DarkBackground,
    secondaryContainer = AcpPalette.DarkSurfaceHigh,
    onSecondaryContainer = AcpPalette.DarkTextPrimary,
    tertiary = AcpPalette.Accent,
    onTertiary = AcpPalette.OnAccent,
    background = AcpPalette.DarkBackground,
    onBackground = AcpPalette.DarkTextPrimary,
    surface = AcpPalette.DarkBackground,
    onSurface = AcpPalette.DarkTextPrimary,
    surfaceVariant = AcpPalette.DarkSurface,
    onSurfaceVariant = AcpPalette.DarkTextSecondary,
    surfaceContainerLowest = AcpPalette.DarkBackground,
    surfaceContainerLow = AcpPalette.DarkSurface,
    surfaceContainer = AcpPalette.DarkSurface,
    surfaceContainerHigh = AcpPalette.DarkSurfaceHigh,
    surfaceContainerHighest = AcpPalette.DarkSurfaceHigh,
    outline = AcpPalette.DarkBorder,
    outlineVariant = AcpPalette.DarkBorder,
    error = AcpPalette.Error,
    onError = AcpPalette.DarkBackground,
    errorContainer = AcpPalette.DarkSurfaceHigh,
    onErrorContainer = AcpPalette.Error,
    inverseSurface = AcpPalette.DarkTextPrimary,
    inverseOnSurface = AcpPalette.DarkBackground,
    inversePrimary = AcpPalette.AccentTextLight,
    scrim = Color.Black,
)

val LightColorScheme = lightColorScheme(
    primary = AcpPalette.Accent,
    onPrimary = AcpPalette.OnAccent,
    primaryContainer = AcpPalette.LightSurfaceHigh,
    onPrimaryContainer = AcpPalette.AccentTextLight,
    secondary = AcpPalette.LightTextSecondary,
    onSecondary = AcpPalette.LightSurface,
    secondaryContainer = AcpPalette.LightSurfaceHigh,
    onSecondaryContainer = AcpPalette.LightTextPrimary,
    tertiary = AcpPalette.Accent,
    onTertiary = AcpPalette.OnAccent,
    background = AcpPalette.LightBackground,
    onBackground = AcpPalette.LightTextPrimary,
    surface = AcpPalette.LightBackground,
    onSurface = AcpPalette.LightTextPrimary,
    surfaceVariant = AcpPalette.LightSurface,
    onSurfaceVariant = AcpPalette.LightTextSecondary,
    surfaceContainerLowest = AcpPalette.LightSurface,
    surfaceContainerLow = AcpPalette.LightSurface,
    surfaceContainer = AcpPalette.LightSurface,
    surfaceContainerHigh = AcpPalette.LightSurfaceHigh,
    surfaceContainerHighest = AcpPalette.LightSurfaceHigh,
    outline = AcpPalette.LightBorder,
    outlineVariant = AcpPalette.LightBorder,
    error = AcpPalette.Error,
    onError = AcpPalette.LightSurface,
    errorContainer = AcpPalette.LightSurfaceHigh,
    onErrorContainer = AcpPalette.Error,
    inverseSurface = AcpPalette.LightTextPrimary,
    inverseOnSurface = AcpPalette.LightBackground,
    inversePrimary = AcpPalette.Accent,
    scrim = Color.Black,
)

@Immutable
data class AcpExtendedColors(
    val accentText: Color,
    val diffAdded: Color,
    val diffRemoved: Color,
    val diffAddedBackground: Color,
    val diffRemovedBackground: Color,
    val warning: Color,
    val roleUser: Color,
    val roleAgent: Color,
    val roleTool: Color,
    val roleError: Color,
)

val DarkExtendedColors = AcpExtendedColors(
    accentText = AcpPalette.Accent,
    diffAdded = AcpPalette.DiffAdded,
    diffRemoved = AcpPalette.DiffRemoved,
    diffAddedBackground = AcpPalette.DiffAdded.copy(alpha = 0.16f),
    diffRemovedBackground = AcpPalette.DiffRemoved.copy(alpha = 0.16f),
    warning = AcpPalette.Warning,
    roleUser = AcpPalette.Accent,
    roleAgent = AcpPalette.DarkTextSecondary,
    roleTool = AcpPalette.DarkBorder,
    roleError = AcpPalette.Error,
)

val LightExtendedColors = AcpExtendedColors(
    accentText = AcpPalette.AccentTextLight,
    diffAdded = AcpPalette.DiffAdded,
    diffRemoved = AcpPalette.DiffRemoved,
    diffAddedBackground = AcpPalette.DiffAdded.copy(alpha = 0.14f),
    diffRemovedBackground = AcpPalette.DiffRemoved.copy(alpha = 0.14f),
    warning = AcpPalette.Warning,
    roleUser = AcpPalette.Accent,
    roleAgent = AcpPalette.LightTextSecondary,
    roleTool = AcpPalette.LightBorder,
    roleError = AcpPalette.Error,
)

val LocalAcpExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
