package com.nl.kaviyy.feature.series

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CblPreview
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.database.ReadingListEntity
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
class ReadingListsSheetTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server =
        Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url, username = "neo")
    private val active = MutableStateFlow<Server?>(server)
    private val rows =
        MutableStateFlow(
            listOf(
                ReadingListRow(ReadingListEntity(1, 1, "Civil War order", owner = "neo"), 0),
                ReadingListRow(ReadingListEntity(1, 2, "Admin's picks", owner = "admin", promoted = true), 0),
                ReadingListRow(ReadingListEntity(1, 3, "Cosmic arcs", owner = "neo"), 0),
            ),
        )
    private val calls = mutableListOf<String>()
    private var holding = setOf(1)

    private val repo =
        object : ReadingListRepository {
            override fun lists(server: Server) = rows

            override fun list(
                server: Server,
                id: Int,
            ) = rows.map { r -> r.firstOrNull { it.list.id == id } }

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
            ) = AppResult.Success(holding)

            override suspend fun create(
                server: Server,
                title: String,
            ): AppResult<Int> {
                calls += "create $title"
                return AppResult.Success(7)
            }

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
            ): AppResult<Unit> {
                calls += "add $seriesId to $id"
                holding = holding + id
                return AppResult.Success(Unit)
            }

            override suspend fun previewCbl(
                server: Server,
                fileName: String,
                bytes: ByteArray,
            ): AppResult<CblPreview> = error("unused")

            override suspend fun importCbl(
                server: Server,
                preview: CblPreview,
            ): AppResult<Int> = error("unused")
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
    fun addsTheSeriesToOwnListsOnceAndToANewOne() =
        runTest {
            val vm = ReadingListsSheetViewModel(session, repo)
            backgroundScope.launch { vm.state.collect {} }
            vm.open(42)
            runCurrent()
            assertEquals(
                listOf("Civil War order", "Cosmic arcs"),
                vm.state.value.lists
                    .map { it.title },
            )
            vm.add(vm.state.value.lists[0]) // already holds it: ignored
            vm.add(vm.state.value.lists[1])
            runCurrent()
            assertEquals(setOf(1, 3), vm.state.value.holding)
            vm.newName.setTextAndPlaceCursorAtEnd(" Spring ")
            vm.create()
            runCurrent()
            assertEquals(listOf("add 42 to 3", "create Spring", "add 42 to 7"), calls)
            assertEquals("", vm.newName.text.toString())
        }

    @Test
    fun screenshotSheetLight() {
        val state = ReadingListsSheetState(rows.value.map { it.list }.filter { it.owner == "neo" }, setOf(1))
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                ReadingListsSheetContent(
                    state,
                    TextFieldState(),
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/series_reading_lists_light.png")
    }
}
