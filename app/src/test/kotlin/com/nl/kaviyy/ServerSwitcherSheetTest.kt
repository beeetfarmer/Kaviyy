package com.nl.kaviyy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ServerSwitcherSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun server(
        id: Long,
        name: String,
        url: String,
    ) = Server(ServerId(id), name, (ServerUrl.parse(url) as ServerUrlResult.Valid).url)

    private val servers =
        listOf(server(1, "Home Server", "https://kavita.home.lan"), server(2, "nas.lan", "http://nas.lan:5000"))
    private val calls = mutableListOf<String>()

    @androidx.compose.runtime.Composable
    private fun Content(appearance: Appearance = Appearance.Light) =
        KavitaTheme(appearance = appearance) {
            Column(Modifier.background(Kavita.colors.background)) {
                ServerSwitcherContent(
                    servers,
                    ServerId(1),
                    { calls += "switch ${it.value}" },
                    { calls += "add" },
                    { calls += "remove ${it.value}" },
                    { calls += "done" },
                )
            }
        }

    @Test
    fun switchAddAndTwoTapRemove() {
        compose.setContent { Content() }
        compose.onNodeWithText("Home Server").performClick() // active: no-op
        compose.onNodeWithText("nas.lan").performClick()
        compose.onNodeWithText("Add Server").performClick()
        compose.onNodeWithText("Remove “Home Server”").performClick()
        compose.onNodeWithText("Tap again", substring = true).performClick()
        assertEquals(listOf("switch 2", "add", "remove 1"), calls)
    }

    @Test
    fun screenshotLight() = captureRoboImage("src/test/screenshots/servers_light.png") { Content() }

    @Test
    fun screenshotDark() = captureRoboImage("src/test/screenshots/servers_dark.png") { Content(Appearance.Dark) }
}
