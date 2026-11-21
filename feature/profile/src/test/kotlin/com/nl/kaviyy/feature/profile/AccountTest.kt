package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.Account
import com.nl.kaviyy.core.data.AccountRepository
import com.nl.kaviyy.core.data.AllStats
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.EmailChange
import com.nl.kaviyy.core.data.ProfileRepository
import com.nl.kaviyy.core.data.WeekStats
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class AccountTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val calls = mutableListOf<String>()
    private var passwordResult: AppResult<Unit> = AppResult.Success(Unit)
    private var emailResult = EmailChange.Sent

    private val accounts =
        object : AccountRepository {
            override suspend fun changePassword(
                server: Server,
                username: String,
                current: String,
                new: String,
            ): AppResult<Unit> {
                calls += "password $username $current $new"
                return passwordResult
            }

            override suspend fun changeEmail(
                server: Server,
                email: String,
                password: String,
            ): AppResult<EmailChange> {
                calls += "email $email $password"
                return AppResult.Success(emailResult)
            }
        }

    private val profile =
        object : ProfileRepository {
            override suspend fun account(server: Server) =
                AppResult.Success(Account(1, "neo", "neo@zion.io", false, null))

            override suspend fun week(
                server: Server,
                userId: Int,
                today: LocalDate,
                firstDay: DayOfWeek,
                zone: ZoneId,
            ): AppResult<WeekStats> = error("unused")

            override suspend fun allStats(
                server: Server,
                userId: Int,
            ): AppResult<AllStats> = error("unused")
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
    fun passwordIsCheckedLocallyThenChangedAndFieldsCleared() =
        runTest {
            val vm = AccountViewModel(session, profile, accounts)
            runCurrent()
            vm.showSheet(AccountSheet.Password)
            vm.current.setTextAndPlaceCursorAtEnd("old")
            vm.new.setTextAndPlaceCursorAtEnd("short")
            vm.savePassword()
            assertEquals(AccountProblem.PasswordLength, vm.state.value.problem)
            vm.new.setTextAndPlaceCursorAtEnd("longer-one")
            vm.confirm.setTextAndPlaceCursorAtEnd("longer-two")
            vm.savePassword()
            assertEquals(AccountProblem.PasswordMismatch, vm.state.value.problem)
            vm.confirm.setTextAndPlaceCursorAtEnd("longer-one")
            vm.savePassword()
            runCurrent()
            assertEquals(listOf("password neo old longer-one"), calls)
            assertEquals(AccountNotice.PasswordChanged, vm.state.value.notice)
            assertEquals(null, vm.state.value.sheet)
            assertEquals("", vm.current.text.toString() + vm.new.text + vm.confirm.text)
        }

    @Test
    fun failedPasswordChangeKeepsTheSheet() =
        runTest {
            passwordResult = AppResult.Failure(AppError.Server(400))
            val vm = AccountViewModel(session, profile, accounts)
            runCurrent()
            vm.showSheet(AccountSheet.Password)
            vm.new.setTextAndPlaceCursorAtEnd("longer-one")
            vm.confirm.setTextAndPlaceCursorAtEnd("longer-one")
            vm.savePassword()
            runCurrent()
            assertEquals(AccountSheet.Password to AccountProblem.Failed, vm.state.value.sheet to vm.state.value.problem)
        }

    @Test
    fun emailChangeReportsWhatTheServerDid() =
        runTest {
            emailResult = EmailChange.NotSent
            val vm = AccountViewModel(session, profile, accounts)
            runCurrent()
            vm.showSheet(AccountSheet.Email)
            vm.email.setTextAndPlaceCursorAtEnd(" new@zion.io ")
            vm.current.setTextAndPlaceCursorAtEnd("pw")
            vm.saveEmail()
            runCurrent()
            assertEquals(listOf("email new@zion.io pw"), calls)
            assertEquals(AccountNotice.EmailNotSent, vm.state.value.notice)
        }

    @Test
    fun screenRoutesActions() {
        val calls = mutableListOf<String>()
        compose.setContent {
            KavitaTheme {
                AccountScreen(
                    AccountUiState(server, Account(1, "neo", "neo@zion.io", false, null)),
                    fields(),
                    AccountActions(onSheet = { calls += "$it" }, onSignOut = { calls += "out" }),
                )
            }
        }
        compose.onNodeWithText("neo@zion.io").assertExists()
        compose.onNodeWithText("Change Password").performClick()
        compose.onNodeWithText("Sign Out").performClick()
        assertEquals(listOf("Password", "out"), calls)
    }

    @Test
    fun screenshotAccountLight() = shot("account_light", Appearance.Light, null)

    @Test
    fun screenshotAccountDark() = shot("account_dark", Appearance.Dark, null)

    @Test
    fun screenshotPasswordSheet() = shot("account_password", Appearance.Light, AccountSheet.Password)

    private fun fields() = AccountFields(TextFieldState(), TextFieldState(), TextFieldState(), TextFieldState())

    private fun shot(
        name: String,
        appearance: Appearance,
        sheet: AccountSheet?,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) {
                AccountScreen(
                    AccountUiState(server, Account(1, "neo", "neo@zion.io", false, null), sheet = sheet),
                    fields(),
                    AccountActions(),
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
