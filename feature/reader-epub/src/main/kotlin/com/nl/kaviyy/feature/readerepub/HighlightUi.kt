package com.nl.kaviyy.feature.readerepub

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.Highlight
import com.nl.kaviyy.core.designsystem.component.HighlightToolbar
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.theme.HighlightColors
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TOOLBAR_HEIGHT = 48.dp
private val TOOLBAR_GAP = 8.dp

@Composable
internal fun highlightColorLabels() =
    listOf(
        stringResource(R.string.epub_color_yellow),
        stringResource(R.string.epub_color_green),
        stringResource(R.string.epub_color_blue),
        stringResource(R.string.epub_color_pink),
    )

/** DESIGN §07: the toolbar sits above the selection, or below it when there is no room above. */
@Composable
internal fun BoxScope.HighlightOverlay(
    target: HighlightTarget,
    actions: EpubReaderActions,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val above = with(density) { (TOOLBAR_HEIGHT + TOOLBAR_GAP).toPx() }
    val y = if (target.top >= above) target.top - above else target.bottom + with(density) { TOOLBAR_GAP.toPx() }
    HighlightToolbar(
        colorLabels = highlightColorLabels(),
        selectedColor = target.color,
        onSelectColor = actions.onPickColor,
        noteLabel = stringResource(R.string.epub_note),
        onNote = actions.onNote,
        copyLabel = stringResource(R.string.epub_copy),
        onCopy = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, target.text))) }
            actions.onDismissHighlight()
        },
        modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, y.roundToInt()) },
    )
}

/** Note on a highlight [derived]: a sheet with one multi-line field; Save stores it (empty removes the note). */
@Composable
internal fun NoteSheet(
    editor: NoteEditor,
    actions: EpubReaderActions,
) {
    val text = remember(editor) { TextFieldState(editor.initial) }
    KavitaBottomSheet(onDismiss = actions.onCloseNote) {
        SheetHeader(
            stringResource(R.string.epub_note),
            stringResource(R.string.epub_cancel),
            actions.onCloseNote,
            applyLabel = stringResource(R.string.epub_save),
            onApply = { actions.onSaveNote(text.text.toString()) },
        )
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(Kavita.colors.surface, KavitaShapes.card)
                .padding(16.dp),
        ) {
            if (text.text.isEmpty()) {
                Text(
                    stringResource(R.string.epub_note_hint),
                    style = Kavita.type.bodyRegular,
                    color = Kavita.colors.placeholder,
                )
            }
            val label = stringResource(R.string.epub_note)
            BasicTextField(
                text,
                Modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = Kavita.type.bodyRegular.copy(color = Kavita.colors.label),
                cursorBrush = SolidColor(Kavita.colors.accent),
            )
        }
    }
}

/** Highlights of this book [derived]: colour dot, the text (2 lines), the note; tap to jump there. */
@Composable
internal fun HighlightRow(
    highlight: Highlight,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Box(
        Modifier
            .padding(top = 5.dp)
            .size(12.dp)
            .background(HighlightColors.all.getOrElse(highlight.color) { HighlightColors.yellow }, CircleShape),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            highlight.text,
            style = Kavita.type.body,
            color = Kavita.colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        highlight.note?.let { Text(it, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary) }
    }
}
