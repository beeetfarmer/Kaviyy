package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.HomeSection
import com.nl.kaviyy.core.data.ListSort
import com.nl.kaviyy.core.data.parseHomeLayout
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1400dp-xhdpi")
class HomeLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun rowsMoveToggleAndSort() {
        val calls = mutableListOf<String>()
        val actions =
            HomeLayoutActions(
                onToggle = { s, v -> calls += "toggle $s $v" },
                onMove = { s, by -> calls += "move $s $by" },
                onSort = { k, s -> calls += "sort $k $s" },
            )
        compose.setContent { KavitaTheme { HomeLayoutScreen(HomeLayoutUiState(), actions) } }
        compose.onNodeWithContentDescription("Move On Deck up").performClick()
        compose.onNodeWithContentDescription("Move Continue Reading up").performClick() // first: disabled
        compose.onNodeWithContentDescription("Downloaded").performClick()
        compose.onNodeWithText("LIBRARIES ORDER").performScrollTo()
        compose.onNodeWithText("Scanned").performClick()
        assertEquals(listOf("move OnDeck -1", "toggle Downloaded true", "sort Libraries Recent"), calls)
    }

    @Test
    fun viewModelSavesTheLayout() =
        runTest {
            val store = AppSettingsStore(InMemoryDataStore(AppSettings()))
            val vm = HomeLayoutViewModel(store)
            vm.move(HomeSection.Libraries, -1)
            vm.toggle(HomeSection.OnDeck, false)
            vm.sort(ShelfKind.Collections, ListSort.NameDesc)
            advanceUntilIdle()
            val saved = store.settings.first()
            val layout = parseHomeLayout(saved.homeLayout)
            val names = layout.map { it.section }
            assertEquals(names.indexOf(HomeSection.ReadingLists) - 1, names.indexOf(HomeSection.Libraries))
            assertEquals(false, layout.first { it.section == HomeSection.OnDeck }.visible)
            assertEquals("NameDesc", saved.collectionSort)
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
            KavitaTheme(appearance = appearance) { HomeLayoutScreen(HomeLayoutUiState(), HomeLayoutActions()) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
