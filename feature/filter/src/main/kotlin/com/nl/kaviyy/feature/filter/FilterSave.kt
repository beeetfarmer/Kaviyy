package com.nl.kaviyy.feature.filter

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.SavedFilter
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita

@Composable
internal fun SaveSection(
    state: FilterUiState,
    name: TextFieldState,
    actions: FilterActions,
) {
    SectionHeader(stringResource(R.string.filter_save_header), Modifier.padding(top = 12.dp))
    GroupedList {
        GroupedInputRow(
            stringResource(R.string.filter_save_name),
            name,
            placeholder = stringResource(R.string.filter_save_name_hint),
            divider = true,
            alignEnd = true,
        )
        GroupedSwitchRow(
            stringResource(R.string.filter_show_on_home),
            state.placement.onHome,
            actions.onShowOnHome,
            divider = true,
        )
        GroupedSwitchRow(
            stringResource(R.string.filter_pin_in_library),
            state.placement.inLibrary,
            actions.onPinInLibrary,
            divider = true,
        )
        GroupedRow(stringResource(R.string.filter_saved_row), onClick = { actions.onShow(FilterPage.Saved) })
    }
    Text(
        stringResource(R.string.filter_save_footnote),
        style = Kavita.type.footnote,
        color = Kavita.colors.labelSecondary,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
internal fun SavedPage(
    saved: List<SavedFilter>?,
    actions: FilterActions,
) {
    when {
        saved == null -> {
            Hint(stringResource(R.string.filter_loading))
        }

        saved.isEmpty() -> {
            Hint(stringResource(R.string.filter_saved_empty))
        }

        else -> {
            GroupedList {
                saved.forEachIndexed { i, f ->
                    GroupedRow(f.name, onClick = { actions.onOpenSaved(f) }, divider = i < saved.lastIndex) {
                        IconButton(onClick = { actions.onDeleteSaved(f) }) {
                            Icon(
                                KavitaIcons.Close,
                                stringResource(R.string.filter_delete_saved, f.name),
                                tint = Kavita.colors.destructive,
                            )
                        }
                    }
                }
            }
        }
    }
}
