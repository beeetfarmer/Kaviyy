package com.nl.kaviyy.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.ButtonSize
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita

/** [derived] While selecting: "N selected" + Select All / Deselect All, then Mark Read and Delete. */
@Composable
internal fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    deleteLabel: String,
    onSelectAll: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.downloads_selected_count, count),
            style = Kavita.type.subhead,
            color = Kavita.colors.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(
            stringResource(if (allSelected) R.string.downloads_deselect_all else R.string.downloads_select_all),
            onSelectAll,
            size = ButtonSize.Small,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
            stringResource(R.string.downloads_mark_read),
            onMarkRead,
            Modifier.weight(1f),
            size = ButtonSize.Medium,
            enabled = count > 0,
        )
        SecondaryButton(
            deleteLabel,
            onDelete,
            Modifier.weight(1f),
            size = ButtonSize.Medium,
            enabled = count > 0,
            destructive = true,
        )
    }
}

@Composable
internal fun CheckCircle(on: Boolean) =
    Box(
        Modifier
            .size(24.dp)
            .then(
                if (on) {
                    Modifier.background(Kavita.colors.accent, CircleShape)
                } else {
                    Modifier.border(1.5.dp, Kavita.colors.labelTertiary, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(KavitaIcons.Check, null, Modifier.size(16.dp), tint = Kavita.colors.onAccent) }
