package com.nl.kaviyy.feature.profile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class YouScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyControlHasASpokenLabel0() {
        compose.setContent { YouPreview() }
        // TalkBack: nothing tappable may be announced without a name.
        compose
            .onAllNodes(
                hasClickAction() and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
            ).assertCountEquals(0)
    }

    @Test
    fun showsProfileAndWeekAndRoutesActions() {
        val calls = mutableListOf<String>()
        compose.setContent {
            KavitaTheme {
                YouScreen(
                    previewYou,
                    YouActions(
                        onAllStats = { calls += "stats" },
                        onServers = { calls += "servers" },
                        onAccount = { calls += "account" },
                        onPrivacy = { calls += "privacy" },
                        onAdmin = { calls += "admin" },
                    ),
                )
            }
        }
        compose.onNodeWithText("Neo Anderson").assertExists()
        compose.onNodeWithText("Admin · kavita.home.lan").assertExists()
        compose.onNodeWithText("6h").assertExists()
        compose.onNodeWithText("412").assertExists()
        compose.onNodeWithText("All Stats").performClick()
        compose.onNodeWithText("Servers").performClick()
        compose.onNodeWithText("Account").performClick()
        compose.onNodeWithText("Privacy & Security").performClick()
        compose.onNodeWithText("Server Admin").performClick()
        assertEquals(listOf("stats", "servers", "account", "privacy", "admin"), calls)
    }

    @Test
    fun serverAdminIsOnlyOfferedToAdmins() {
        val user = previewYou.copy(account = previewYou.account?.copy(admin = false))
        compose.setContent { KavitaTheme { YouScreen(user, YouActions()) } }
        compose.onNodeWithText("Server Admin").assertDoesNotExist()
    }

    @Test
    fun offlineShowsDashesAndANote() {
        compose.setContent { KavitaTheme { YouScreen(previewYou.copy(week = null, statsFailed = true), YouActions()) } }
        compose.onNodeWithText("Stats aren't available right now. Connect to your server to see them.").assertExists()
    }

    @Test
    fun initialsTakeTwoWords() {
        assertEquals("NA", initials("Neo Anderson"))
        assertEquals("T", initials("trinity"))
        assertEquals("?", initials(""))
    }

    @Test
    fun screenshotYouLight() = shot("you_light") { YouPreview() }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi", fontScale = 2f)
    fun screenshotYouFontScale200() = shot("you_font200") { YouPreview() }

    @Test
    fun screenshotYouDark() = shot("you_dark") { YouPreview(Appearance.Dark) }

    @Test
    @Config(qualifiers = "w412dp-h2900dp-xhdpi") // the whole page, every chart
    fun screenshotAllStatsLight() = shot("all_stats_light") { AllStatsPreview() }

    @Test
    @Config(qualifiers = "w412dp-h2900dp-xhdpi") // the whole page, every chart
    fun screenshotAllStatsDark() = shot("all_stats_dark") { AllStatsPreview(Appearance.Dark) }

    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
