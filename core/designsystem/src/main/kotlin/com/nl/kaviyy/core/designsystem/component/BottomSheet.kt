package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.R
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

private val GrabberColor = Color(0xFFC7C7CC)

@Composable
private fun SheetPill(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = Kavita.colors
    Box(
        modifier =
            Modifier
                .height(40.dp)
                .clip(KavitaShapes.pill)
                .background(if (primary) colors.accent else colors.surface)
                .then(if (primary) Modifier else Modifier.hairline())
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = if (primary) 16.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Kavita.type.callout.copy(fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium),
            color = if (primary) colors.onAccent else colors.labelStrong,
        )
    }
}

/** Cancel (secondary pill) · title · Apply (primary pill). */
@Composable
fun SheetHeader(
    title: String,
    cancelLabel: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    applyLabel: String? = null,
    onApply: () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
        Row(Modifier.align(Alignment.CenterStart)) { SheetPill(cancelLabel, primary = false, onClick = onCancel) }
        Text(
            title,
            style = Kavita.type.sheetTitle,
            color = Kavita.colors.label,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
        if (applyLabel != null) {
            Row(Modifier.align(Alignment.CenterEnd)) { SheetPill(applyLabel, primary = true, onClick = onApply) }
        }
    }
}

/** Modal sheet: radius 28 top, 40×5 grabber, `background` ground, swipe-to-dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KavitaBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState =
            rememberBottomSheetState(
                SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        shape = KavitaShapes.sheet,
        containerColor = Kavita.colors.background,
        dragHandle = {
            val handle = stringResource(R.string.sheet_drag_handle)
            Box(
                Modifier
                    .semantics { contentDescription = handle }
                    .padding(top = 8.dp)
                    .size(40.dp, 5.dp)
                    .clip(KavitaShapes.pill)
                    .background(GrabberColor),
            )
        },
        content = content,
    )
}
