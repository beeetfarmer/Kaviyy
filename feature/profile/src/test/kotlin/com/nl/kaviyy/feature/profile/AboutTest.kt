package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class AboutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsVersionsAndWarnsAboutOldServers() {
        compose.setContent {
            KavitaTheme {
                AboutScreen(
                    AboutUiState("Home Server", "0.8.4", supported = false),
                    "0.1.0",
                    {},
                )
            }
        }
        compose.onNodeWithText("Kavita 0.8.4").assertExists()
        compose.onNodeWithText("0.1.0").assertExists()
        compose.onNodeWithText("older than Kavita 0.9.0.2", substring = true).assertExists()
    }

    @Test
    fun supportedServersHaveNoWarning() {
        compose.setContent { KavitaTheme { AboutScreen(AboutUiState("Home Server", "0.9.1"), "0.1.0", {}) } }
        compose.onNodeWithText("Readium Kotlin Toolkit").assertExists()
        compose
            .onNodeWithText("Ask your server admin to update.", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun screenshotAboutLight() = shot("about_light", Appearance.Light)

    @Test
    fun screenshotAboutDark() = shot("about_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) { AboutScreen(AboutUiState("Home Server", "0.9.1"), "0.1.0", {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
