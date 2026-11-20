package com.nl.kaviyy.feature.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.FieldKind
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita

/** Screen 09 as a sheet over Library. [currentLibrary] is the grid's library (the default scope). */
@Composable
fun FilterSheet(
    currentLibrary: Int?,
    onDismiss: () -> Unit,
    viewModel: FilterViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open(currentLibrary) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDismiss() }
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val actions =
        FilterActions(
            onCancel = onDismiss,
            onApply = viewModel::apply,
            onMatchAll = viewModel::setMatchAll,
            onRemoveRule = viewModel::removeRule,
            onEditRule = viewModel::editRule,
            onAddRule = viewModel::addRule,
            onShow = viewModel::show,
            onSort = viewModel::setSort,
            onToggleLibrary = viewModel::toggleLibrary,
            onPickField = viewModel::pickField,
            onComparison = viewModel::setComparison,
            onToggleValue = viewModel::toggleValue,
            onCommitRule = viewModel::commitRule,
            onShowOnHome = viewModel::setShowOnHome,
            onPinInLibrary = viewModel::setPinInLibrary,
            onOpenSaved = viewModel::openSaved,
            onDeleteSaved = viewModel::deleteSaved,
        )
    KavitaBottomSheet(onDismiss = onDismiss) {
        FilterContent(state, libraries, FilterFields(viewModel.valueText, viewModel.nameText), actions)
    }
}

@Composable
fun ColumnScope.FilterContent(
    state: FilterUiState,
    libraries: List<LibraryEntity>,
    fields: FilterFields,
    actions: FilterActions,
) {
    FilterHeader(state, actions)
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.error?.let {
            Text(stringResource(it.label()), style = Kavita.type.footnote, color = Kavita.colors.destructive)
        }
        when (state.page) {
            FilterPage.Main -> {
                MainPage(state, libraries, fields.name, actions)
            }

            FilterPage.Saved -> {
                SavedPage(state.saved, actions)
            }

            FilterPage.Fields -> {
                Choices(
                    FilterField.entries.map {
                        stringResource(it.label()) to false
                    },
                ) { actions.onPickField(FilterField.entries[it]) }
            }

            FilterPage.Rule -> {
                state.rule?.let { RulePage(it, state, fields.value, actions) }
            }

            FilterPage.Sort -> {
                Choices(
                    SeriesSort.entries.map {
                        stringResource(it.label()) to (it == state.draft.sort)
                    },
                ) { actions.onSort(SeriesSort.entries[it]) }
            }

            FilterPage.Libraries -> {
                val scope = state.draft.libraryIds.ifEmpty { listOfNotNull(state.currentLibrary) }
                Choices(libraries.map { it.name to (it.id in scope) }) { actions.onToggleLibrary(libraries[it].id) }
            }
        }
    }
}

@Composable
private fun MainPage(
    state: FilterUiState,
    libraries: List<LibraryEntity>,
    name: TextFieldState,
    actions: FilterActions,
) {
    val f = state.draft
    SectionHeader(stringResource(R.string.filter_match_header))
    SegmentedControl(
        listOf(stringResource(R.string.filter_match_all), stringResource(R.string.filter_match_any)),
        if (f.matchAll) 0 else 1,
        { actions.onMatchAll(it == 0) },
    )
    GroupedList(Modifier.padding(top = 6.dp)) {
        f.rules.forEachIndexed { i, rule ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) {
                        actions.onEditRule(i)
                    }.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${stringResource(
                            rule.field.label(),
                        )} · ${stringResource(rule.comparison.label(rule.field.kind))}",
                        style = Kavita.type.footnote,
                        color = Kavita.colors.labelSecondary,
                    )
                    Text(
                        rule.valueLabel(),
                        style = Kavita.type.bodyStrong,
                        color = Kavita.colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { actions.onRemoveRule(i) }) {
                    Icon(
                        KavitaIcons.Close,
                        stringResource(R.string.filter_remove_rule),
                        tint = Kavita.colors.destructive,
                    )
                }
            }
            Divider()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 52.dp,
                ).clickable(role = Role.Button, onClick = actions.onAddRule)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(KavitaIcons.Plus, null, Modifier.size(20.dp), tint = Kavita.colors.accent)
            Text(stringResource(R.string.filter_add_rule), style = Kavita.type.bodyStrong, color = Kavita.colors.accent)
        }
    }
    SectionHeader(stringResource(R.string.filter_sort_scope_header), Modifier.padding(top = 12.dp))
    val scope = f.libraryIds.ifEmpty { listOfNotNull(state.currentLibrary) }
    GroupedList {
        GroupedRow(stringResource(R.string.filter_sort_by), value = stringResource(f.sort.label()), onClick = {
            actions.onShow(FilterPage.Sort)
        }, divider = true)
        GroupedRow(
            stringResource(R.string.filter_libraries),
            value = libraries.filter { it.id in scope }.joinToString { it.name },
            onClick = { actions.onShow(FilterPage.Libraries) },
        )
    }
    SaveSection(state, name, actions)
}

