package com.nl.kaviyy.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class SearchScreenTest {
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
    fun resultsShowGroupsCountsAndOpenHits() {
        val opened = mutableListOf<SearchHit>()
        var scope: SearchScope? = null
        compose.setContent {
            SearchPreview(
                Appearance.Light,
                actions = SearchActions(onOpen = { opened += it }, onScope = { scope = it }),
            )
        }
        compose.onNodeWithText("3 results").assertExists()
        compose.onNodeWithText("Manga · 12 volumes").assertExists()
        compose.onNodeWithText("Ember Road").performClick()
        compose.onNodeWithText("Fire Magic").performScrollTo().performClick()
        assertEquals(listOf(SearchHit.Series(7), SearchHit.Genre(2, "Fire Magic")), opened)
        compose.onNodeWithText("People").performClick()
        assertEquals(SearchScope.People, scope)
    }

    @Test
    fun recentsFillTheQueryAndClear() {
        var recent: String? = null
        var cleared = 0
        var home = 0
        compose.setContent {
            SearchPreview(
                Appearance.Light,
                previewRecents,
                query = "",
                actions =
                    SearchActions(
                        onRecent = { recent = it },
                        onClearRecents = { cleared++ },
                        onHome = { home++ },
                    ),
            )
        }
        compose.onNodeWithText("lantern").performClick()
        compose.onNodeWithText("Clear Recent Searches").performClick()
        compose.onNodeWithContentDescription("Back to tabs").performClick()
        assertEquals("lantern" to 1, recent to cleared)
        assertEquals(1, home)
    }

    @Test
    fun noResultsSaysSo() {
        compose.setContent {
            SearchPreview(
                Appearance.Light,
                SearchUiState(
                    results =
                        Results.Loaded(
                            "zzz",
                            com.nl.kaviyy.core.data
                                .SearchResults(),
                        ),
                ),
            )
        }
        compose.onNodeWithText("No results for \"zzz\".").assertExists()
    }

    @Test
    fun offlineResultsSayTheyAreDownloads() {
        compose.setContent {
            SearchPreview(
                Appearance.Light,
                SearchUiState(
                    results =
                        Results.Loaded(
                            "ember",
                            com.nl.kaviyy.core.data.SearchResults(
                                series =
                                    listOf(
                                        com.nl.kaviyy.core.data
                                            .SearchSeries(7, "Ember Road", "Manga", 2),
                                    ),
                                offline = true,
                            ),
                        ),
                ),
            )
        }
        compose.onNodeWithText("Can't reach your server. Showing matching downloads.").assertExists()
        compose.onNodeWithText("Ember Road").assertExists()
    }

    @Test
    fun screenshotLight() = shot("search_light") { SearchPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = shot("search_dark") { SearchPreview(Appearance.Dark) }

    @Test
    fun screenshotRecents() =
        shot("search_recents_light") { SearchPreview(Appearance.Light, previewRecents, query = "") }
}
