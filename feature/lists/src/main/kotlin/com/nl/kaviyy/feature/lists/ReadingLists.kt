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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.next
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.ProgressBar
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing

/** Lists shows this many reading lists; "See All" opens the rest (their progress is fetched with them). */
internal const val READING_LISTS_SHOWN = com.nl.kaviyy.core.data.READING_LISTS_PREVIEW
private val TILE_SHAPE = RoundedCornerShape(10.dp)
private const val PERCENT = 100

private val ReadingListItemEntity.read: Boolean get() = pagesTotal > 0 && pagesRead >= pagesTotal

/** Row: 44 colour tile with the list icon, title, "14 / 24", 4dp progress. */
@Composable
internal fun ReadingListRowItem(
    row: ReadingListRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier
        .fillMaxWidth()
        .clickable(
            role = Role.Button,
            onClick = onClick,
        ).padding(horizontal = 14.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        Modifier.size(44.dp).clip(TILE_SHAPE).background(coverColorFor(row.list.id.toLong())),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KavitaIcons.ListIcon, null, Modifier.size(20.dp), tint = Color.White)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.list.title,
                Modifier.weight(1f),
                style = Kavita.type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = Kavita.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.lists_reading_progress, row.readCount, row.list.itemCount),
                style = Kavita.type.caption,
                color = Kavita.colors.labelSecondary,
            )
        }
        ProgressBar(if (row.list.itemCount > 0) row.readCount.toFloat() / row.list.itemCount else 0f)
    }
}

@Composable
private fun RowsCard(
    rows: List<ReadingListRow>,
    onOpen: (Int) -> Unit,
) = Column(Modifier.fillMaxWidth().clip(KavitaShapes.card).background(Kavita.colors.surface)) {
    rows.forEach { r -> ReadingListRowItem(r, { onOpen(r.list.id) }) }
}

/** Lists section: "Reading Lists" + "See All" (when there are more), rows in one card. */
@Composable
internal fun ReadingListsSection(
    state: ListsUiState,
    actions: ListsActions,
) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(R.string.lists_reading_lists),
            Modifier.weight(1f),
            style = Kavita.type.title2,
            color = Kavita.colors.label,
        )
        if (state.readingLists.size > READING_LISTS_SHOWN) {
            Text(
                stringResource(R.string.lists_see_all),
                Modifier
                    .clickable(
                        role = Role.Button,
                        onClick = actions.onSeeAllReadingLists,
                    ).padding(vertical = 12.dp),
                style = Kavita.type.callout.copy(fontWeight = FontWeight.Medium),
                color = Kavita.colors.accent,
            )
        }
    }
    if (state.readingLists.isEmpty()) {
        Text(
            stringResource(R.string.lists_no_reading_lists),
            style = Kavita.type.callout,
            color = Kavita.colors.labelSecondary,
        )
    } else {
        RowsCard(state.readingLists.take(READING_LISTS_SHOWN), actions.onOpenReadingList)
    }
}

/** [derived] All reading lists: back, "Reading Lists" large title, the rows. */
@Composable
fun AllReadingListsScreen(
    state: ListsUiState,
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = LazyColumn(
    modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
    contentPadding = screenPadding(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    item { CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.lists_back), onBack) }
    item { LargeTitleHeader(stringResource(R.string.lists_reading_lists)) }
    item { RowsCard(state.readingLists, onOpen) }
}

private fun screenPadding() =
    PaddingValues(
        start = KavitaSpacing.screenHorizontal,
        end = KavitaSpacing.screenHorizontal,
        top = 8.dp,
        bottom = KavitaSpacing.contentBottomPadding,
    )

/**
 * [derived] A reading list: "Reading list" overline + title, "14 of 24 read" with a 6dp bar, Continue (first
 * unfinished item), then the items in order: number, series, item title and progress.
 */
@Composable
fun ReadingListScreen(
    state: ReadingListUiState,
    onBack: () -> Unit,
    onRead: (seriesId: Int, chapterId: Int) -> Unit,
    modifier: Modifier = Modifier,
) = LazyColumn(
    modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
    contentPadding = screenPadding(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    val list = state.list
    val read = state.items.count { it.read }
    item { CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.lists_back), onBack) }
    item { LargeTitleHeader(list?.list?.title.orEmpty(), overline = stringResource(R.string.lists_reading_list)) }
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
    state.items.next()?.let { next ->
        item {
            val label =
                when {
                    read == state.items.size -> stringResource(R.string.lists_reading_again)
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
    when {
        state.items.isEmpty() && state.error != null -> item { Message(stringResource(R.string.lists_failed)) }
        state.items.isEmpty() -> item { Message(stringResource(R.string.lists_reading_empty)) }
        else -> item { ItemsCard(state.items, onRead) }
    }
}

private const val SUMMARY_MAX_LINES = 6

@Composable
private fun Message(text: String) =
    Text(text, Modifier.padding(vertical = 24.dp), style = Kavita.type.callout, color = Kavita.colors.labelSecondary)

@Composable
private fun ItemsCard(
    items: List<ReadingListItemEntity>,
    onRead: (Int, Int) -> Unit,
) = Column(Modifier.fillMaxWidth().clip(KavitaShapes.card).background(Kavita.colors.surface)) {
    items.forEachIndexed { i, item -> ItemRow(i + 1, item) { onRead(item.seriesId, item.chapterId) } }
}

@Composable
private fun ItemRow(
    number: Int,
    item: ReadingListItemEntity,
    onClick: () -> Unit,
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
                stringResource(R.string.lists_percent, item.pagesRead * PERCENT / item.pagesTotal),
                style = Kavita.type.caption,
                color = Kavita.colors.labelSecondary,
            )
        }
    }
}
