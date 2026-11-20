package com.nl.kaviyy.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.CoverCard
import com.nl.kaviyy.core.designsystem.component.CoverCardSize
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.seriesCoverUrl

private const val GRID_COLUMNS = 3
private const val DESCRIPTION_LINES = 6

@Composable
fun BrowseRoute(
    onBack: () -> Unit,
    onOpenSeries: (seriesId: Int) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrowseScreen(state, viewModel.series.collectAsLazyPagingItems(), onBack, onOpenSeries)
}

/** [derived] People/genre/tag page: Library-style header and 3-column grid (DESIGN.md §7 screen 03 pattern). */
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    series: LazyPagingItems<SeriesEntity>,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
        contentPadding =
            PaddingValues(
                start = KavitaSpacing.screenHorizontal,
                end = KavitaSpacing.screenHorizontal,
                top = 8.dp,
                bottom = KavitaSpacing.contentBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(KavitaSpacing.gridRowGap),
        horizontalArrangement = Arrangement.spacedBy(KavitaSpacing.gridColumnGap),
    ) {
        fullWidth("back") {
            Column(Modifier.fillMaxWidth()) {
                CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.series_back), onBack)
            }
        }
        fullWidth("header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LargeTitleHeader(state.target.name, overline = stringResource(fieldLabel(state.target.field)))
                state.description?.let {
                    Text(
                        plainSummary(it),
                        style = Kavita.type.callout,
                        color = Kavita.colors.labelBody,
                        maxLines = DESCRIPTION_LINES,
                    )
                }
            }
        }
        items(state, series, onOpenSeries)
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.items(
    state: BrowseUiState,
    series: LazyPagingItems<SeriesEntity>,
    onOpen: (Int) -> Unit,
) {
    val refresh = series.loadState.refresh
    if (series.itemCount == 0) {
        when {
            refresh is LoadState.Error -> fullWidth("error") { Retry(series) }
            refresh is LoadState.NotLoading -> fullWidth("empty") { Hint(stringResource(R.string.browse_empty)) }
            else -> fullWidth("loading") { Hint(stringResource(R.string.series_loading)) }
        }
        return
    }
    items(series.itemCount, key = series.itemKey { it.id }) { index ->
        val s = series[index] ?: return@items
        val progress = rowState(s.pages, s.pagesRead, asPagesLeft = false)
        CoverCard(
            title = s.localizedName ?: s.name,
            subtitle = stateText(progress),
            color = coverColorFor(s.id.toLong()),
            onClick = { onOpen(s.id) },
            size = CoverCardSize.Grid,
            progress = (progress as? RowState.Percent)?.let { s.pagesRead.toFloat() / s.pages },
            imageUrl = state.server?.seriesCoverUrl(s.id),
        )
    }
    if (series.loadState.append is LoadState.Error) fullWidth("append-error") { Retry(series) }
}

@Composable
private fun Retry(series: LazyPagingItems<SeriesEntity>) =
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Hint(stringResource(R.string.browse_failed))
        SecondaryButton(stringResource(R.string.series_retry), series::retry)
    }
