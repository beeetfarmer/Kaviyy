package com.nl.kaviyy.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.designsystem.component.Avatar
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.FilterChip
import com.nl.kaviyy.core.designsystem.component.FloatingSearchBar
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.GroupedThumbRow
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.seriesCoverUrl

@Composable
fun SearchRoute(
    onHome: () -> Unit,
    onOpen: (SearchDestination) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.destinations.collect(onOpen) }
    val focus = remember { FocusRequester() }
    // Open with the keyboard up, as the tab bar's search button promises.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SearchScreen(
        state,
        viewModel.query,
        SearchActions(
            viewModel::setScope,
            viewModel::open,
            viewModel::useRecent,
            viewModel::clearRecents,
            viewModel::submit,
            onHome,
        ),
        focusRequester = focus,
    )
}

/** Screen 05 (DESIGN.md §7). */
@Composable
fun SearchScreen(
    state: SearchUiState,
    query: TextFieldState,
    actions: SearchActions,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Box(modifier.fillMaxSize().background(Kavita.colors.background)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding =
                PaddingValues(
                    start = KavitaSpacing.screenHorizontal,
                    end = KavitaSpacing.screenHorizontal,
                    top = KavitaSpacing.topBelowStatusBar,
                    bottom = KavitaSpacing.contentBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("title") {
                Text(
                    stringResource(R.string.search_title),
                    style = Kavita.type.largeTitle,
                    color = Kavita.colors.label,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item("scopes") { Scopes(state.scope, actions.onScope) }
            if (state.openFailed) item("open-failed") { Hint(stringResource(R.string.search_open_failed)) }
            when (val r = state.results) {
                Results.Idle -> {
                    item("recents") { Recents(state.recents, actions) }
                }

                Results.Loading -> {
                    item("loading") { Hint(stringResource(R.string.search_loading)) }
                }

                is Results.Failed -> {
                    item("failed") { Hint(stringResource(R.string.search_failed)) }
                }

                is Results.Loaded -> {
                    val groups = groups(r.value, state.scope)
                    if (groups.isEmpty()) {
                        item("empty") { Hint(stringResource(R.string.search_no_results, r.query)) }
                    }
                    groups.forEach { g -> item(g.kind.name) { Group(g, state, actions.onOpen) } }
                }
            }
        }
        FloatingSearchBar(
            query,
            label = stringResource(R.string.search_field),
            homeLabel = stringResource(R.string.search_home),
            clearLabel = stringResource(R.string.search_clear),
            onHome = actions.onHome,
            onSubmit = actions.onSubmit,
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun Scopes(
    selected: SearchScope,
    onScope: (SearchScope) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchScope.entries.forEach { s ->
            FilterChip(stringResource(s.label()), selected = s == selected, onClick = { onScope(s) })
        }
    }
}

/** [derived] Recent searches when the field is empty. */
@Composable
private fun Recents(
    recents: List<String>,
    actions: SearchActions,
) {
    if (recents.isEmpty()) {
        Hint(stringResource(R.string.search_hint))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader(stringResource(R.string.search_recent), null)
        GroupedList {
            recents.forEachIndexed { i, q -> GroupedRow(q, onClick = { actions.onRecent(q) }, divider = true) }
            GroupedRow(
                stringResource(R.string.search_clear_recent),
                onClick = actions.onClearRecents,
                showChevron = false,
                divider = false,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(
    group: ResultGroup,
    state: SearchUiState,
    onOpen: (SearchHit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader(
            stringResource(group.kind.label()),
            pluralStringResource(R.plurals.search_results, group.rows.size, group.rows.size),
        )
        if (group.kind == GroupKind.GenresTags) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.rows.forEach { row ->
                    FilterChip(row.title, selected = false, onClick = { row.hit?.let(onOpen) })
                }
            }
        } else {
            GroupedList {
                group.rows.forEachIndexed { i, row ->
                    GroupedThumbRow(
                        title = row.title,
                        subtitle = row.subtitle(),
                        onClick = row.hit?.let { hit -> { onOpen(hit) } },
                        divider = i < group.rows.lastIndex,
                    ) { Thumb(group.kind, row, state) }
                }
            }
        }
    }
}

@Composable
private fun Thumb(
    kind: GroupKind,
    row: ResultRow,
    state: SearchUiState,
) {
    val color = coverColorFor(row.key.toLong())
    when (kind) {
        GroupKind.Series -> {
            CoverImage(
                title = "", // the row names it; titles break mid-word in 40dp
                color = color,
                modifier = Modifier.size(40.dp, 60.dp),
                shape = KavitaShapes.rowThumb,
                imageUrl = (row.hit as? SearchHit.Series)?.let { state.server?.seriesCoverUrl(it.id) },
            )
        }

        GroupKind.People -> {
            Avatar(initials(row.title), null, size = 40.dp)
        }

        else -> {
            Box(Modifier.size(40.dp).background(color, KavitaShapes.settingsTile))
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    count: String?,
) = Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
    Text(
        name.uppercase(),
        style = Kavita.type.sectionHeader,
        color = Kavita.colors.labelSecondary,
        modifier = Modifier.weight(1f).semantics { heading() },
    )
    count?.let { Text(it, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary) }
}

@Composable
private fun Hint(text: String) =
    Text(
        text,
        style = Kavita.type.callout,
        color = Kavita.colors.labelSecondary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
