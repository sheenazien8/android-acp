package com.lakasir.acp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AcpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalAcpExtendedColors provides extended,
        LocalAcpCodeTypography provides DefaultCodeTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AcpTypography,
            shapes = AcpShapes,
            content = content,
        )
    }
}

object AcpTheme {
    val extended: AcpExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAcpExtendedColors.current

    val code: AcpCodeTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAcpCodeTypography.current
}
