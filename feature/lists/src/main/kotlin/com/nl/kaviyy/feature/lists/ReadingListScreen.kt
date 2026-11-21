package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.next
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.designsystem.component.ButtonSize
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.ProgressBar
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.download.EnqueueResult

/**
 * [derived] A reading list: "Reading list" overline + title, "14 of 24 read" with a 6dp bar, Continue (first
 * unfinished item), then the items in order: number, series, item title and progress. Owners get Edit: items then
 * show move up / down / remove, and "Name & details" opens the edit sheet.
 */
@Composable
fun ReadingListScreen(
    state: ReadingListUiState,
    actions: ReadingListActions,
    modifier: Modifier = Modifier,
) = LazyColumn(
    modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
    contentPadding = screenPadding(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    val list = state.list
    val read = state.items.count { it.read }
    item { CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.lists_back), actions.onBack) }
    item {
        LargeTitleHeader(list?.list?.title.orEmpty(), overline = stringResource(R.string.lists_reading_list)) {
            if (state.editable) {
                SecondaryButton(
                    stringResource(if (state.arranging) R.string.lists_done else R.string.lists_edit),
                    actions.onArrange,
                    size = ButtonSize.Small,
                )
            }
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.lists_reading_read_of, read, state.items.size),
                style = Kavita.type.callout,
                color = Kavita.colors.labelSecondary,
            )
            ProgressBar(if (state.items.isEmpty()) 0f else read.toFloat() / state.items.size, height = 6.dp)
            list?.list?.summary?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Kavita.type.callout, color = Kavita.colors.labelBody, maxLines = SUMMARY_MAX_LINES)
            }
        }
    }
    if (state.arranging) {
        item {
            SecondaryButton(
                stringResource(R.string.lists_details),
                actions.onEdit,
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                size = ButtonSize.Medium,
            )
        }
    } else {
        continueButton(state.items, read, actions.onRead)
        if (state.items.isNotEmpty()) {
            item {
                SecondaryButton(
                    stringResource(R.string.lists_download),
                    actions.onDownload,
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    leadingIcon = KavitaIcons.Download,
                )
            }
        }
        state.download?.let { item { Message(stringResource(downloadText(it))) } }
    }
    if (state.error != null && state.items.isNotEmpty()) item { Message(stringResource(R.string.lists_change_failed)) }
    when {
        state.items.isEmpty() && state.error != null -> item { Message(stringResource(R.string.lists_failed)) }
        state.items.isEmpty() -> item { Message(stringResource(R.string.lists_reading_empty)) }
        else -> item { ItemsCard(state, actions) }
    }
}

private fun LazyListScope.continueButton(
    items: List<ReadingListItemEntity>,
    read: Int,
    onRead: (Int, Int) -> Unit,
) {
    val next = items.next() ?: return
    item {
        val label =
            when {
                read == items.size -> stringResource(R.string.lists_reading_again)
                read == 0 && next.pagesRead == 0 -> stringResource(R.string.lists_reading_start)
                else -> stringResource(R.string.lists_reading_continue)
            }
        PrimaryButton(
            label,
            { onRead(next.seriesId, next.chapterId) },
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            leadingIcon = KavitaIcons.Play,
        )
    }
}

private const val SUMMARY_MAX_LINES = 6

private fun downloadText(result: EnqueueResult): Int =
    when (result) {
        EnqueueResult.Queued -> R.string.lists_download_queued
        EnqueueResult.NothingNew -> R.string.lists_download_nothing_new
        EnqueueResult.NoPermission -> R.string.lists_download_no_role
        EnqueueResult.OverLimit -> R.string.lists_download_over_limit
    }

@Composable
private fun Message(text: String) =
    Text(text, Modifier.padding(vertical = 12.dp), style = Kavita.type.callout, color = Kavita.colors.labelSecondary)

@Composable
private fun ItemsCard(
    state: ReadingListUiState,
    actions: ReadingListActions,
) = Column(Modifier.fillMaxWidth().clip(KavitaShapes.card).background(Kavita.colors.surface)) {
    state.items.forEachIndexed { i, item ->
        ItemRow(i + 1, item, { actions.onRead(item.seriesId, item.chapterId) }) {
            if (state.arranging) {
                ArrangeControls(i, state.items.lastIndex, !state.busy, actions, item)
            } else {
                ItemProgress(item)
            }
        }
    }
}

@Composable
private fun ArrangeControls(
    index: Int,
    last: Int,
    enabled: Boolean,
    actions: ReadingListActions,
    item: ReadingListItemEntity,
) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    CircleIconButton(
        KavitaIcons.ChevronDown,
        stringResource(R.string.lists_move_up),
        { actions.onMove(index, -1) },
        Modifier.rotate(HALF_TURN),
        enabled = enabled && index > 0,
    )
    CircleIconButton(
        KavitaIcons.ChevronDown,
        stringResource(R.string.lists_move_down),
        { actions.onMove(index, 1) },
        enabled = enabled && index < last,
    )
    CircleIconButton(
        KavitaIcons.Minus,
        stringResource(R.string.lists_remove_item),
        { actions.onRemove(item) },
        enabled = enabled,
        contentColor = Kavita.colors.destructive,
    )
}

private const val HALF_TURN = 180f

@Composable
private fun ItemProgress(item: ReadingListItemEntity) =
    when {
        item.read -> {
            Icon(
                KavitaIcons.Check,
                stringResource(R.string.lists_read),
                Modifier.size(18.dp),
                tint = Kavita.colors.accent,
            )
        }

        item.pagesRead > 0 -> {
            Text(
                stringResource(R.string.lists_percent, item.pagesRead * READ_PERCENT / item.pagesTotal),
                style = Kavita.type.caption,
                color = Kavita.colors.labelSecondary,
            )
        }

        else -> {
            Unit
        }
    }

@Composable
private fun ItemRow(
    number: Int,
    item: ReadingListItemEntity,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .clickable(
            role = Role.Button,
            onClick = onClick,
        ).padding(horizontal = 14.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        number.toString(),
        Modifier.width(28.dp),
        style = Kavita.type.callout.copy(fontWeight = FontWeight.SemiBold),
        color = Kavita.colors.labelSecondary,
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            item.seriesName.orEmpty(),
            style = Kavita.type.callout.copy(fontWeight = FontWeight.SemiBold),
            color = Kavita.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.title?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = Kavita.type.caption,
                color = Kavita.colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.pagesRead in 1 until item.pagesTotal) {
            ProgressBar(item.pagesRead.toFloat() / item.pagesTotal, Modifier.padding(top = 4.dp))
        }
    }
    trailing()
}