@Composable
private fun RulePage(
    rule: RuleDraft,
    state: FilterUiState,
    valueText: TextFieldState,
    actions: FilterActions,
) {
    val kind = rule.field.kind
    SectionHeader(stringResource(R.string.filter_condition_header))
    Choices(
        kind.comparisons.map {
            stringResource(it.label(kind)) to (it == rule.comparison)
        },
    ) { actions.onComparison(kind.comparisons[it]) }
    SectionHeader(stringResource(R.string.filter_value_header), Modifier.padding(top = 12.dp))
    when {
        kind == FieldKind.Boolean -> {
            val options = booleanOptions()
            Choices(
                options.map {
                    it.label to (
                        it.code in
                            rule.selected.map { s ->
                                s.code
                            }
                    )
                },
            ) { actions.onToggleValue(options[it]) }
        }

        rule.field.source == OptionSource.Formats -> {
            val options = formatOptions()
            Choices(
                options.map {
                    it.label to (
                        it.code in
                            rule.selected.map { s ->
                                s.code
                            }
                    )
                },
            ) { actions.onToggleValue(options[it]) }
        }

        rule.usesOptions -> {
            OptionValues(rule, state.options, actions.onToggleValue)
        }

        else -> {
            val numeric = kind != FieldKind.Text && !rule.expectsDate
            GroupedList {
                GroupedInputRow(
                    stringResource(R.string.filter_value),
                    valueText,
                    placeholder =
                        when {
                            rule.expectsDate -> stringResource(R.string.filter_date_hint)
                            kind == FieldKind.Date -> stringResource(R.string.filter_days_hint)
                            else -> null
                        },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                        ),
                )
            }
        }
    }
    state.ruleError?.let {
        Text(
            stringResource(it.label()),
            style = Kavita.type.footnote,
            color = Kavita.colors.destructive,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun OptionValues(
    rule: RuleDraft,
    options: Options,
    onToggle: (RuleValue) -> Unit,
) {
    when (options) {
        Options.Loading -> {
            Hint(stringResource(R.string.filter_loading))
        }

        is Options.Failed -> {
            Hint(stringResource(R.string.filter_options_failed))
        }

        is Options.Loaded -> {
            if (options.values.isEmpty()) {
                Hint(stringResource(R.string.filter_no_options))
            } else {
                val picked = rule.selected.map { it.code }.toSet()
                Choices(options.values.map { it.label to (it.code in picked) }) { onToggle(options.values[it]) }
            }
        }
    }
}

/** Grouped list of choices; selected ones carry an accent check. */
@Composable
private fun Choices(
    rows: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
) {
    GroupedList {
        rows.forEachIndexed { i, (label, selected) ->
            GroupedRow(label, onClick = { onPick(i) }, showChevron = false, divider = i < rows.lastIndex) {
                if (selected) {
                    Icon(
                        KavitaIcons.Check,
                        stringResource(R.string.filter_selected),
                        Modifier.size(18.dp),
                        tint = Kavita.colors.accent,
                    )
                }
            }
        }
    }
}

/** 0.5dp separator inset from the leading edge, as in GroupedRow. */
@Composable
private fun Divider() =
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(Kavita.colors.separator),
    )

@Composable
internal fun Hint(text: String) =
    Text(text, style = Kavita.type.callout, color = Kavita.colors.labelSecondary, modifier = Modifier.padding(8.dp))

@Composable
private fun FilterHeader(
    state: FilterUiState,
    actions: FilterActions,
) {
    val back = { actions.onShow(FilterPage.Main) }
    when (state.page) {
        FilterPage.Main -> {
            SheetHeader(
                stringResource(R.string.filter_title),
                stringResource(R.string.filter_cancel),
                actions.onCancel,
                applyLabel = stringResource(R.string.filter_apply),
                onApply = { if (!state.busy) actions.onApply() },
            )
        }

        FilterPage.Saved -> {
            SheetHeader(stringResource(R.string.filter_saved_title), stringResource(R.string.filter_back), back)
        }

        FilterPage.Fields -> {
            SheetHeader(stringResource(R.string.filter_field_title), stringResource(R.string.filter_back), back)
        }

        FilterPage.Rule -> {
            SheetHeader(
                state.rule
                    ?.field
                    ?.let { stringResource(it.label()) }
                    .orEmpty(),
                stringResource(R.string.filter_back),
                back,
                applyLabel = stringResource(R.string.filter_done),
                onApply = actions.onCommitRule,
            )
        }

        FilterPage.Sort -> {
            SheetHeader(stringResource(R.string.filter_sort_by), stringResource(R.string.filter_back), back)
        }

        FilterPage.Libraries -> {
            SheetHeader(stringResource(R.string.filter_libraries), stringResource(R.string.filter_done), back)
        }
    }
}
