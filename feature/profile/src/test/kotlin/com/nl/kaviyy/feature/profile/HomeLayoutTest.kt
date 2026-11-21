package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.HomeSection
import com.nl.kaviyy.core.data.HomeSectionPref
import com.nl.kaviyy.core.data.ListSort
import com.nl.kaviyy.core.data.ListsRepository
import com.nl.kaviyy.core.data.parseHomeLayout
import com.nl.kaviyy.core.data.withItems
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1400dp-xhdpi")
class HomeLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    private val sample =
        HomeLayoutUiState(
            parseHomeLayout("Collection:1").withItems(listOf(1, 2), listOf(5)),
            collectionNames = mapOf(1 to "Weekend Reads", 2 to "Award Winners"),
            libraryNames = mapOf(5 to "Manga"),
        )

    @Test
    fun rowsMoveToggleAndSort() {
        val calls = mutableListOf<String>()
        val actions =
            HomeLayoutActions(
                onToggle = { p, v -> calls += "toggle ${p.section}:${p.id} $v" },
                onMove = { p, by -> calls += "move ${p.section}:${p.id} $by" },
                onReadingListSort = { calls += "sort $it" },
            )
        compose.setContent { KavitaTheme { HomeLayoutScreen(sample, actions) } }
        compose.onNodeWithContentDescription("Move On Deck up").performClick()
        compose.onNodeWithContentDescription("Move Continue Reading up").performClick() // first: disabled
        compose.onNodeWithContentDescription("Award Winners").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Move Manga down").performClick() // last: disabled
        compose.onNodeWithContentDescription("Move Manga up").performClick()
        compose.onNodeWithText("READING LISTS ORDER").performScrollTo()
        compose.onNodeWithText("A–Z").performClick()
        assertEquals(
            listOf("move OnDeck:0 -1", "toggle Collection:2 true", "move Library:5 -1", "sort NameAsc"),
            calls,
        )
    }

    @Test
    fun viewModelListsEveryCollectionAndLibraryAndSavesTheLayout() =
        runTest {
            val store = AppSettingsStore(InMemoryDataStore(AppSettings()))
            val vm = HomeLayoutViewModel(store, fakeSession(), fakeLists(), fakeCatalog())
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
            val rows =
                vm.state.value.layout
                    .filter { it.section.perItem }
            assertEquals(
                listOf(HomeSection.Collection to 7, HomeSection.Library to 2),
                rows.map { it.section to it.id },
            )
            assertEquals("Shonen", vm.state.value.collectionNames[7])
            vm.toggle(rows[0], true)
            advanceUntilIdle()
            vm.move(
                vm.state.value.layout
                    .first { it.section == HomeSection.Collection },
                -1,
            )
            vm.sortReadingLists(ListSort.NameDesc)
            advanceUntilIdle()
            val saved = store.settings.first()
            val layout = parseHomeLayout(saved.homeLayout)
            val collection = layout.indexOfFirst { it.section == HomeSection.Collection }
            assertEquals(HomeSectionPref(HomeSection.Collection, true, 7), layout[collection])
            assertEquals(HomeSection.Downloaded, layout[collection + 1].section) // moved up past it
            assertEquals("NameDesc", saved.readingListSort)
        }

    @Test
    fun screenshotLight() = shot("home_layout_light", Appearance.Light)

    @Test
    fun screenshotDark() = shot("home_layout_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) { HomeLayoutScreen(sample, HomeLayoutActions()) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

private fun server() = Server(ServerId(1), "S", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)

private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        answer(method.name)
    } as T

private fun fakeSession(): AuthSession =
    proxy { name -> if (name == "getActiveServer") flowOf(server()) else error("unused: $name") }

private fun fakeLists(): ListsRepository =
    proxy { name ->
        if (name == "collections") flowOf(listOf(CollectionEntity(1, 7, "Shonen"))) else error("unused: $name")
    }

private fun fakeCatalog(): CatalogRepository =
    proxy { name ->
        if (name ==
            "libraries"
        ) {
            flowOf(listOf(LibraryEntity(1, 2, "Manga", 0)))
        } else {
            error("unused: $name")
        }
    }
