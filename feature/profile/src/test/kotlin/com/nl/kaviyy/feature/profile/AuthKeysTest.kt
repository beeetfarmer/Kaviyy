package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AccountRepository
import com.nl.kaviyy.core.data.AuthKey
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.EmailChange
import com.nl.kaviyy.core.data.NewAuthKey
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val JAN_1 = 1_767_268_800_000L // 2026-01-01T12:00Z: same date in every time zone

internal val previewKeys =
    listOf(
        AuthKey(1, "Phone", "x9Qa", JAN_1, 0, JAN_1, system = false),
        AuthKey(2, "Tablet", "7Lmp", JAN_1, JAN_1 + 31_536_000_000L, 0, system = false),
        AuthKey(3, "OPDS", "c0de", JAN_1, 0, 0, system = true),
    )

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class AuthKeysTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val calls = mutableListOf<String>()
    private val clock = Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneId.of("UTC"))

    private val accounts =
        object : AccountRepository {
            override suspend fun changePassword(
                server: Server,
                username: String,
                current: String,
                new: String,
            ): AppResult<Unit> = error("unused")

            override suspend fun changeEmail(
                server: Server,
                email: String,
                password: String,
            ): AppResult<EmailChange> = error("unused")

            override suspend fun authKeys(server: Server) = AppResult.Success(previewKeys)

            override suspend fun createAuthKey(
                server: Server,
                name: String,
                expiresAt: Instant?,
            ): AppResult<NewAuthKey> {
                calls += "create $name $expiresAt"
                return AppResult.Success(NewAuthKey(previewKeys[0].copy(id = 9, name = name), "S".repeat(32)))
            }

            override suspend fun rotateAuthKey(
                server: Server,
                key: AuthKey,
            ): AppResult<NewAuthKey> {
                calls += "rotate ${key.id}"
                return AppResult.Success(NewAuthKey(key, "R".repeat(32)))
            }

            override suspend fun deleteAuthKey(
                server: Server,
                id: Int,
            ): AppResult<Unit> {
                calls += "delete $id"
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
    fun createWithExpiryRevealsTheSecretOnceUntilClosed() =
        runTest {
            val vm = AuthKeysViewModel(session, accounts, clock)
            runCurrent()
            vm.openNew()
            vm.name.setTextAndPlaceCursorAtEnd(" Laptop ")
            vm.setExpiry(1)
            vm.create()
            runCurrent()
            assertEquals(listOf("create Laptop 2026-12-21T00:00:00Z"), calls)
            assertEquals(
                "S".repeat(32),
                vm.state.value.revealed
                    ?.secret,
            )
            vm.close()
            assertNull(vm.state.value.revealed)
        }

    @Test
    fun rotateAndDeleteAskFirstAndSystemKeysCantBeOpened() =
        runTest {
            val vm = AuthKeysViewModel(session, accounts, clock)
            runCurrent()
            vm.select(previewKeys[2])
            assertNull(vm.state.value.selected)
            vm.select(previewKeys[0])
            vm.rotate()
            assertEquals(KeyConfirm.Rotate, vm.state.value.confirm)
            vm.rotate()
            runCurrent()
            assertEquals(
                "R".repeat(32),
                vm.state.value.revealed
                    ?.secret,
            )
            vm.close()
            vm.select(previewKeys[1])
            vm.delete()
            vm.delete()
            runCurrent()
            assertEquals(listOf("rotate 1", "delete 2"), calls)
            assertNull(vm.state.value.revealed)
        }

    @Test
    fun revealCopies() {
        var copied = ""
        compose.setContent {
            KavitaTheme {
                AuthKeysScreen(
                    AuthKeysUiState(previewKeys, revealed = NewAuthKey(previewKeys[0], "secret-123")),
                    TextFieldState(),
                    AuthKeysActions(onCopy = { copied = it }),
                )
            }
        }
        compose.onNodeWithText("Copy Key").performClick()
        assertEquals("secret-123", copied)
    }

    @Test
    fun screenshotKeysLight() = shot("auth_keys_light", Appearance.Light, AuthKeysUiState(previewKeys))

    @Test
    fun screenshotKeysDark() = shot("auth_keys_dark", Appearance.Dark, AuthKeysUiState(previewKeys))

    @Test
    fun screenshotKeySheet() =
        shot(
            "auth_keys_sheet",
            Appearance.Light,
            AuthKeysUiState(previewKeys, selected = previewKeys[0], confirm = KeyConfirm.Delete),
        )

    private fun shot(
        name: String,
        appearance: Appearance,
        state: AuthKeysUiState,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) { AuthKeysScreen(state, TextFieldState(), AuthKeysActions()) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
