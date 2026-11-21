package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AdminRepository
import com.nl.kaviyy.core.data.AdminServerInfo
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.ServerJob
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class AdminTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server = requireNotNull(previewYou.server)
    private val scans = mutableListOf<Int?>()
    private var jobsResult: AppResult<List<ServerJob>> = AppResult.Success(listOf(ServerJob("Backup", 0)))

    private val admin =
        object : AdminRepository {
            override suspend fun refreshMetadata(
                server: Server,
                libraryId: Int,
                seriesId: Int,
            ) = AppResult.Success(Unit)

            override suspend fun scan(
                server: Server,
                libraryId: Int,
                seriesId: Int,
            ) = AppResult.Success(Unit)

            override suspend fun updateSummary(
                server: Server,
                seriesId: Int,
                summary: String,
            ) = AppResult.Success(Unit)

            override suspend fun serverInfo(server: Server) = AppResult.Success(AdminServerInfo("0.9.1", false, 0))

            override suspend fun jobs(server: Server) = jobsResult

            override suspend fun recentlyAdded(server: Server) = AppResult.Success(listOf("Ember Road" to 0L))

            override suspend fun scanLibrary(
                server: Server,
                libraryId: Int?,
            ): AppResult<Unit> {
                scans += libraryId
                return if (libraryId == 2) AppResult.Failure(AppError.Forbidden) else AppResult.Success(Unit)
            }
        }

    private fun <T> fake(
        type: Class<T>,
        answer: (String) -> Any?,
    ): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, _ ->
                answer(m.name)
                    ?: error("unused: ${m.name}")
            },
        )

    private val session = fake(AuthSession::class.java) { if (it == "getActiveServer") flowOf(server) else null }
    private val catalog =
        fake(CatalogRepository::class.java) {
            if (it ==
                "libraries"
            ) {
                flowOf(listOf(LibraryEntity(1, 1, "Manga", 0)))
            } else {
                null
            }
        }

    @Test
    fun loadsTheOverviewAndReportsScans() =
        runTest {
            val vm = AdminViewModel(session, admin, catalog)
            runCurrent()
            val st = vm.state.value
            assertEquals("0.9.1" to listOf("Manga"), st.info?.version to st.libraries.map { it.name })
            assertEquals(listOf("Backup") to false, st.jobs.map { it.title } to st.failed)
            vm.scan(1)
            runCurrent()
            assertEquals(true, vm.state.value.scanStarted)
            vm.scan(2)
            vm.scan(null)
            runCurrent()
            assertEquals(listOf(1, 2, null), scans)
            assertEquals(true, vm.state.value.scanStarted) // the last (scan all) went through
        }

    @Test
    fun aFailedCallIsFlaggedButTheRestStillShows() =
        runTest {
            jobsResult = AppResult.Failure(AppError.Forbidden)
            val vm = AdminViewModel(session, admin, catalog)
            runCurrent()
            assertEquals(
                true to "0.9.1",
                vm.state.value.failed to
                    vm.state.value.info
                        ?.version,
            )
        }

    @Test
    fun scanRowsCallWithTheirLibrary() {
        val calls = mutableListOf<Int?>()
        compose.setContent { AdminPreviewWith(AdminActions(onScan = { calls += it })) }
        compose.onNodeWithText("Comics").performClick()
        compose.onNodeWithText("Scan All Libraries").performScrollTo().performClick()
        assertEquals(listOf(2, null), calls)
    }

    @Test
    fun screenshotAdminLight() = shot("admin_light", Appearance.Light)

    @Test
    fun screenshotAdminDark() = shot("admin_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent { AdminPreview(appearance) }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@androidx.compose.runtime.Composable
private fun AdminPreviewWith(actions: AdminActions) =
    com.nl.kaviyy.core.designsystem.theme
        .KavitaTheme { AdminScreen(previewAdmin, actions) }
