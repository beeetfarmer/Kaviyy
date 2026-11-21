package com.nl.kaviyy.feature.downloads

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.displayTitle
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.designsystem.component.ButtonSize
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.rowDivider
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.download.DownloadItem

@Composable
fun DownloadedSeriesRoute(
    onBack: () -> Unit,
    onRead: (seriesId: Int, chapterId: Int) -> Unit,
    viewModel: DownloadedSeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DownloadedSeriesScreen(
        state,
        DownloadedSeriesActions(
            onBack,
            viewModel::toggleSelecting,
            viewModel::toggle,
            viewModel::delete,
            viewModel::markRead,
            viewModel::deleteAll,
            onRead = { onRead(viewModel.seriesId, it) },
        ),
    )
}

/** [derived] Screen 11 › series: downloaded chapters with size and read state; swipe or select to act on them. */
@Composable
fun DownloadedSeriesScreen(
    state: DownloadedSeriesUiState,
    actions: DownloadedSeriesActions,
    modifier: Modifier = Modifier,
) = LazyColumn(
    modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
    contentPadding =
        PaddingValues(
            start = KavitaSpacing.screenHorizontal,
            end = KavitaSpacing.screenHorizontal,
            top = 8.dp,
            bottom = KavitaSpacing.contentBottomPadding,
        ),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item { CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.downloads_back), actions.onBack) }
    item {
        LargeTitleHeader(state.seriesName.orEmpty(), overline = stringResource(R.string.downloads_downloaded)) {
            if (state.chapters.isNotEmpty()) {
                SecondaryButton(
                    stringResource(if (state.selecting) R.string.downloads_done else R.string.downloads_select),
                    actions.onToggleSelecting,
                    size = ButtonSize.Small,
                )
            }
        }
    }
    when {
        state.loading -> {
            Unit
        }

        state.chapters.isEmpty() -> {
            item { Hint(stringResource(R.string.downloads_series_empty)) }
        }

        else -> {
            if (state.selecting) {
                item { SelectionBar(state.selected, actions) }
            } else {
                state.continueChapterId?.let { id ->
                    item {
                        PrimaryButton(
                            stringResource(R.string.downloads_continue),
                            { actions.onRead(id) },
                            Modifier.fillMaxWidth(),
                            leadingIcon = KavitaIcons.Play,
                        )
                    }
                }
            }
            item { ChapterList(state, actions) }
            if (!state.selecting) {
                item {
                    GroupedList {
                        GroupedRow(
                            stringResource(deleteAllLabel(state.confirmDeleteAll)),
                            onClick = actions.onDeleteAll,
                            showChevron = false,
                            labelColor = Kavita.colors.destructiveText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    selected: Set<Int>,
    actions: DownloadedSeriesActions,
) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    SecondaryButton(
        stringResource(R.string.downloads_mark_read),
        { actions.onMarkRead(selected) },
        Modifier.weight(1f),
        size = ButtonSize.Medium,
        enabled = selected.isNotEmpty(),
    )
    SecondaryButton(
        stringResource(R.string.downloads_delete_count, selected.size),
        { actions.onDelete(selected) },
        Modifier.weight(1f),
        size = ButtonSize.Medium,
        enabled = selected.isNotEmpty(),
        destructive = true,
    )
}

@Composable
private fun ChapterList(
    state: DownloadedSeriesUiState,
    actions: DownloadedSeriesActions,
) = GroupedList {
    state.chapters.forEachIndexed { i, item ->
        val id = item.row.chapterId
        val divider = i < state.chapters.lastIndex
        if (state.selecting) {
            ChapterRow(item, divider, selected = id in state.selected, onClick = { actions.onToggle(id) })
        } else {
            SwipeToDelete(
                { actions.onDelete(setOf(id)) },
            ) { ChapterRow(item, divider, onClick = { actions.onRead(id) }) }
        }
    }
}

/** End-to-start swipe reveals a red "Delete"; TalkBack gets the same as a custom action. */
@Composable
private fun SwipeToDelete(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val swipe =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                (value == SwipeToDismissBoxValue.EndToStart).also { if (it) onDelete() }
            },
        )
    val label = stringResource(R.string.downloads_delete)
    SwipeToDismissBox(
        swipe,
        backgroundContent = {
            // Only while swiping, so the red never shows at the card's rounded corners.
            if (swipe.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            Box(
                Modifier.fillMaxSize().background(Kavita.colors.destructive).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text(label, style = Kavita.type.bodyStrong, color = Kavita.colors.onAccent) }
        },
        modifier =
            Modifier.semantics {
                customActions =
                    listOf(CustomAccessibilityAction(label) { onDelete().let { true } })
            },
        enableDismissFromStartToEnd = false,
    ) { Box(Modifier.background(Kavita.colors.surface)) { content() } }
}

/** "Chapter 49" / "32 pages · 140 MB" / Read · Unread · 62%; a check circle while selecting. */
@Composable
private fun ChapterRow(
    item: DownloadItem,
    divider: Boolean,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val readLabel = stringResource(R.string.downloads_read_action)
    val chapter = item.chapter
    val title =
        chapter?.number()?.let { stringResource(R.string.downloads_chapter_full, it) }
            ?: chapter?.displayTitle()
            ?: stringResource(R.string.downloads_unknown_series)
    val pages = chapter?.pages ?: item.row.pagesTotal
    val subtitle =
        listOfNotNull(
            pages.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.downloads_pages, it, it) },
            Formatter.formatShortFileSize(context, item.row.bytesDone),
        ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .rowDivider(divider, if (selected != null) 52.dp else 16.dp)
            .then(
                when {
                    onClick == null -> {
                        Modifier
                    }

                    selected != null -> {
                        Modifier
                            .clickable(
                                role = Role.Checkbox,
                                onClick = onClick,
                            ).semantics { this.selected = selected }
                    }

                    else -> {
                        Modifier.clickable(onClickLabel = readLabel, role = Role.Button, onClick = onClick)
                    }
                },
            ).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) CheckCircle(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Kavita.type.bodyStrong, color = Kavita.colors.label)
            Text(subtitle, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
        }
        ReadState(item)
    }
}

@Composable
private fun ReadState(item: DownloadItem) {
    val chapter = item.chapter ?: return
    val (text, accent) =
        when {
            chapter.pages > 0 && chapter.pagesRead >= chapter.pages -> stringResource(R.string.downloads_read) to false
            chapter.pagesRead == 0 -> stringResource(R.string.downloads_unread) to false
            else -> stringResource(R.string.downloads_percent, chapter.pagesRead * PERCENT / chapter.pages) to true
        }
    Text(text, style = Kavita.type.subhead, color = if (accent) Kavita.colors.accent else Kavita.colors.labelSecondary)
}

@Composable
private fun CheckCircle(on: Boolean) =
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

@Composable
private fun Hint(text: String) =
    Text(text, Modifier.padding(vertical = 12.dp), style = Kavita.type.callout, color = Kavita.colors.labelSecondary)

private const val PERCENT = 100

private fun deleteAllLabel(confirm: Boolean) =
    if (confirm) R.string.downloads_delete_all_confirm else R.string.downloads_delete_all
