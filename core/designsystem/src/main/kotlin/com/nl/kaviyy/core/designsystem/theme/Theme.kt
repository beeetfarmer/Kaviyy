package com.nl.kaviyy.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

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
    /** Android 12+: the accent comes from the wallpaper (Material You) instead of [accent]. */
    dynamicAccent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance) {
            Appearance.System -> isSystemInDarkTheme()
            Appearance.Light -> false
            Appearance.Dark -> true
        }
    val context = LocalContext.current
    val colors =
        remember(dark, accent, dynamicAccent) {
            val base = kavitaColors(dark, accent)
            if (dynamicAccent &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                base.withAccent(dynamicAccent(context, dark))
            } else {
                base
            }
        }
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

/** Only the accent slice of the system's dynamic scheme: the rest of the palette stays the design's. */
@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicAccent(
    context: Context,
    dark: Boolean,
): AccentColors {
    val s = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return AccentColors(s.primary, s.onPrimaryContainer, s.primaryContainer, s.onPrimaryContainer, s.onPrimary)
}
