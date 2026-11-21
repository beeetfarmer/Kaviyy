package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.datastore.ThemeMode
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ThemeTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun viewModelSavesEachSetting() =
        runTest {
            val store = AppSettingsStore(InMemoryDataStore(AppSettings()))
            val vm = ThemeViewModel(store)
            vm.setTheme(ThemeMode.Dark)
            vm.setAccent("Plum")
            vm.setDynamicColor(true)
            runCurrent()
            assertEquals(AppSettings(ThemeMode.Dark, "Plum", true), store.settings.first())
            assertEquals(AppSettings(ThemeMode.Dark, "Plum", true), vm.state.value)
        }

    @Test
    fun screenRoutesChoices() {
        val calls = mutableListOf<String>()
        compose.setContent {
            KavitaTheme {
                ThemeScreen(
                    AppSettings(),
                    ThemeActions(
                        onTheme = { calls += "theme $it" },
                        onAccent = { calls += "accent $it" },
                        onDynamicColor = { calls += "dynamic $it" },
                        onLanguage = { calls += "language" },
                    ),
                )
            }
        }
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithContentDescription("Ocean").performClick()
        compose.onNodeWithContentDescription("Use wallpaper colors").performClick()
        compose.onNodeWithText("Language").performClick()
        assertEquals(listOf("theme Dark", "accent Ocean", "dynamic true", "language"), calls)
    }

    @Test
    fun screenshotThemeLight() = shot("theme_light", Appearance.Light)

    @Test
    fun screenshotThemeDark() = shot("theme_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) { ThemeScreen(AppSettings(accent = "KavitaGreen"), ThemeActions()) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
