package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.theme.Kavita

/** [derived] Edit a collection or reading list: name and summary (Save), and Delete, which asks once more. */
@Composable
internal fun EditListSheet(
    form: EditListForm,
    title: TextFieldState,
    summary: TextFieldState,
    actions: EditListActions,
) = KavitaBottomSheet(onDismiss = actions.onClose) {
    SheetHeader(
        form.heading,
        stringResource(R.string.lists_cancel),
        actions.onClose,
        applyLabel = stringResource(R.string.lists_save),
        onApply = { if (!form.busy && title.text.isNotBlank()) actions.onSave() },
    )
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        GroupedList {
            GroupedInputRow(stringResource(R.string.lists_name), title, divider = true)
            GroupedInputRow(
                stringResource(R.string.lists_summary),
                summary,
                placeholder = stringResource(R.string.lists_summary_hint),
            )
        }
        if (form.failed) {
            Text(
                stringResource(R.string.lists_change_failed),
                Modifier.padding(top = 8.dp),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
        GroupedList(Modifier.padding(top = 24.dp).fillMaxWidth()) {
            GroupedRow(
                if (form.confirmDelete) stringResource(R.string.lists_delete_confirm) else form.deleteLabel,
                onClick = actions.onDelete.takeIf { !form.busy },
                showChevron = false,
                labelColor = Kavita.colors.destructiveText,
            )
        }
    }
}
