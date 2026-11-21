package com.nl.kaviyy.feature.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.paging.compose.collectAsLazyPagingItems
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
    fun ownersSeeEditAndTheSheetSavesAndAsksBeforeDeleting() {
        val calls = mutableListOf<String>()
        compose.setContent {
            CollectionScreen(
                CollectionUiState(collection = previewCollections[0], editable = true),
                kotlinx.coroutines.flow
                    .flowOf(
                        androidx.paging.PagingData.empty<com.nl.kaviyy.core.database.SeriesEntity>(),
                    ).collectAsLazyPagingItems(),
                {},
                {},
                onEdit = { calls += "edit" },
            )
        }
        compose.onNodeWithText("Edit").performClick()
        assertEquals(listOf("edit"), calls)
    }

    @Test
    fun screenshotEditCollection() =
        shot("collection_edit") {
            CollectionPreview()
            EditListSheet(
                EditListForm(
                    "Edit collection",
                    "Delete collection",
                    busy = false,
                    failed = false,
                    confirmDelete = true,
                ),
                androidx.compose.foundation.text.input
                    .TextFieldState("Comfort reads"),
                androidx.compose.foundation.text.input
                    .TextFieldState(),
                EditListActions(),
            )
        }

    @Test
    fun readingListRowsOpenAndTheListContinuesAtTheFirstUnfinishedItem() {
        val calls = mutableListOf<String>()
        compose.setContent {
            com.nl.kaviyy.core.designsystem.theme.KavitaTheme {
                ReadingListScreen(previewReadingList, ReadingListActions(onRead = { s, c -> calls += "read $s $c" }))
            }
        }
        compose.onNodeWithText("2 of 4 read").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Iron Man").performClick()
        assertEquals(listOf("read 1 102", "read 3 103"), calls)
    }

    @Test
    fun listsShowReadingListProgress() {
        var opened = 0
        compose.setContent { ListsScreen(previewLists, ListsActions(onOpenReadingList = { opened = it })) }
        compose.onNodeWithText("14 / 24").assertExists()
        compose.onNodeWithText("Cosmic arcs").performClick()
        assertEquals(3, opened)
    }

    @Test
    fun ownersArrangeItemsWithMoveAndRemove() {
        val calls = mutableListOf<String>()
        compose.setContent {
            com.nl.kaviyy.core.designsystem.theme.KavitaTheme {
                ReadingListScreen(
                    previewReadingList.copy(editable = true, arranging = true),
                    ReadingListActions(
                        onMove = { i, by -> calls += "move $i $by" },
                        onRemove = { calls += "remove ${it.id}" },
                        onEdit = { calls += "edit" },
                    ),
                )
            }
        }
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Move down")[0].performClick()
        compose.onAllNodesWithContentDescription("Move up")[1].performClick()
        compose.onAllNodesWithContentDescription("Remove from list")[3].performClick()
        compose.onNodeWithText("Name & details").performClick()
        assertEquals(listOf("move 0 1", "move 1 -1", "remove 4", "edit"), calls)
    }

    @Test
    fun screenshotReadingListArranging() =
        shot("reading_list_arranging") {
            com.nl.kaviyy.core.designsystem.theme.KavitaTheme {
                ReadingListScreen(previewReadingList.copy(editable = true, arranging = true), ReadingListActions())
            }
        }

    @Test
    fun screenshotNewReadingList() =
        shot("lists_new_reading_list") {
            ListsPreview()
            NewReadingListSheet(
                NewListState(open = true),
                androidx.compose.foundation.text.input
                    .TextFieldState(),
                {},
                {},
            )
        }

    @Test
    fun screenshotReadingListLight() = shot("reading_list_light") { ReadingListPreview() }

    @Test
    fun screenshotReadingListDark() = shot("reading_list_dark") { ReadingListPreview(Appearance.Dark) }

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
