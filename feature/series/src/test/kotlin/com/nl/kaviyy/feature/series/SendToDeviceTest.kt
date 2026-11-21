package com.nl.kaviyy.feature.series

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.DeviceRepository
import com.nl.kaviyy.core.data.SendTarget
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.EmailDeviceDto
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class SendToDeviceTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val devices =
        listOf(EmailDeviceDto(1, "Paperwhite", "a@kindle.com", 2), EmailDeviceDto(2, "Libra", "a@kobo.com", 3))
    private val sent = mutableListOf<String>()

    private val repo =
        object : DeviceRepository {
            override suspend fun devices(server: Server) = AppResult.Success(devices)

            override suspend fun save(
                server: Server,
                device: EmailDeviceDto,
            ): AppResult<EmailDeviceDto> = error("unused")

            override suspend fun delete(
                server: Server,
                id: Int,
            ): AppResult<Unit> = error("unused")

            override suspend fun send(
                server: Server,
                deviceId: Int,
                target: SendTarget,
            ): AppResult<Unit> {
                if (deviceId == 2) return AppResult.Failure(AppError.Network)
                sent += "$target to $deviceId"
                return AppResult.Success(Unit)
            }
        }

    private val session =
        object : AuthSession {
            override val servers = active.map { listOfNotNull(it) }
            override val activeServer = active

            override suspend fun switchTo(serverId: ServerId) = Unit

            override suspend fun remove(serverId: ServerId) = Unit

            override suspend fun deleteAllLocalData() = Unit

            override suspend fun knownServer(url: ServerUrl): Server? = null

            override suspend fun signIn(
                target: Server,
                method: LoginMethod,
                staySignedIn: Boolean,
            ): AppResult<Server> = error("unused")

            override suspend fun signOut(serverId: ServerId) = Unit

            override fun credentialSource(serverId: ServerId) = error("unused")
        }

    @Test
    fun sendsAndMarksTheDeviceAndReportsAFailure() =
        runTest {
            val vm = SendToDeviceViewModel(session, repo)
            vm.open()
            runCurrent()
            vm.send(1, SendTarget.Chapters(listOf(5, 6)))
            runCurrent()
            assertEquals(listOf("Chapters(ids=[5, 6]) to 1"), sent)
            assertEquals(setOf(1), vm.state.value.sent)
            vm.send(2, SendTarget.Series(9))
            runCurrent()
            assertEquals(true, vm.state.value.sendFailed)
            assertEquals(setOf(1), vm.state.value.sent)
        }

    @Test
    fun noDevicesPointsToSettings() {
        compose.setContent { KavitaTheme { SendToDeviceContent(SendSheetState(emptyList()), {}, {}) } }
        compose.onNodeWithText("No devices yet. Add one in You › Send to Device.").assertExists()
    }

    @Test
    fun tappingADeviceSends() {
        var sentTo = 0
        compose.setContent { KavitaTheme { SendToDeviceContent(SendSheetState(devices), {}, { sentTo = it }) } }
        compose.onNodeWithText("Libra").performClick()
        assertEquals(2, sentTo)
    }

    @Test
    fun screenshotSendSheet() {
        compose.setContent {
            KavitaTheme(
                appearance = Appearance.Light,
            ) { SendToDeviceContent(SendSheetState(devices, sent = setOf(1)), {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/series_send_to_device.png")
    }
}
