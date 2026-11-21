package com.nl.kaviyy.feature.series

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ListSummary
import com.nl.kaviyy.core.data.ListsRepository
import com.nl.kaviyy.core.database.CollectionEntity
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
class CollectionsSheetTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val server =
        Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url, username = "neo")
    private val active = MutableStateFlow<Server?>(server)
    private val all =
        MutableStateFlow(
            listOf(
                CollectionEntity(1, 1, "Comfort reads", owner = "neo"),
                CollectionEntity(1, 2, "Theirs", owner = "trinity"),
                CollectionEntity(1, 3, "MAL sync", owner = "neo", source = 2),
                CollectionEntity(1, 4, "Award winners", owner = "neo"),
            ),
        )
    private val calls = mutableListOf<String>()
    private var result: AppResult<Unit> = AppResult.Success(Unit)

    private val lists =
        object : ListsRepository {
            override fun wantToRead(server: Server) = flowOf(ListSummary(0, false, emptyList(), 0))

            override suspend fun refreshWantToRead(server: Server) = AppResult.Success(Unit)

            override fun collections(server: Server) = all

            override fun collection(
                server: Server,
                id: Int,
            ) = all.map { l -> l.firstOrNull { it.id == id } }

            override suspend fun refreshCollections(server: Server) = AppResult.Success(Unit)

            override suspend fun collectionsWith(
                server: Server,
                seriesId: Int,
            ) = AppResult.Success(setOf(1))

            override suspend fun setInCollection(
                server: Server,
                collection: CollectionEntity,
                seriesId: Int,
                inIt: Boolean,
            ): AppResult<Unit> {
                calls += "${if (inIt) "add" else "remove"} ${collection.id} $seriesId"
                return result
            }

            override suspend fun createCollection(
                server: Server,
                title: String,
                seriesId: Int,
            ): AppResult<Unit> {
                calls += "create $title $seriesId"
                return result
            }

            override suspend fun updateCollection(
                server: Server,
                collection: CollectionEntity,
                title: String,
                summary: String?,
            ): AppResult<Unit> = error("unused")

            override suspend fun deleteCollection(
                server: Server,
                id: Int,
            ): AppResult<Unit> = error("unused")
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
    fun listsOnlyOwnManualCollectionsAndTogglesWithUndoOnFailure() =
        runTest {
            val vm = CollectionsSheetViewModel(session, lists)
            backgroundScope.launch { vm.state.collect {} }
            vm.open(42)
            runCurrent()
            assertEquals(
                listOf("Comfort reads", "Award winners"),
                vm.state.value.collections
                    .map { it.title },
            )
            assertEquals(setOf(1), vm.state.value.member)
            vm.toggle(vm.state.value.collections[1])
            runCurrent()
            assertEquals(setOf(1, 4), vm.state.value.member)
            result = AppResult.Failure(AppError.Network)
            vm.toggle(vm.state.value.collections[0])
            runCurrent()
            assertEquals(setOf(1, 4), vm.state.value.member) // undone
            assertEquals(AppError.Network, vm.state.value.error)
            assertEquals(listOf("add 4 42", "remove 1 42"), calls)
        }

    @Test
    fun createsANamedCollectionWithTheSeries() =
        runTest {
            val vm = CollectionsSheetViewModel(session, lists)
            backgroundScope.launch { vm.state.collect {} }
            vm.open(42)
            vm.create() // blank: ignored
            vm.newName.setTextAndPlaceCursorAtEnd("  Rainy days ")
            vm.create()
            runCurrent()
            assertEquals(listOf("create Rainy days 42"), calls)
            assertEquals("", vm.newName.text.toString())
        }

    @Test
    fun sheetTogglesAndShootsLightAndDark() {
        val toggled = mutableListOf<String>()
        val state = CollectionsSheetState(all.value.filter { it.owner == "neo" && it.source == 0 }, setOf(1))
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                CollectionsSheetContent(state, TextFieldState(), {}, { toggled += it.title }, {})
            }
        }
        compose.onNodeWithText("Award winners").performClick()
        assertEquals(listOf("Award winners"), toggled)
        compose.onRoot().captureRoboImage("src/test/screenshots/series_collections_light.png")
    }

    @Test
    fun screenshotSheetDark() {
        val state = CollectionsSheetState(all.value.filter { it.owner == "neo" && it.source == 0 }, setOf(1))
        compose.setContent {
            KavitaTheme(appearance = Appearance.Dark) {
                CollectionsSheetContent(state, TextFieldState("Rainy days"), {}, {}, {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/series_collections_dark.png")
    }
}
