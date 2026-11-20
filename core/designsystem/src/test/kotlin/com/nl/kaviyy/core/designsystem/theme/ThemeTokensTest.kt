package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeTokensTest {
    @Test
    fun defaultAccentKeepsCanvasValues() {
        assertEquals(LightKavitaColors, kavitaColors(dark = false))
        assertEquals(DarkKavitaColors, kavitaColors(dark = true))
        assertEquals(Color(0xFF187A4F), kavitaColors(dark = false).accent)
    }

    @Test
    fun accentPresetOnlyChangesAccentSlice() {
        AccentPreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val base = if (dark) DarkKavitaColors else LightKavitaColors
                val colors = kavitaColors(dark, preset)
                val expected = if (dark) preset.dark else preset.light
                assertEquals(expected.accent, colors.accent)
                assertEquals(expected.onAccent, colors.onAccent)
                assertEquals(base.background, colors.background)
                assertEquals(base.label, colors.label)
                assertEquals(dark, colors.isDark)
            }
        }
    }

    @Test
    fun materialSchemeFollowsTokens() {
        val colors = kavitaColors(dark = true, AccentPreset.Ocean)
        val m3 = colors.toMaterial()
        assertEquals(colors.accent, m3.primary)
        assertEquals(colors.onAccent, m3.onPrimary)
        assertEquals(colors.background, m3.background)
        assertEquals(colors.surface, m3.surface)
        assertEquals(colors.destructive, m3.error)
    }

    @Test
    fun typographyUsesBundledFamilies() {
        val type = KavitaTypography()
        assertEquals(Inter, type.largeTitle.fontFamily)
        assertEquals(Inter, type.body.fontFamily)
        assertEquals(Literata, type.readerBody.fontFamily)
        assertEquals(Literata, type.coverTitle.fontFamily)
        assertEquals(34f, type.largeTitle.fontSize.value)
        assertTrue(type.largeTitle.letterSpacing.value < 0)
    }

    @Test
    fun readerAndHighlightPalettesAreComplete() {
        assertEquals(listOf("Paper", "White", "Night"), ReaderThemes.all.map { it.name })
        assertEquals(4, HighlightColors.all.size)
        assertFalse(ImageReaderColors.background == ImageReaderColors.pageBackground)
    }
}
