package com.nl.kaviyy.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.seriesCoverUrl

/** More than this many libraries: the segmented control scrolls (DESIGN.md §7 screen 03). */
private const val MAX_FIXED_SEGMENTS = 4
private const val SKELETON_COUNT = 9
private const val GRID_COLUMNS = 3

@Composable
fun LibraryRoute(
    onOpenSeries: (seriesId: Int) -> Unit,
    onOpenFilter: (libraryId: Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val series = viewModel.series.collectAsLazyPagingItems()
    val actions =
        LibraryActions(
            onSelect = viewModel::select,
            onToggleLayout = viewModel::toggleLayout,
            onOpenSeries = onOpenSeries,
            onRetryLibraries = viewModel::retry,
            onToggleUnread = viewModel::toggleUnread,
            onShowSheet = viewModel::showSheet,
            onSort = viewModel::setSort,
            onPick = viewModel::pick,
            onOpenFilter = { onOpenFilter(state.selectedId) },
            onOpenPinned = viewModel::openPinned,
        )
    LibraryScreen(state, series, actions, modifier)
    LibrarySheets(state, actions)
}

data class LibraryActions(
    val onSelect: (libraryId: Int) -> Unit = {},
    val onToggleLayout: () -> Unit = {},
    val onOpenSeries: (seriesId: Int) -> Unit = {},
    val onRetryLibraries: () -> Unit = {},
    val onToggleUnread: () -> Unit = {},
    val onShowSheet: (LibrarySheet?) -> Unit = {},
    val onSort: (com.nl.kaviyy.core.data.SeriesSort) -> Unit = {},
    val onPick: (com.nl.kaviyy.core.data.RuleValue?) -> Unit = {},
    val onOpenFilter: () -> Unit = {},
    val onOpenPinned: (com.nl.kaviyy.core.data.SavedFilter) -> Unit = {},
)

/** Screen 03 (DESIGN.md §7). TODO(api): the count line needs a total; the list endpoint returns none in the spec. */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    series: LazyPagingItems<SeriesEntity>,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    val coverUrl: (Int) -> String? = { id -> state.server?.seriesCoverUrl(id) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (state.grid) GRID_COLUMNS else 1),
        modifier = modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
        contentPadding =
            PaddingValues(
                start = KavitaSpacing.screenHorizontal,
                end = KavitaSpacing.screenHorizontal,
                top = KavitaSpacing.topBelowStatusBar,
                bottom = KavitaSpacing.contentBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(if (state.grid) KavitaSpacing.gridRowGap else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(KavitaSpacing.gridColumnGap),
    ) {
        fullWidth("header") { Header(state.grid, actions.onToggleLayout) }
        val libraries = state.libraries
        when {
            libraries == null -> {
                Unit
            }

            libraries.isEmpty() && state.error != null -> {
                fullWidth("error") { ErrorBlock(actions.onRetryLibraries) }
            }

            libraries.isEmpty() -> {
                fullWidth("none") { NoLibraries() }
            }

            else -> {
                if (libraries.size > 1) {
                    fullWidth("libraries") { Libraries(state, actions.onSelect) }
                }
                fullWidth("chips") { QuickChips(state.filter, actions, state.pinned) }
                seriesItems(series, state.grid, coverUrl, actions.onOpenSeries)
            }
        }
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.seriesItems(
    series: LazyPagingItems<SeriesEntity>,
    grid: Boolean,
    coverUrl: (Int) -> String?,
    onOpen: (Int) -> Unit,
) {
    val refresh = series.loadState.refresh
    if (series.itemCount == 0) {
        when {
            refresh is LoadState.Loading -> {
                items(SKELETON_COUNT) { Skeleton(grid) }
            }

            refresh is LoadState.Error -> {
                fullWidth("grid-error") { ErrorBlock(series::retry) }
            }

            series.loadState.append.endOfPaginationReached -> {
                fullWidth("empty") { Message(stringResource(R.string.library_empty)) }
            }
        }
        return
    }
    items(series.itemCount, key = series.itemKey { it.id }) { index ->
        val s = series[index] ?: return@items
        if (grid) GridCard(s, coverUrl(s.id), onOpen) else ListRow(s, coverUrl(s.id), onOpen)
    }
    if (series.loadState.append is LoadState.Error) {
        fullWidth("append-error") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Message(stringResource(R.string.library_page_error))
                SecondaryButton(stringResource(R.string.library_retry), series::retry)
            }
        }
    }
}

@Composable
private fun Header(
    grid: Boolean,
    onToggle: () -> Unit,
) {
    // ponytail: the canvas "more" button has no specified contents yet; add it with its menu.
    LargeTitleHeader(stringResource(R.string.library_title)) {
        CircleIconButton(
            if (grid) KavitaIcons.ListIcon else KavitaIcons.Grid,
            stringResource(if (grid) R.string.library_list_view else R.string.library_grid_view),
            onToggle,
        )
    }
}

@Composable
private fun Libraries(
    state: LibraryUiState,
    onSelect: (Int) -> Unit,
) {
    val libraries = state.libraries.orEmpty()
    val names = libraries.map { it.name }
    val select: (Int) -> Unit = { i -> onSelect(libraries[i].id) }
    if (libraries.size <= MAX_FIXED_SEGMENTS) {
        SegmentedControl(names, state.selectedIndex, select, Modifier.padding(bottom = 4.dp))
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
            SegmentedControl(names, state.selectedIndex, select, Modifier.width(SEGMENT_WIDTH * libraries.size))
        }
    }
}

private val SEGMENT_WIDTH = 96.dp

@Composable
private fun subtitle(s: SeriesEntity): String {
    val progress =
        when (val p = s.readProgress()) {
            Progress.Unread -> stringResource(R.string.library_unread)
            Progress.Read -> stringResource(R.string.library_read)
            is Progress.Partial -> stringResource(R.string.library_percent, p.percent)
        }
    return s.bookFormat()?.let { stringResource(R.string.library_format_progress, it, progress) } ?: progress
}

@Composable
private fun GridCard(
    s: SeriesEntity,
    imageUrl: String?,
    onOpen: (Int) -> Unit,
) {
    // TODO(api): the canvas badge counts unread chapters; SeriesDto has no such field (only pages/pagesRead),
    // and per-series volume calls would be N requests per page. Progress bar shown instead.
    CoverCard(
        title = s.localizedName ?: s.name,
        subtitle = subtitle(s),
        color = coverColorFor(s.id.toLong()),
        onClick = { onOpen(s.id) },
        size = CoverCardSize.Grid,
        progress = s.progressBar(),
        imageUrl = imageUrl,
    )
}

/** [derived] List view: 48×72 thumb, title, progress line. */
@Composable
private fun ListRow(
    s: SeriesEntity,
    imageUrl: String?,
    onOpen: (Int) -> Unit,
) {
    val title = s.localizedName ?: s.name
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(s.id) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Title is beside the thumb; placeholder art stays plain at this size.
        CoverImage(
            "",
            coverColorFor(s.id.toLong()),
            Modifier.size(48.dp, 72.dp),
            KavitaShapes.rowThumb,
            imageUrl = imageUrl,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = Kavita.type.bodyStrong,
                color = Kavita.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle(s), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
        }
    }
}
