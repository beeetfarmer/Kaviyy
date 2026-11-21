package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.designsystem.component.FilterChip
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita

/**
 * [derived] New Smart Collection: name, Any/All, tags and genres as chips (selected ones first, with a search to
 * narrow long lists), and a live "N series match". Built from the filter sheet's pieces.
 */
@Composable
internal fun SmartCollectionSheet(
    state: SmartCollectionUiState,
    name: TextFieldState,
    search: TextFieldState,
    actions: SmartCollectionActions,
) = KavitaBottomSheet(onDismiss = actions.onClose) {
    SheetHeader(
        stringResource(R.string.lists_new_smart),
        stringResource(R.string.lists_cancel),
        actions.onClose,
        applyLabel = stringResource(R.string.lists_create),
        onApply = { if (state.canCreate && name.text.isNotBlank()) actions.onCreate() },
    )
    SmartCollectionForm(state, name, search, actions)
}

@Composable
internal fun SmartCollectionForm(
    state: SmartCollectionUiState,
    name: TextFieldState,
    search: TextFieldState,
    actions: SmartCollectionActions,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    GroupedList {
        GroupedInputRow(
            stringResource(R.string.lists_name),
            name,
            placeholder = stringResource(R.string.lists_smart_name_hint),
        )
    }
    SegmentedControl(
        listOf(stringResource(R.string.lists_smart_any), stringResource(R.string.lists_smart_all)),
        if (state.matchAll) 1 else 0,
        { actions.onMatchAll(it == 1) },
    )
    MatchLine(state)
    GroupedList {
        GroupedInputRow(
            stringResource(R.string.lists_smart_search),
            search,
            placeholder = stringResource(R.string.lists_smart_search_hint),
        )
    }
    if (state.loadingOptions) Note(stringResource(R.string.lists_smart_loading))
    if (state.failed && state.tags.isEmpty() &&
        state.genres.isEmpty()
    ) {
        Note(stringResource(R.string.lists_change_failed))
    }
    ChipGroup(
        stringResource(R.string.lists_smart_tags),
        state.selectedTags,
        state.visible(state.tags, state.selectedTags),
        actions.onToggleTag,
    )
    ChipGroup(
        stringResource(R.string.lists_smart_genres),
        state.selectedGenres,
        state.visible(state.genres, state.selectedGenres),
        actions.onToggleGenre,
    )
    Note(stringResource(R.string.lists_smart_footnote))
}

@Composable
private fun MatchLine(state: SmartCollectionUiState) {
    val text =
        when (val c = state.count) {
            MatchCount.None -> {
                stringResource(R.string.lists_smart_pick)
            }

            MatchCount.Checking -> {
                stringResource(R.string.lists_smart_checking)
            }

            MatchCount.Failed -> {
                stringResource(R.string.lists_change_failed)
            }

            is MatchCount.Count -> {
                if (c.n == 0) {
                    stringResource(R.string.lists_smart_none)
                } else {
                    pluralStringResource(R.plurals.lists_smart_matches, c.n, c.n)
                }
            }
        }
    Text(text, style = Kavita.type.subhead, color = Kavita.colors.accent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    title: String,
    selected: List<RuleValue>,
    others: List<RuleValue>,
    onToggle: (RuleValue) -> Unit,
) {
    if (selected.isEmpty() && others.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected.forEach { FilterChip(it.label, true, { onToggle(it) }, leadingIcon = KavitaIcons.Check) }
            others.forEach { FilterChip(it.label, false, { onToggle(it) }) }
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
