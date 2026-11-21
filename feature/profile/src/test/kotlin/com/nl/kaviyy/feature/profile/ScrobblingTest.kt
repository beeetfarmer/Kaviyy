package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.LinkState
import com.nl.kaviyy.core.data.ScrobbleProvider
import com.nl.kaviyy.core.data.ScrobbleStatus
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.network.ScrobbleHoldDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ScrobblingTest {
    @get:Rule
    val compose = createComposeRule()

    private val status =
        ScrobbleStatus(
            true,
            mapOf(ScrobbleProvider.AniList to LinkState.Connected, ScrobbleProvider.Mal to LinkState.Expired),
            listOf(ScrobbleHoldDto(4, "Berserk"), ScrobbleHoldDto(9, "Vinland Saga")),
        )

    @Test
    fun showsLinksAndOpensAPausedSeries() {
        var opened = 0
        compose.setContent { KavitaTheme { ScrobblingScreen(ScrobblingUiState(status), {}, { opened = it }) } }
        compose.onNodeWithText("Connected").assertExists()
        compose.onNodeWithText("Expired").assertExists()
        compose.onNodeWithText("Vinland Saga").performClick()
        assertEquals(9, opened)
    }

    @Test
    fun unlicensedExplainsKavitaPlus() {
        compose.setContent { KavitaTheme { ScrobblingScreen(ScrobblingUiState(ScrobbleStatus(false)), {}, {}) } }
        compose.onNodeWithText("Scrobbling needs Kavita+ on your server.").assertExists()
        compose.onNodeWithText("Accounts").assertDoesNotExist()
    }

    @Test
    fun screenshotScrobblingLight() = shot("scrobbling_light", Appearance.Light)

    @Test
    fun screenshotScrobblingDark() = shot("scrobbling_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent {
            KavitaTheme(
                appearance = appearance,
            ) { ScrobblingScreen(ScrobblingUiState(status), {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
