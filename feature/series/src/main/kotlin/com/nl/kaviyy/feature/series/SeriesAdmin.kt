package com.nl.kaviyy.feature.series

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.theme.Kavita

/** [derived] Admin rows in the "more" sheet, shown only with the Admin role (the server enforces it too). */
@Composable
internal fun AdminSection(admin: AdminActions) {
    SectionHeader(stringResource(R.string.series_admin), Modifier.padding(top = 8.dp))
    GroupedList {
        GroupedRow(
            stringResource(R.string.series_refresh_metadata),
            onClick = admin.onRefreshMetadata,
            showChevron = false,
            divider = true,
        )
        GroupedRow(stringResource(R.string.series_scan), onClick = admin.onScan, showChevron = false, divider = true)
        GroupedRow(stringResource(R.string.series_edit_summary), onClick = admin.onEditSummary)
    }
}

@Composable
internal fun EditSummaryContent(
    summary: TextFieldState,
    onSave: () -> Unit,
) {
    val label = stringResource(R.string.series_edit_summary)
    GroupedList {
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(16.dp)) {
            BasicTextField(
                summary,
                Modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = Kavita.type.bodyRegular.copy(color = Kavita.colors.label),
                cursorBrush = SolidColor(Kavita.colors.accent),
            )
        }
    }
    PrimaryButton(stringResource(R.string.series_save), onSave, Modifier.fillMaxWidth())
}

internal fun adminNoticeText(notice: AdminNotice): Int =
    when (notice) {
        AdminNotice.Started -> R.string.series_admin_started
        AdminNotice.Saved -> R.string.series_admin_saved
        AdminNotice.Failed -> R.string.series_admin_failed
    }
