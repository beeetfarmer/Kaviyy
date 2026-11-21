package com.nl.kaviyy.feature.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val done = LoadState.NotLoading(endOfPaginationReached = true)

    @Test
    fun showsLibrariesProgressAndActions() {
        var selected = 0
        var toggled = 0
        var opened = 0
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                val items =
                    flowOf(
                        PagingData.from(previewSeries, LoadStates(done, done, done)),
                    ).collectAsLazyPagingItems()
                LibraryScreen(
                    LibraryUiState(null, previewLibraries, 1),
                    items,
                    LibraryActions(onSelect = { selected = it }, onToggleLayout = { toggled++ }, onOpenSeries = {
                        opened =
                            it
                    }),
                )
            }
        }
        compose.onNodeWithText("Comics").performClick()
        assertEquals(2, selected)
        compose.onNodeWithText("EPUB · Unread").assertExists()
        compose.onNodeWithText("40%").assertExists()
        compose.onNodeWithContentDescription("Switch to list view").performClick()
        assertEquals(1, toggled)
        compose.onNodeWithText("North of Quiet").performClick()
        assertEquals(2, opened)
        compose.onNodeWithText("Recently Added").assertExists() // default sort chip
    }

    @Test
    fun noLibraries() {
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                val items =
                    flowOf(
                        PagingData.empty<com.nl.kaviyy.core.database.SeriesEntity>(),
                    ).collectAsLazyPagingItems()
                LibraryScreen(LibraryUiState(libraries = emptyList()), items, LibraryActions())
            }
        }
        compose.onNodeWithText("No libraries").assertExists()
    }

    @Test
    fun chipsReportTheirActions() {
        var unread = 0
        var sheet: LibrarySheet? = null
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                val items =
                    flowOf(
                        PagingData.from(previewSeries, LoadStates(done, done, done)),
                    ).collectAsLazyPagingItems()
                LibraryScreen(
                    LibraryUiState(null, previewLibraries, 1),
                    items,
                    LibraryActions(onToggleUnread = { unread++ }, onShowSheet = { sheet = it }),
                )
            }
        }
        compose.onAllNodesWithText("Unread").onFirst().performClick() // the chip; cards also say "Unread"
        assertEquals(1, unread)
        compose.onNodeWithText("Genre").performClick()
        assertEquals(LibrarySheet.Genre, sheet)
    }

    @Test
    fun screenshotLight() = shot("library_light") { LibraryPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = shot("library_dark") { LibraryPreview(Appearance.Dark) }

    @Test
    fun gridWidensOnTablets() {
        assertEquals(listOf(3, 3, 5, 5, 6), listOf(360, 599, 600, 839, 840).map(::gridColumns))
    }

    @Test
    @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun screenshotTablet() = shot("library_tablet_light") { LibraryPreview(Appearance.Light) }

    @Test
    fun screenshotList() = shot("library_list_light") { LibraryPreview(Appearance.Light, grid = false) }

    /** Through the compose rule so paging has delivered before the capture. */
    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
