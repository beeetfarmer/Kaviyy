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

    /** Narrow phone with a large system font: "Downloads" must stay inside its pill (it was clipped both sides). */
    @Test
    @Config(qualifiers = "w360dp-h200dp-xhdpi", fontScale = 1.3f)
    fun tabBarNarrowLargeFont() =
        captureRoboImage("src/test/screenshots/tab_bar_narrow_font130.png") {
            KavitaTheme(Appearance.Light) {
                FloatingTabBar(
                    items =
                        listOf("Home", "Library", "Lists", "Downloads", "You").map {
                            TabItem(it, com.nl.kaviyy.core.designsystem.icon.KavitaIcons.Search)
                        },
                    selectedIndex = 3,
                    onSelect = {},
                    searchLabel = "Search",
                    onSearch = {},
                )
            }
        }

    private fun capture(
        appearance: Appearance,
        name: String,
    ) = captureRoboImage("src/test/screenshots/component_showcase_$name.png") {
        KavitaTheme(appearance) { ComponentShowcase(Modifier.fillMaxWidth()) }
    }
}
