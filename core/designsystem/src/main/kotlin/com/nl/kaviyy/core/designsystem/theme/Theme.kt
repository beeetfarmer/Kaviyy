package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalKavitaColors = staticCompositionLocalOf { LightKavitaColors }
val LocalKavitaTypography = staticCompositionLocalOf { KavitaTypography() }

enum class Appearance { System, Light, Dark }

fun kavitaColors(
    dark: Boolean,
    accent: AccentPreset = AccentPreset.KavitaGreen,
): KavitaColors = (if (dark) DarkKavitaColors else LightKavitaColors).withAccent(accent)

/** Maps our tokens onto M3 so any stock component still used inherits sane colors. */
internal fun KavitaColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        background = background,
        onBackground = label,
        surface = surface,
        onSurface = label,
        surfaceVariant = fillSegmented,
        onSurfaceVariant = labelSecondary,
        outline = separator,
        outlineVariant = separator,
        error = destructive,
    )
}

@Composable
fun KavitaTheme(
    appearance: Appearance = Appearance.System,
    accent: AccentPreset = AccentPreset.KavitaGreen,
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance) {
            Appearance.System -> isSystemInDarkTheme()
            Appearance.Light -> false
            Appearance.Dark -> true
        }
    val colors = remember(dark, accent) { kavitaColors(dark, accent) }
    CompositionLocalProvider(LocalKavitaColors provides colors) {
        MaterialTheme(colorScheme = remember(colors) { colors.toMaterial() }, content = content)
    }
}

object Kavita {
    val colors: KavitaColors
        @Composable get() = LocalKavitaColors.current
    val type: KavitaTypography
        @Composable get() = LocalKavitaTypography.current
}
