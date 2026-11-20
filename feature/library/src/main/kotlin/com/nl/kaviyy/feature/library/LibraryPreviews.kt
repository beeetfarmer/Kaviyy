package com.nl.kaviyy.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import kotlinx.coroutines.flow.flowOf

internal val previewLibraries =
    listOf("Manga", "Comics", "Books", "Light Novels").mapIndexed { i, n -> LibraryEntity(1, i + 1, n, 0) }

private val titles =
    listOf(
        "Ember Road",
        "North of Quiet",
        "Glasshouse Saints",
        "Paper Moths",
        "Tidewater Nine",
        "Signal Fires",
        "Hollow Crown Café",
        "Moonwell Academy",
        "Last Train West",
    )

private val readPages = listOf(0, 40, 100, 0, 12, 0, 100, 3, 0)

internal val previewSeries =
    titles.mapIndexed { i, t ->
        val read = readPages[i]
        SeriesEntity(1, i + 1, 1, t, t, format = if (i == 3) 3 else 0, pages = 100, pagesRead = read)
    }

private val done = LoadState.NotLoading(endOfPaginationReached = true)

@Composable
internal fun LibraryPreview(
    appearance: Appearance,
    grid: Boolean = true,
) = KavitaTheme(appearance = appearance) {
    val items =
        flowOf(PagingData.from(previewSeries, LoadStates(done, done, done))).collectAsLazyPagingItems()
    LibraryScreen(LibraryUiState(null, previewLibraries, 1, grid), items, LibraryActions())
}

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun LibraryLightPreview() = LibraryPreview(Appearance.Light)

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun LibraryDarkPreview() = LibraryPreview(Appearance.Dark)

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun LibraryListPreview() = LibraryPreview(Appearance.Light, grid = false)
