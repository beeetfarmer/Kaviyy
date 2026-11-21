@file:Suppress("MagicNumber")

package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Values from docs/DESIGN.md §2 (and docs/design/screens/*.dc.html). Keep them in sync.

@Immutable
data class KavitaColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val segmentSelected: Color,
    val label: Color,
    val labelStrong: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelBody: Color,
    val tabInactive: Color,
    val placeholder: Color,
    val separator: Color,
    val separatorForm: Color,
    val fillSegmented: Color,
    val switchOff: Color,
    val track: Color,
    val hairline: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val onAccent: Color,
    val online: Color,
    val rating: Color,
    val destructive: Color,
    /** [derived] Destructive as small text: DESIGN.md §2.4 needs 4.5:1, which #E5484D misses in light. */
    val destructiveText: Color,
    val chartMuted: Color,
    val tabBar: Color,
    val tabBarShadow: Color,
    val isDark: Boolean,
)

val LightKavitaColors =
    KavitaColors(
        background = Color(0xFFF2F2F7),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFFFFFFF),
        segmentSelected = Color(0xFFFFFFFF),
        label = Color(0xFF000000),
        labelStrong = Color(0xFF1C1C1E),
        labelSecondary = Color(0xFF6C6C70),
        labelTertiary = Color(0xFFAEAEB2),
        labelBody = Color(0xFF3A3A3C),
        tabInactive = Color(0xFF3A3A3C),
        placeholder = Color(0xFF8A8A8E),
        separator = Color(0xFFE5E5EA),
        separatorForm = Color(0xFFD1D1D6),
        fillSegmented = Color(0xFFE3E3E8),
        switchOff = Color(0xFFE3E3E8),
        track = Color(0xFFE5E5EA),
        hairline = Color(0x14000000),
        accent = Color(0xFF187A4F),
        accentPressed = Color(0xFF11603D),
        accentContainer = Color(0xFFE2F0E8),
        onAccentContainer = Color(0xFF11603D),
        onAccent = Color(0xFFFFFFFF),
        online = Color(0xFF22A05F),
        rating = Color(0xFFB45309),
        destructive = Color(0xFFE5484D),
        destructiveText = Color(0xFFD61E24),
        chartMuted = Color(0xFFB9DCC8),
        tabBar = Color(0xDBFFFFFF),
        tabBarShadow = Color(0x24000000),
        isDark = false,
    )

val DarkKavitaColors =
    KavitaColors(
        background = Color(0xFF000000),
        surface = Color(0xFF1C1C1E),
        surfaceElevated = Color(0xFF2C2C2E),
        segmentSelected = Color(0xFF636366),
        label = Color(0xFFFFFFFF),
        labelStrong = Color(0xFFF2F2F7),
        labelSecondary = Color(0xFF98989F),
        labelTertiary = Color(0xFF636366),
        labelBody = Color(0xFFD1D1D6),
        tabInactive = Color(0xFFAEAEB2),
        placeholder = Color(0xFF8E8E93),
        separator = Color(0xFF38383A),
        separatorForm = Color(0xFF38383A),
        fillSegmented = Color(0xFF2C2C2E),
        switchOff = Color(0xFF39393D),
        track = Color(0xFF3A3A3C),
        hairline = Color(0x1AFFFFFF),
        accent = Color(0xFF4CC38A),
        accentPressed = Color(0xFF7FD4A8),
        accentContainer = Color(0xFF12372A),
        onAccentContainer = Color(0xFF7FD4A8),
        onAccent = Color(0xFF03140C),
        online = Color(0xFF30D158),
        rating = Color(0xFFF5A524),
        destructive = Color(0xFFFF6369),
        destructiveText = Color(0xFFFF6369),
        chartMuted = Color(0xFF2E5A44),
        tabBar = Color(0xDB1C1C1E),
        tabBarShadow = Color(0x80000000),
        isDark = true,
    )

/** The accent-dependent slice of [KavitaColors]. */
@Immutable
data class AccentColors(
    val accent: Color,
    val accentPressed: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val onAccent: Color,
)

