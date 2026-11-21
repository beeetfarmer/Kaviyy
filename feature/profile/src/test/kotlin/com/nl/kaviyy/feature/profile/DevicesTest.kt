package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import kotlin.test.assertNull

internal val previewDevices =
    listOf(EmailDeviceDto(1, "Paperwhite", "me@kindle.com", 2), EmailDeviceDto(2, "Libra", "me@kobo.com", 3))

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class DevicesTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val calls = mutableListOf<String>()
    private var list = previewDevices
    private var failSave = false

    private val repo =
        object : DeviceRepository {
            override suspend fun devices(server: Server) = AppResult.Success(list)

            override suspend fun save(
                server: Server,
                device: EmailDeviceDto,
            ): AppResult<EmailDeviceDto> {
                if (failSave) return AppResult.Failure(AppError.Network)
                calls += "save ${device.id} ${device.name} ${device.emailAddress} ${device.platform}"
                list = list + device.copy(id = 3)
                return AppResult.Success(device)
            }

            override suspend fun delete(
                server: Server,
                id: Int,
            ): AppResult<Unit> {
                calls += "delete $id"
                return AppResult.Success(Unit)
            }

            override suspend fun send(
                server: Server,
                deviceId: Int,
                target: SendTarget,
            ) = AppResult.Success(Unit)
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
    fun addValidatesTheEmailThenSavesAndReloads() =
        runTest {
            val vm = DevicesViewModel(session, repo)
            runCurrent()
            vm.add()
            vm.name.setTextAndPlaceCursorAtEnd(" Clara ")
            vm.email.setTextAndPlaceCursorAtEnd("clara@kobo")
            vm.setPlatform(3)
            vm.save()
            assertEquals(
                true,
                vm.state.value.form
                    ?.invalidEmail,
            )
            vm.email.setTextAndPlaceCursorAtEnd("clara@kobo.com")
            vm.save()
            runCurrent()
            assertEquals(listOf("save 0 Clara clara@kobo.com 3"), calls)
            assertNull(vm.state.value.form)
            assertEquals(
                3,
                vm.state.value.devices
                    ?.size,
            )
        }

    @Test
    fun editKeepsTheIdAndDeleteAsksFirst() =
        runTest {
            val vm = DevicesViewModel(session, repo)
            runCurrent()
            vm.edit(previewDevices[0])
            assertEquals("me@kindle.com", vm.email.text.toString())
            vm.delete()
            assertEquals(
                true,
                vm.state.value.form
                    ?.confirmDelete,
            )
            vm.delete()
            runCurrent()
            assertEquals(listOf("delete 1"), calls)
        }

    @Test
    fun failedSaveKeepsTheSheetOpen() =
        runTest {
            failSave = true
            val vm = DevicesViewModel(session, repo)
            runCurrent()
            vm.edit(previewDevices[1])
            vm.save()
            runCurrent()
            assertEquals(true, vm.state.value.changeFailed)
            assertEquals(
                2,
                vm.state.value.form
                    ?.id,
            )
        }

    @Test
    fun rowsOpenTheEditor() {
        val calls = mutableListOf<String>()
        compose.setContent {
            KavitaTheme {
                DevicesScreen(
                    DevicesUiState(previewDevices),
                    TextFieldState(),
                    TextFieldState(),
                    DevicesActions(onEdit = { calls += "edit ${it.id}" }, onAdd = { calls += "add" }),
                )
            }
        }
        compose.onNodeWithText("Libra").performClick()
        compose.onNodeWithText("Add Device").performClick()
        assertEquals(listOf("edit 2", "add"), calls)
    }

    @Test
    fun screenshotDevicesLight() = shot("devices_light", Appearance.Light, DevicesUiState(previewDevices))

    @Test
    fun screenshotDevicesDark() = shot("devices_dark", Appearance.Dark, DevicesUiState(previewDevices))

    @Test
    fun screenshotDeviceSheet() =
        shot("devices_sheet", Appearance.Light, DevicesUiState(previewDevices, form = DeviceForm(1, 2)))

    private fun shot(
        name: String,
        appearance: Appearance,
        state: DevicesUiState,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) {
                DevicesScreen(state, TextFieldState("Paperwhite"), TextFieldState("me@kindle.com"), DevicesActions())
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
