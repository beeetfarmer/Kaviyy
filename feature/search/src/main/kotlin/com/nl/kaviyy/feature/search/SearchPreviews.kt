package com.nl.kaviyy.feature.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.NamedRef
import com.nl.kaviyy.core.data.SearchChapter
import com.nl.kaviyy.core.data.SearchList
import com.nl.kaviyy.core.data.SearchResults
import com.nl.kaviyy.core.data.SearchSeries
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

/** The canvas example ("ember"). */
internal val previewResults =
    SearchUiState(
        results =
            Results.Loaded(
                "ember",
                SearchResults(
                    series =
                        listOf(
                            SearchSeries(7, "Ember Road", "Manga", 12),
                            SearchSeries(8, "Ember Road: Ashes", "Light Novels", 6),
                        ),
                    chapters =
                        listOf(
                            SearchChapter(87, "Embers in the Snow", "87"),
                            SearchChapter(102, "The Last Ember", "102"),
                        ),
                    people = listOf(NamedRef(5, "Emberly Moss")),
                    genres = listOf(NamedRef(1, "Ember Saga"), NamedRef(2, "Fire Magic")),
                    tags = listOf(NamedRef(3, "Post-apocalyptic")),
                    readingLists = listOf(SearchList(4, "Ember Road Timeline", 24)),
                ),
            ),
    )

internal val previewRecents = SearchUiState(recents = listOf("ember", "lantern", "Mira Okafor"))

@Composable
internal fun SearchPreview(
    appearance: Appearance,
    state: SearchUiState = previewResults,
    query: String = "ember",
    actions: SearchActions = SearchActions(),
) = KavitaTheme(appearance = appearance) { SearchScreen(state, TextFieldState(query), actions) }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SearchLightPreview() = SearchPreview(Appearance.Light)

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SearchDarkPreview() = SearchPreview(Appearance.Dark)

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SearchRecentsPreview() = SearchPreview(Appearance.Light, previewRecents, query = "")
