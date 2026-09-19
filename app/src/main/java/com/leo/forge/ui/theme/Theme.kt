package com.leo.forge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalForgeColors = staticCompositionLocalOf { forgeDark(oled = true) }

/** Mirrors the user's haptics setting so every tappable surface honours it. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

object Forge {
    val colors: ForgeColors
        @Composable @ReadOnlyComposable get() = LocalForgeColors.current
}

@Composable
fun ForgeTheme(
    oled: Boolean = true,
    dark: Boolean = isSystemInDarkTheme() || true, // dark-only for now; light mode is a later job
    content: @Composable () -> Unit,
) {
    val colors = forgeDark(oled)

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.surface3,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.cool,
            onSecondary = Color.Black,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface1,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            outlineVariant = colors.outline,
            error = colors.danger,
            onError = Color.Black,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(primary = colors.accent, onPrimary = colors.onAccent)
    }

    CompositionLocalProvider(LocalForgeColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = ForgeTypography, content = content)
    }
}
