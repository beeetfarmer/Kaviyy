package com.nl.kaviyy.feature.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.nl.kaviyy.core.data.ListSummary
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import kotlinx.coroutines.flow.flowOf

internal val previewLists = ListsUiState(wantToRead = ListSummary(18, false, listOf(1, 2, 3), 3))

@Composable
internal fun ListsPreview(
    state: ListsUiState = previewLists,
    appearance: Appearance = Appearance.Light,
) = KavitaTheme(appearance = appearance) { ListsScreen(state, ListsActions()) }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ListsLightPreview() = ListsPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ListsDarkPreview() = ListsPreview(appearance = Appearance.Dark)

internal val previewWantToRead =
    listOf("Ember Road", "North of Quiet", "The Lantern Keeper", "Salt & Static", "Paper Moons").mapIndexed { i, t ->
        SeriesEntity(1, i + 1, 1, t, t, format = 0, pages = 100, pagesRead = listOf(0, 40, 0, 100, 12)[i])
    }

private val done = LoadState.NotLoading(endOfPaginationReached = true)

@Composable
internal fun WantToReadPreview(
    appearance: Appearance = Appearance.Light,
    state: WantToReadUiState = WantToReadUiState(),
    actions: WantToReadActions = WantToReadActions(),
) = KavitaTheme(appearance = appearance) {
    val items = flowOf(PagingData.from(previewWantToRead, LoadStates(done, done, done))).collectAsLazyPagingItems()
    WantToReadScreen(state, items, actions)
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun WantToReadLightPreview() = WantToReadPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun WantToReadDarkPreview() = WantToReadPreview(Appearance.Dark)
