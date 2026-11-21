package com.nl.kaviyy.feature.lists

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CblPreview
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.CblSavedFileDto
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class CblImportTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val uploads = mutableListOf<String>()
    private var importResult: AppResult<Int> = AppResult.Success(12)
    private val preview =
        CblPreview(
            "Civil War",
            found = 20,
            total = 24,
            missing = (1..10).map { "Iron Man #$it" },
            importable = true,
            saved = CblSavedFileDto("Civil War", "civil-war.cbl"),
        )

    private val repo =
        object : ReadingListRepository {
            override fun lists(server: Server) = flowOf(emptyList<ReadingListRow>())

            override fun list(
                server: Server,
                id: Int,
            ) = emptyFlow<ReadingListRow?>()

            override fun items(
                server: Server,
                id: Int,
            ) = flowOf(emptyList<ReadingListItemEntity>())

            override suspend fun refreshLists(server: Server) = AppResult.Success(Unit)

            override suspend fun refreshItems(
                server: Server,
                id: Int,
            ) = AppResult.Success(Unit)

            override suspend fun listsWith(
                server: Server,
                seriesId: Int,
            ): AppResult<Set<Int>> = error("unused")

            override suspend fun create(
                server: Server,
                title: String,
            ): AppResult<Int> = error("unused")

            override suspend fun update(
                server: Server,
                id: Int,
                title: String,
                summary: String?,
            ): AppResult<Unit> = error("unused")

            override suspend fun delete(
                server: Server,
                id: Int,
            ): AppResult<Unit> = error("unused")

            override suspend fun move(
                server: Server,
                item: ReadingListItemEntity,
                to: Int,
            ): AppResult<Unit> = error("unused")

            override suspend fun remove(
                server: Server,
                item: ReadingListItemEntity,
            ): AppResult<Unit> = error("unused")

            override suspend fun addSeries(
                server: Server,
                id: Int,
                seriesId: Int,
            ): AppResult<Unit> = error("unused")

            override suspend fun previewCbl(
                server: Server,
                fileName: String,
                bytes: ByteArray,
            ): AppResult<CblPreview> {
                uploads += "$fileName ${bytes.size}"
                return AppResult.Success(preview)
            }

            override suspend fun importCbl(
                server: Server,
                preview: CblPreview,
            ) = importResult
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

    private fun vm(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        CblImportViewModel(RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher(scheduler), session, repo)

    @Test
    fun aPickedFileIsPreviewedThenImportedAndOpenedOnce() =
        runTest {
            val file = tmp.newFile("Civil War (2006).cbl").apply { writeText("<ReadingList/>") }
            val vm = vm(testScheduler)
            vm.picked(Uri.fromFile(file))
            runCurrent()
            assertEquals(preview, vm.state.value.preview)
            assertEquals(listOf("Civil War _2006_.cbl 14"), uploads)
            importResult = AppResult.Failure(AppError.Network)
            vm.import()
            runCurrent()
            assertEquals(CblProblem.Failed to preview, vm.state.value.problem to vm.state.value.preview) // can retry
            importResult = AppResult.Success(12)
            vm.import()
            runCurrent()
            assertEquals(12, vm.state.value.imported)
            vm.consumeImported()
            assertEquals(CblImportState(), vm.state.value)
        }

    @Test
    fun oversizedFilesAreRefusedWithoutUploading() =
        runTest {
            val file = tmp.newFile("huge.cbl").apply { writeBytes(ByteArray(CBL_MAX_BYTES + 10)) }
            val vm = vm(testScheduler)
            vm.picked(Uri.fromFile(file))
            runCurrent()
            assertEquals(CblProblem.TooBig, vm.state.value.problem)
            assertEquals(emptyList(), uploads)
        }

    @Test
    fun namesAreMadeSafeAndEndInCbl() {
        assertEquals("list.cbl", safeCblName("list.cbl"))
        assertEquals("evil.cbl", safeCblName("../../evil.cbl"))
        assertEquals("a_b_c.xml.cbl", safeCblName("a\"b;c.xml"))
        assertEquals("reading-list.cbl", safeCblName(null))
    }

    @Test
    fun sheetShowsTheMatchAndImports() {
        var imported = 0
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                CblImportSheet(CblImportState(open = true, preview = preview), {}, { imported++ })
            }
        }
        compose.onNodeWithText("20 of 24 issues found in your library.").assertExists()
        compose.onNodeWithText("and 2 more").assertExists()
        compose.onNodeWithText("Import reading list").performClick()
        assertEquals(1, imported)
        compose.onRoot().captureRoboImage("src/test/screenshots/cbl_import_light.png")
    }

    @Test
    fun screenshotSheetDark() {
        compose.setContent {
            KavitaTheme(appearance = Appearance.Dark) {
                CblImportSheet(CblImportState(open = true, preview = preview), {}, {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/cbl_import_dark.png")
    }
}
