package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * DESIGN.md §2.4: body text ≥ 4.5:1, large text (≥ 24sp or ≥ 18.66sp bold) and icons ≥ 3:1,
 * for every theme and accent. Translucent colors are composited over their real backdrop first.
 */
class ContrastTest {
    private enum class Use(
        val min: Double,
    ) {
        Text(4.5),
        Icon(3.0),

        /** Placeholder: allowed because every input also has a visible label (DESIGN.md §2.1). */
        Placeholder(3.0),
    }

    private data class Pair(
        val name: String,
        val fg: Color,
        val bg: Color,
        val use: Use,
    )

    private fun ratio(
        fg: Color,
        bg: Color,
    ): Double {
        val opaqueBg = bg.compositeOver(Color.Black)
        val a = fg.compositeOver(opaqueBg).luminance() + 0.05
        val b = opaqueBg.luminance() + 0.05
        return maxOf(a, b).toDouble() / minOf(a, b)
    }

    private fun appPairs(c: KavitaColors): List<Pair> {
        val tabBar = c.tabBar.compositeOver(c.background)
        return listOf(
            Pair("label/background", c.label, c.background, Use.Text),
            Pair("label/surface", c.label, c.surface, Use.Text),
            Pair("labelStrong/surface", c.labelStrong, c.surface, Use.Text),
            Pair("labelSecondary/background", c.labelSecondary, c.background, Use.Text),
            Pair("labelSecondary/surface", c.labelSecondary, c.surface, Use.Text),
            Pair("labelBody/background", c.labelBody, c.background, Use.Text),
            Pair("labelBody/surface", c.labelBody, c.surface, Use.Text),
            Pair("label/segmentSelected", c.label, c.segmentSelected, Use.Text),
            Pair("label/fillSegmented", c.label, c.fillSegmented, Use.Text),
            Pair("tabInactive/tabBar", c.tabInactive, tabBar, Use.Text),
            Pair("accent/tabBar(selected pill)", c.accent, c.accentContainer.compositeOver(tabBar), Use.Text),
            Pair("accent/surface", c.accent, c.surface, Use.Text),
            Pair("accent/background", c.accent, c.background, Use.Text),
            Pair("onAccent/accent", c.onAccent, c.accent, Use.Text),
            Pair("onAccentContainer/accentContainer", c.onAccentContainer, c.accentContainer, Use.Text),
            Pair("accent/accentContainer", c.accent, c.accentContainer, Use.Icon),
            Pair("placeholder/surface", c.placeholder, c.surface, Use.Placeholder),
            Pair("online/surface", c.online, c.surface, Use.Icon),
            Pair("rating/surface", c.rating, c.surface, Use.Icon),
            Pair("destructive/surface", c.destructive, c.surface, Use.Icon),
        )
    }

    private fun readerPairs(t: ReaderTheme): List<Pair> {
        val chrome = t.chrome.compositeOver(t.background)
        return listOf(
            Pair("${t.name} fg/bg", t.foreground, t.background, Use.Text),
            Pair("${t.name} muted/bg", t.muted, t.background, Use.Text),
            Pair("${t.name} fg/chrome", t.foreground, chrome, Use.Text),
            Pair("${t.name} muted/chrome", t.muted, chrome, Use.Text),
            Pair("${t.name} fg/highlight", t.foreground, t.highlight, Use.Text),
        )
    }

    private fun imageReaderPairs(): List<Pair> {
        val chrome = ImageReaderColors.chrome.compositeOver(ImageReaderColors.background)
        return listOf(
            Pair("image content/chrome", ImageReaderColors.content, chrome, Use.Text),
            Pair("image secondary/chrome", ImageReaderColors.secondary, chrome, Use.Text),
            Pair("image accent/chrome", ImageReaderColors.accent, chrome, Use.Icon),
        )
    }

    private fun assertAll(
        label: String,
        pairs: List<Pair>,
    ) {
        val failures =
            pairs.mapNotNull { p ->
                val r = ratio(p.fg, p.bg)
                if (r < p.use.min) "$label ${p.name}: %.2f < ${p.use.min}".format(r) else null
            }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun appThemesPassForEveryAccent() {
        AccentPreset.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                assertAll("${accent.name}/${if (dark) "dark" else "light"}", appPairs(kavitaColors(dark, accent)))
            }
        }
    }

    @Test
    fun readerThemesPass() = ReaderThemes.all.forEach { assertAll("reader", readerPairs(it)) }

    @Test
    fun imageReaderChromePasses() = assertAll("imageReader", imageReaderPairs())

    @Test
    fun settingsTilesKeepWhiteGlyphsVisible() {
        listOf(
            SettingsTileColors.reader,
            SettingsTileColors.theme,
            SettingsTileColors.downloads,
            SettingsTileColors.servers,
            SettingsTileColors.sendToDevice,
            SettingsTileColors.scrobbling,
        ).forEach { assertTrue(ratio(Color.White, it) >= Use.Icon.min, "tile $it") }
    }

    @Test
    fun ratioMatchesWcagReference() {
        // Black on white is 21:1; #767676 on white is the classic 4.54:1 AA boundary.
        assertTrue(ratio(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(ratio(Color(0xFF767676), Color.White) in 4.53..4.55)
    }
}
