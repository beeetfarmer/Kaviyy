package com.nl.kaviyy.feature.readerepub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.theme.Kavita

private const val INDENT_DP = 16

/**
 * Contents and Highlights [derived]: a segmented control switches between the book's TOC (nested entries indented;
 * the one being read is bold in the accent colour) and this book's highlights.
 */
@Composable
internal fun TocSheet(
    state: EpubReaderUiState,
    actions: EpubReaderActions,
) = KavitaBottomSheet(onDismiss = { actions.onShowToc(false) }) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SheetHeader(stringResource(R.string.epub_toc), stringResource(R.string.epub_done), { actions.onShowToc(false) })
    SegmentedControl(
        listOf(stringResource(R.string.epub_toc), stringResource(R.string.epub_highlights)),
        tab,
        { tab = it },
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (tab == 0) Contents(state, actions) else Highlights(state, actions)
}

@Composable
private fun Contents(
    state: EpubReaderUiState,
    actions: EpubReaderActions,
) {
    val here = state.locator?.href?.removeFragment()
    LazyColumn(Modifier.padding(bottom = 32.dp)) {
        itemsIndexed(state.toc) { _, entry ->
            val current = here != null && entry.link.url().removeFragment() == here
            Text(
                entry.title.ifBlank { stringResource(R.string.epub_untitled) },
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { actions.onGo(entry) }
                    .background(if (current) Kavita.colors.accentContainer else Kavita.colors.background)
                    .semantics { selected = current }
                    .padding(start = (16 + INDENT_DP * entry.depth).dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                style = Kavita.type.body.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
                color = if (current) Kavita.colors.accent else Kavita.colors.label,
            )
        }
    }
}

@Composable
private fun Highlights(
    state: EpubReaderUiState,
    actions: EpubReaderActions,
) {
    if (state.highlights.isEmpty()) {
        Text(
            stringResource(R.string.epub_no_highlights),
            Modifier.fillMaxWidth().padding(32.dp),
            style = Kavita.type.callout,
            color = Kavita.colors.labelSecondary,
            textAlign = TextAlign.Center,
        )
        return
    }
    LazyColumn(Modifier.padding(bottom = 32.dp)) {
        items(state.highlights, key = { it.uuid }) { HighlightRow(it) { actions.onGoHighlight(it) } }
    }
}
