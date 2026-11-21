package com.nl.kaviyy.feature.profile

import com.nl.kaviyy.core.data.Account
import com.nl.kaviyy.core.data.AllStats
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ProfileRepository
import com.nl.kaviyy.core.data.WeekStats
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.test.assertEquals

class YouViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneId.of("UTC"))
    private val asked = mutableListOf<String>()
    private var weekResult: AppResult<WeekStats> =
        AppResult.Success(WeekStats(1, 2, 3, listOf(LocalDate.of(2026, 11, 15)), listOf(1)))
    private val all = AllStats(1, 2, 3, 0.5, emptyList(), List(7) { 0L }, emptyList())

    private val profile =
        object : ProfileRepository {
            override suspend fun account(server: Server) = AppResult.Success(Account(42, "neo", null, false, null))

            override suspend fun week(
                server: Server,
                userId: Int,
                today: LocalDate,
                firstDay: DayOfWeek,
                zone: ZoneId,
            ): AppResult<WeekStats> {
                asked += "week $userId $today $firstDay ${zone.id}"
                return weekResult
            }

            override suspend fun allStats(
                server: Server,
                userId: Int,
            ): AppResult<AllStats> {
                asked += "all $userId"
                return AppResult.Success(all)
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
    fun loadsTheAccountThenThisWeekForThatUser() =
        runTest {
            val vm = YouViewModel(session, profile, clock, AppSettingsStore(InMemoryDataStore(AppSettings())))
            runCurrent()
            val st = vm.state.value
            assertEquals("neo" to 3, st.account?.username to st.week?.chapters)
            assertEquals(listOf("week 42 2026-11-21 ${WeekFields.of(Locale.getDefault()).firstDayOfWeek} UTC"), asked)
            assertEquals(false, st.statsFailed)
        }

    @Test
    fun failedStatsAreFlagged() =
        runTest {
            weekResult = AppResult.Failure(AppError.Network)
            val vm = YouViewModel(session, profile, clock, AppSettingsStore(InMemoryDataStore(AppSettings())))
            runCurrent()
            assertEquals(true to null, vm.state.value.statsFailed to vm.state.value.week)
        }

    @Test
    fun allStatsUseTheAccountId() =
        runTest {
            val vm = AllStatsViewModel(session, profile)
            runCurrent()
            assertEquals(all, vm.state.value.stats)
            assertEquals(listOf("all 42"), asked)
        }
}
