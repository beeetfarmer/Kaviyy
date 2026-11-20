package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Baselines live in `src/test/screenshots/` (committed). Record intentionally with
 * `./gradlew recordRoborazziDebug`, check with `./gradlew verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2600dp-xhdpi")
class ComponentShowcaseScreenshotTest {
    @Test
    fun light() = capture(Appearance.Light, "light")

    @Test
    fun dark() = capture(Appearance.Dark, "dark")

    private fun capture(
        appearance: Appearance,
        name: String,
    ) = captureRoboImage("src/test/screenshots/component_showcase_$name.png") {
        KavitaTheme(appearance) { ComponentShowcase(Modifier.fillMaxWidth()) }
    }
}
