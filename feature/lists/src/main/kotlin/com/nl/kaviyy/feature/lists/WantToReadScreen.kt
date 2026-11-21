package com.nl.kaviyy.feature.lists

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.designsystem.component.ButtonSize
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.CoverCard
import com.nl.kaviyy.core.designsystem.component.CoverCardSize
import com.nl.kaviyy.core.designsystem.component.FilterChip
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.seriesCoverUrl

private const val GRID_COLUMNS = 3
private const val PERCENT = 100
private const val SUMMARY_LINES = 6

data class WantToReadActions(
    val onBack: () -> Unit = {},
    val onOpenSeries: (Int) -> Unit = {},
    val onOpenFilter: () -> Unit = {},
)

@Composable
fun WantToReadRoute(
    actions: WantToReadActions,
    viewModel: WantToReadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WantToReadScreen(state, viewModel.series.collectAsLazyPagingItems(), actions)
}

/** [derived] Want to Read: Library-style header, a Filter chip (screen 09 sheet), 3-column grid. */
@Composable
fun WantToReadScreen(
    state: WantToReadUiState,
    series: LazyPagingItems<SeriesEntity>,
    actions: WantToReadActions,
    modifier: Modifier = Modifier,
) = SeriesGridScreen(
    state.server,
    series,
    actions.onBack,
    actions.onOpenSeries,
    stringResource(R.string.lists_want_to_read_empty),
    modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LargeTitleHeader(stringResource(R.string.lists_want_to_read))
        val count = state.filter.activeRules
        FilterChip(
            if (count > 0) {
                stringResource(R.string.lists_filter_count, count)
            } else {
                stringResource(R.string.lists_filter)
            },
            selected = true,
            onClick = actions.onOpenFilter,
            leadingIcon = KavitaIcons.Filter,
        )
    }
}

/** Back button, [header], then the series in a 3-column grid (Library screen 03 pattern). */
@Composable
internal fun SeriesGridScreen(
    server: Server?,
    series: LazyPagingItems<SeriesEntity>,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
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
                CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.lists_back), onBack)
            }
        }
        fullWidth("header") { header() }
        grid(server, series, emptyText, onOpenSeries)
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.grid(
    server: Server?,
    series: LazyPagingItems<SeriesEntity>,
    emptyText: String,
    onOpen: (Int) -> Unit,
) {
    val refresh = series.loadState.refresh
    if (series.itemCount == 0) {
        when {
            refresh is LoadState.Error -> {
                fullWidth("error") { Retry(series) }
            }

            refresh is LoadState.NotLoading -> {
                fullWidth("empty") { Hint(emptyText) }
            }

            else -> {
                fullWidth("loading") { Hint(stringResource(R.string.lists_loading)) }
            }
        }
        return
    }
    items(series.itemCount, key = series.itemKey { it.id }) { index ->
        val s = series[index] ?: return@items
        val percent = if (s.pages > 0) s.pagesRead * PERCENT / s.pages else 0
        CoverCard(
            title = s.localizedName ?: s.name,
            subtitle =
                when {
                    s.pagesRead == 0 -> stringResource(R.string.lists_unread)
                    percent >= PERCENT -> stringResource(R.string.lists_read)
                    else -> stringResource(R.string.lists_percent, percent)
                },
            color = coverColorFor(s.id.toLong()),
            onClick = { onOpen(s.id) },
            size = CoverCardSize.Grid,
            progress =
                s.pagesRead
                    .toFloat()
                    .div(s.pages)
                    .takeIf { s.pagesRead in 1 until s.pages },
            imageUrl = server?.seriesCoverUrl(s.id),
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
        Hint(stringResource(R.string.lists_failed))
        SecondaryButton(stringResource(R.string.lists_retry), series::retry)
    }

@Composable
private fun Hint(text: String) =
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = Kavita.type.callout,
        color = Kavita.colors.labelSecondary,
        textAlign = TextAlign.Center,
    )

/** [derived] A collection: "Collection" overline + title (+ Smart badge), its summary, then the series grid. */
@Composable
fun CollectionScreen(
    state: CollectionUiState,
    series: LazyPagingItems<SeriesEntity>,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
) = SeriesGridScreen(
    state.server,
    series,
    onBack,
    onOpenSeries,
    stringResource(R.string.lists_collection_empty),
    modifier,
) {
    val c = state.collection
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LargeTitleHeader(c?.title.orEmpty(), overline = stringResource(R.string.lists_collection)) {
            if (state.editable) SecondaryButton(stringResource(R.string.lists_edit), onEdit, size = ButtonSize.Small)
        }
        if (c?.smart == true) SmartBadge()
        c?.summary?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = Kavita.type.callout, color = Kavita.colors.labelBody, maxLines = SUMMARY_LINES)
        }
    }
}
