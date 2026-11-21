package com.nl.kaviyy.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.Home
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyControlHasASpokenLabel0() {
        compose.setContent { HomePreview(Appearance.Light) }
        // TalkBack: nothing tappable may be announced without a name.
        compose
            .onAllNodes(
                hasClickAction() and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
            ).assertCountEquals(0)
    }

    @Test
    fun everyControlHasASpokenLabel1() {
        compose.setContent { HomePreview(Appearance.Light, previewOffline) }
        // TalkBack: nothing tappable may be announced without a name.
        compose
            .onAllNodes(
                hasClickAction() and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
            ).assertCountEquals(0)
    }

    @Test
    fun showsContinueCardAndRailSubtitles() {
        var resumed = 0
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                HomeScreen(previewState(), "Friday, 20 November", HomeActions(onResume = { resumed = it.seriesId }), {})
            }
        }
        compose.onNodeWithText("Volume 5 · Chapter 49").assertExists()
        compose.onNodeWithText("14 pages left · about 6 min").assertExists()
        compose.onNodeWithText("Ch. 49 · 62%").assertExists()
        compose.onNodeWithText("3 new chapters").assertExists()
        compose.onNodeWithText("Resume").performClick()
        assertEquals(1, resumed)
    }

    @Test
    fun emptyAfterFailureOffersRetry() {
        var retries = 0
        val state = previewState(Home(null, emptyList())).copy(error = AppError.Network)
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                HomeScreen(state, "Friday, 20 November", HomeActions(), { retries++ })
            }
        }
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun offlinePutsDownloadedFirstAndShowsOfflinePill() {
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) { HomeScreen(previewOffline, "Friday", HomeActions(), {}) }
        }
        compose.onNodeWithText("Offline").assertExists()
        compose.onNodeWithText("Downloaded").assertExists()
        compose.onNodeWithText("Moss & Iron").assertExists()
    }

    @Test
    fun screenshotOfflineLight() =
        captureRoboImage(
            "src/test/screenshots/home_offline_light.png",
        ) { HomePreview(Appearance.Light, previewOffline) }

    @Test
    fun screenshotOfflineDark() =
        captureRoboImage("src/test/screenshots/home_offline_dark.png") { HomePreview(Appearance.Dark, previewOffline) }

    @Test
    fun screenshotLight() = captureRoboImage("src/test/screenshots/home_light.png") { HomePreview(Appearance.Light) }

    @Test
    fun screenshotDark() = captureRoboImage("src/test/screenshots/home_dark.png") { HomePreview(Appearance.Dark) }
}
