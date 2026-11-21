package com.nl.kaviyy.feature.profile

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
    fun screenshotYouDark() = shot("you_dark") { YouPreview(Appearance.Dark) }

    @Test
    fun screenshotAllStatsLight() = shot("all_stats_light") { AllStatsPreview() }

    @Test
    fun screenshotAllStatsDark() = shot("all_stats_dark") { AllStatsPreview(Appearance.Dark) }

    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
