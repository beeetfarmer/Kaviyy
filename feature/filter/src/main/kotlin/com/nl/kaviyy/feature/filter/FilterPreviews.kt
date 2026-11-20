package com.nl.kaviyy.feature.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesFilter
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

/** The canvas example: four rules, last chapter added, Manga + Comics. */
internal val previewFilter =
    FilterUiState(
        draft =
            SeriesFilter(
                rules =
                    listOf(
                        FilterRule(
                            FilterField.Genres,
                            Comparison.Contains,
                            listOf(RuleValue("1", "Fantasy"), RuleValue("2", "Adventure")),
                        ),
                        FilterRule(FilterField.ReadProgress, Comparison.LessThan, listOf(RuleValue("100"))),
                        FilterRule(FilterField.ReleaseYear, Comparison.GreaterThan, listOf(RuleValue("2015"))),
                        FilterRule(FilterField.AgeRating, Comparison.LessThanEqual, listOf(RuleValue("9", "Teen"))),
                    ),
                sort = SeriesSort.RecentlyUpdated,
                libraryIds = listOf(1, 2),
            ),
        currentLibrary = 1,
    )

internal val previewLibraries =
    listOf(LibraryEntity(1, 1, "Manga", 0), LibraryEntity(1, 2, "Comics", 1), LibraryEntity(1, 3, "Books", 2))

/** Sheet content on the sheet ground, as it appears over Library. */
@Composable
internal fun FilterPreview(
    appearance: Appearance,
    state: FilterUiState = previewFilter,
) = KavitaTheme(appearance = appearance) {
    Column(Modifier.fillMaxSize().background(Kavita.colors.background)) {
        FilterContent(state, previewLibraries, remember { TextFieldState() }, FilterActions())
    }
}

internal val previewRule =
    previewFilter.copy(
        page = FilterPage.Rule,
        rule = RuleDraft(FilterField.Genres, selected = listOf(RuleValue("4", "Fantasy"))),
        options =
            Options.Loaded(
                listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy").mapIndexed {
                    i,
                    t,
                    ->
                    RuleValue("$i", t)
                },
            ),
    )

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun FilterLightPreview() = FilterPreview(Appearance.Light)

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun FilterDarkPreview() = FilterPreview(Appearance.Dark)

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun FilterRulePreview() = FilterPreview(Appearance.Light, previewRule)
