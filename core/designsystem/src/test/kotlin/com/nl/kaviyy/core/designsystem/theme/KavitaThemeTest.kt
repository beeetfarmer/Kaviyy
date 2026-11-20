package com.nl.kaviyy.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class KavitaThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appearanceAndAccentReachComposition() {
        lateinit var colors: KavitaColors
        var primary = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            KavitaTheme(appearance = Appearance.Dark, accent = AccentPreset.Plum) {
                colors = Kavita.colors
                primary = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(kavitaColors(dark = true, AccentPreset.Plum), colors)
        assertEquals(AccentPreset.Plum.dark.accent, primary)
    }

    @Test
    fun lightAppearanceUsesLightTokens() {
        lateinit var colors: KavitaColors
        compose.setContent { KavitaTheme(appearance = Appearance.Light) { colors = Kavita.colors } }
        assertEquals(LightKavitaColors, colors)
    }
}