/** DESIGN.md §2.4. Kavita Green is the canvas default; the other four are app presets (contrast-tested). */
enum class AccentPreset(
    val light: AccentColors,
    val dark: AccentColors,
) {
    KavitaGreen(
        light = AccentColors(Color(0xFF187A4F), Color(0xFF11603D), Color(0xFFE2F0E8), Color(0xFF11603D), Color.White),
        dark =
            AccentColors(
                Color(0xFF4CC38A),
                Color(0xFF7FD4A8),
                Color(0xFF12372A),
                Color(0xFF7FD4A8),
                Color(0xFF03140C),
            ),
    ),
    Ocean(
        light = AccentColors(Color(0xFF1D64B8), Color(0xFF154C8D), Color(0xFFE1ECF8), Color(0xFF154C8D), Color.White),
        dark =
            AccentColors(
                Color(0xFF5AA2EE),
                Color(0xFF8FC0F4),
                Color(0xFF102A47),
                Color(0xFF8FC0F4),
                Color(0xFF03111F),
            ),
    ),
    Ember(
        light = AccentColors(Color(0xFFAA4310), Color(0xFF93390C), Color(0xFFFBE8DC), Color(0xFF93390C), Color.White),
        dark =
            AccentColors(
                Color(0xFFF08A4B),
                Color(0xFFF6AE80),
                Color(0xFF3F1F0B),
                Color(0xFFF6AE80),
                Color(0xFF1F0A00),
            ),
    ),
    Plum(
        light = AccentColors(Color(0xFF7A3FB0), Color(0xFF602F8C), Color(0xFFEFE5F8), Color(0xFF602F8C), Color.White),
        dark =
            AccentColors(
                Color(0xFFB98AE8),
                Color(0xFFD2B3F1),
                Color(0xFF2E1A44),
                Color(0xFFD2B3F1),
                Color(0xFF160A24),
            ),
    ),
    Slate(
        light = AccentColors(Color(0xFF475569), Color(0xFF334155), Color(0xFFE6EAF0), Color(0xFF334155), Color.White),
        dark =
            AccentColors(
                Color(0xFF9AA8BC),
                Color(0xFFC0CAD8),
                Color(0xFF1F2733),
                Color(0xFFC0CAD8),
                Color(0xFF0B0F14),
            ),
    ),
}

fun KavitaColors.withAccent(preset: AccentPreset): KavitaColors = withAccent(if (isDark) preset.dark else preset.light)

fun KavitaColors.withAccent(a: AccentColors): KavitaColors =
    copy(
        accent = a.accent,
        accentPressed = a.accentPressed,
        accentContainer = a.accentContainer,
        onAccentContainer = a.onAccentContainer,
        onAccent = a.onAccent,
    )

/** Settings tile colors (white glyph on top). Same in both themes. */
object SettingsTileColors {
    val reader = Color(0xFFE8590C)
    val theme = Color(0xFF7C3AED)
    val downloads = Color(0xFF2563EB)
    val servers = Color(0xFF187A4F)
    val sendToDevice = Color(0xFF475569)
    val scrobbling = Color(0xFFDB2777)
}

object HighlightColors {
    val yellow = Color(0xFFF5D565)
    val green = Color(0xFF8FD6A8)
    val blue = Color(0xFF8DB8F0)
    val pink = Color(0xFFF0A3C0)
    val all = listOf(yellow, green, blue, pink)
}

@Immutable
data class ReaderTheme(
    val name: String,
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val chrome: Color,
    val track: Color,
    val highlight: Color,
)

object ReaderThemes {
    val Paper =
        ReaderTheme(
            "Paper",
            Color(0xFFFBF7EE),
            Color(0xFF2B2620),
            Color(0xFF6E6458),
            Color(0xCCFFFFFF),
            Color(0xFFE6DFD0),
            Color(0xFFF5E08A),
        )
    val White =
        ReaderTheme(
            "White",
            Color(0xFFFFFFFF),
            Color(0xFF111111),
            Color(0xFF6C6C70),
            Color(0xEBF2F2F7),
            Color(0xFFE5E5EA),
            Color(0xFFF8E58C),
        )
    val Night =
        ReaderTheme(
            "Night",
            Color(0xFF121212),
            Color(0xFFE8E6E1),
            Color(0xFFA1A1A6),
            Color(0xD92C2C2E),
            Color(0xFF3A3A3C),
            Color(0xFF6B5A1E),
        )
    val all = listOf(Paper, White, Night)
}

/** Image (manga/comic) reader chrome: always dark. */
object ImageReaderColors {
    val background = Color(0xFF000000)
    val chrome = Color(0xC72C2C2E)
    val content = Color(0xFFFFFFFF)
    val secondary = Color(0xFFC7C7CC)
    val accent = Color(0xFF7FD4A8)
    val track = Color(0x38FFFFFF)
    val pageBackground = Color(0xFFFAFAF7)
}
