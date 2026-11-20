package com.nl.kaviyy.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.FilterOption
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.designsystem.component.FilterChip
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing

@Composable
internal fun sortLabel(sort: SeriesSort): String =
    stringResource(
        when (sort) {
            SeriesSort.RecentlyAdded -> R.string.library_sort_recently_added
            SeriesSort.RecentlyUpdated -> R.string.library_sort_recently_updated
            SeriesSort.Title -> R.string.library_sort_title
            SeriesSort.ReleaseYear -> R.string.library_sort_release_year
            SeriesSort.ReadProgress -> R.string.library_sort_read_progress
            SeriesSort.UserRating -> R.string.library_sort_user_rating
        },
    )

/**
 * Canvas chip row: Sort dropdown · Unread · Genre · Status. Runs to the screen edge on the right.
 * The accent "Filter · n" chip opens sheet 09, which lands with M7.
 */
@Composable
internal fun QuickChips(
    filters: QuickFilters,
    actions: LibraryActions,
) {
    val edge = KavitaSpacing.screenHorizontal
    Row(
        Modifier
            .layout { measurable, c ->
                val extra = edge.roundToPx()
                val p = measurable.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
                layout(c.maxWidth, p.height) { p.place(0, 0) }
            }.horizontalScroll(rememberScrollState())
            .padding(end = edge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(sortLabel(filters.sort), false, { actions.onShowSheet(LibrarySheet.Sort) }, dropdown = true)
        FilterChip(stringResource(R.string.library_chip_unread), filters.unreadOnly, actions.onToggleUnread)
        FilterChip(
            filters.genre?.title ?: stringResource(R.string.library_chip_genre),
            filters.genre != null,
            { actions.onShowSheet(LibrarySheet.Genre) },
            dropdown = true,
        )
        FilterChip(
            filters.status?.title ?: stringResource(R.string.library_chip_status),
            filters.status != null,
            { actions.onShowSheet(LibrarySheet.Status) },
            dropdown = true,
        )
    }
}

@Composable
internal fun LibrarySheets(
    state: LibraryUiState,
    actions: LibraryActions,
) {
    val sheet = state.sheet ?: return
    val close = { actions.onShowSheet(null) }
    KavitaBottomSheet(onDismiss = close) {
        val title =
            when (sheet) {
                LibrarySheet.Sort -> R.string.library_sort_title_sheet
                LibrarySheet.Genre -> R.string.library_chip_genre
                LibrarySheet.Status -> R.string.library_chip_status
            }
        SheetHeader(stringResource(title), stringResource(R.string.library_done), close)
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (sheet) {
                LibrarySheet.Sort -> {
                    val rows = SeriesSort.entries.map { sortLabel(it) to (it == state.filters.sort) }
                    Options(rows) { i -> actions.onSort(SeriesSort.entries[i]) }
                }

                else -> {
                    FilterOptions(state, sheet, actions.onPick)
                }
            }
        }
    }
}

@Composable
private fun FilterOptions(
    state: LibraryUiState,
    sheet: LibrarySheet,
    onPick: (FilterOption?) -> Unit,
) {
    val current = if (sheet == LibrarySheet.Genre) state.filters.genre else state.filters.status
    when (val o = state.options) {
        SheetOptions.Loading -> {
            Hint(stringResource(R.string.library_loading))
        }

        is SheetOptions.Failed -> {
            Hint(stringResource(R.string.library_error_body))
        }

        is SheetOptions.Loaded -> {
            val any = stringResource(R.string.library_any) to (current == null)
            val rows = listOf(any) + o.options.map { it.title to (it.id == current?.id) }
            Options(rows) { i -> onPick(if (i == 0) null else o.options[i - 1]) }
        }
    }
}

/** Grouped list of choices; the selected one carries an accent check. */
@Composable
private fun Options(
    rows: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
) {
    GroupedList {
        rows.forEachIndexed { i, (label, selected) ->
            GroupedRow(label, onClick = { onPick(i) }, showChevron = false, divider = i < rows.lastIndex) {
                if (selected) {
                    Icon(
                        KavitaIcons.Check,
                        stringResource(R.string.library_selected),
                        Modifier.size(18.dp),
                        tint = Kavita.colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = Kavita.type.callout, color = Kavita.colors.labelSecondary, modifier = Modifier.padding(8.dp))
}
