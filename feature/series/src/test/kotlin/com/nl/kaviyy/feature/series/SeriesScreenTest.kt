package com.nl.kaviyy.feature.series

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class SeriesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCanvasContentAndContinuesAtTheServerPoint() {
        var read: Int? = -1
        var back = 0
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                SeriesScreen(previewSeries, SeriesActions(onBack = { back++ }, onRead = { read = it }))
            }
        }
        compose.onNodeWithText("Mira Okafor · Manga").assertExists()
        compose.onNodeWithText("Ongoing").assertExists()
        compose.onNodeWithText("2019").assertExists()
        compose.onNodeWithText("Teen").assertExists()
        compose.onNodeWithText("4.6").assertExists()
        compose.onNodeWithText("~38 h left").assertExists()
        compose.onNodeWithContentDescription("Remove from Want to Read").assertExists()
        compose.onNodeWithText("Continue Ch. 49").performClick()
        assertEquals(49, read)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }

    @Test
    fun tabsListRowsAndRowsOpenSheets() {
        var tab: SeriesTab? = null
        var sheet: SeriesSheet? = null
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                SeriesScreen(previewSeries, SeriesActions(onTab = { tab = it }, onSheet = { sheet = it }))
            }
        }
        compose.onNodeWithText("Volume 5").assertExists()
        compose.onNodeWithText("Ch. 48–50 · 212 pages").assertExists()
        compose.onNodeWithText("61%").assertExists()
        compose.onNodeWithText("Volume 5").performClick()
        assertEquals(SeriesSheet.Volume(5), sheet)
        compose.onNodeWithText("Chapters").performClick()
        assertEquals(SeriesTab.Chapters, tab)
    }

    @Test
    fun chapterTabAndSheet() {
        var read: Int? = null
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                SeriesScreen(
                    previewSeries.copy(tab = SeriesTab.Chapters, sheet = SeriesSheet.Chapter(49)),
                    SeriesActions(onRead = { read = it }),
                )
            }
        }
        compose.onNodeWithText("The Ash Ledger · 32 pages").assertExists()
        compose.onNodeWithText("14 left").assertExists()
        // The sheet's Read button (row states also say "Read").
        compose.onNode(hasText("Read") and hasClickAction() and !hasText("Crossing", substring = true)).performClick()
        assertEquals(49, read)
    }

    @Test
    fun failedFirstLoadOffersRetry() {
        var retried = 0
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                SeriesScreen(SeriesUiState(refreshing = false), SeriesActions(onRetry = { retried++ }))
            }
        }
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun screenshotLight() = shot("series_light") { SeriesPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = shot("series_dark") { SeriesPreview(Appearance.Dark) }

    @Test
    @Config(qualifiers = "w412dp-h1400dp-xhdpi")
    fun screenshotVolumes() = shot("series_volumes_light") { SeriesPreview(Appearance.Light) }

    @Test
    fun screenshotVolumeSheet() =
        shot("series_volume_sheet_dark") {
            SeriesPreview(Appearance.Dark, previewSeries.copy(sheet = SeriesSheet.Volume(5)))
        }

    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
