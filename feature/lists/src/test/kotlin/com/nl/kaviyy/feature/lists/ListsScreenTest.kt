package com.nl.kaviyy.feature.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.ListSummary
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesFilter
import com.nl.kaviyy.core.designsystem.theme.Appearance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ListsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun wantToReadCardSummarisesAndOpens() {
        var opened = 0
        compose.setContent {
            ListsScreen(previewLists, ListsActions(onOpenWantToRead = { opened++ }))
        }
        compose.onNodeWithText("18 series · 3 updated this week").assertExists()
        compose.onNodeWithText("Want to Read").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun fullFirstPageShowsAPlusAndNoUpdatesShowsJustTheCount() {
        compose.setContent {
            ListsPreview(previewLists.copy(wantToRead = ListSummary(50, true, emptyList(), 0)))
        }
        compose.onNodeWithText("50+ series").assertExists()
    }

    @Test
    fun wantToReadGridShowsProgressAndTheFilterChip() {
        val calls = mutableListOf<String>()
        val filter =
            SeriesFilter(rules = listOf(FilterRule(FilterField.Genres, Comparison.Contains, listOf(RuleValue("3")))))
        compose.setContent {
            WantToReadPreview(
                state = WantToReadUiState(filter = filter),
                actions =
                    WantToReadActions(
                        onOpenFilter = { calls += "filter" },
                        onOpenSeries = { calls += "series $it" },
                        onBack = { calls += "back" },
                    ),
            )
        }
        compose.onNodeWithText("40%").assertExists()
        compose.onNodeWithText("Filter · 1").performClick()
        compose.onNodeWithText("North of Quiet").performClick()
        assertEquals(listOf("filter", "series 2"), calls)
    }

    @Test
    fun collectionsShowFourWithSeeAllAndOpen() {
        val calls = mutableListOf<String>()
        compose.setContent {
            ListsScreen(
                previewLists,
                ListsActions(onOpenCollection = { calls += "open $it" }, onSeeAllCollections = { calls += "all" }),
            )
        }
        compose.onNodeWithText("Re-read soon").assertDoesNotExist() // fifth: behind See All
        compose.onNodeWithText("Smart").assertExists()
        compose.onNodeWithText("40 series").assertExists()
        compose.onNodeWithText("Comfort reads").performClick()
        compose.onNodeWithText("See All").performClick()
        assertEquals(listOf("open 1", "all"), calls)
    }

    @Test
    fun collectionScreenShowsTitleSummaryAndSeries() {
        compose.setContent { CollectionPreview() }
        compose.onNodeWithText("MAL: Isekai Gems").assertExists()
        compose.onNodeWithText("Synced from a MyAnimeList interest stack.").assertExists()
        compose.onNodeWithText("North of Quiet").assertExists()
    }

    @Test
    fun screenshotCollection() = shot("collection_light") { CollectionPreview() }

    @Test
    fun screenshotAllCollectionsDark() =
        shot("collections_all_dark") {
            com.nl.kaviyy.core.designsystem.theme.KavitaTheme(appearance = Appearance.Dark) {
                AllCollectionsScreen(previewLists, ListsActions(), {})
            }
        }

    @Test
    fun screenshotListsLight() = shot("lists_light") { ListsPreview() }

    @Test
    fun screenshotListsDark() = shot("lists_dark") { ListsPreview(appearance = Appearance.Dark) }

    @Test
    fun screenshotWantToRead() = shot("want_to_read_light") { WantToReadPreview() }

    @Test
    fun screenshotWantToReadDark() = shot("want_to_read_dark") { WantToReadPreview(Appearance.Dark) }
}
