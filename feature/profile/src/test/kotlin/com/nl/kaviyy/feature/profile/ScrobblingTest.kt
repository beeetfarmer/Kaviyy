package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.LinkState
import com.nl.kaviyy.core.data.ScrobbleProvider
import com.nl.kaviyy.core.data.ScrobbleStatus
import com.nl.kaviyy.core.data.Tracker
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
    fun trackersConnectFromThePhoneEvenWithoutKavitaPlus() {
        var connected: Triple<Tracker, String, String>? = null
        val actions = TrackerActions(onConnect = { t, a, b -> connected = Triple(t, a, b) })
        val state = ScrobblingUiState(ScrobbleStatus(false), editing = Tracker.Kitsu)
        compose.setContent { KavitaTheme { ScrobblingScreen(state, {}, {}, actions) } }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("neo@example.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("hunter2")
        compose.onNodeWithText("Connect").performClick()
        assertEquals(Triple(Tracker.Kitsu, "neo@example.com", "hunter2"), connected)
    }

    @Test
    fun aConnectedTrackerOffersDisconnect() {
        var gone: Tracker? = null
        val state = ScrobblingUiState(trackers = mapOf(Tracker.AniList to "neo"), editing = Tracker.AniList)
        compose.setContent {
            KavitaTheme {
                ScrobblingScreen(
                    state,
                    {},
                    {},
                    TrackerActions(onDisconnect = { gone = it }),
                )
            }
        }
        compose.onNodeWithText("Disconnect").performClick()
        assertEquals(Tracker.AniList, gone)
    }

    @Test
    fun unlicensedExplainsKavitaPlus() {
        compose.setContent { KavitaTheme { ScrobblingScreen(ScrobblingUiState(ScrobbleStatus(false)), {}, {}) } }
        compose.onNodeWithText("Scrobbling needs Kavita+ on your server.").assertExists()
        compose.onNodeWithText("Accounts").assertDoesNotExist()
        compose.onNodeWithText("FROM THIS PHONE").assertExists() // direct trackers don't need Kavita+
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
            ) { ScrobblingScreen(ScrobblingUiState(status, trackers = mapOf(Tracker.Kitsu to "neo")), {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
