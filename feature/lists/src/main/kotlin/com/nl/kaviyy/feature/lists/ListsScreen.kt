package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.nl.kaviyy.core.data.ListSummary
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.seriesCoverUrl

@Composable
fun ListsRoute(
    onOpenWantToRead: () -> Unit,
    onOpenCollection: (Int) -> Unit,
    onSeeAllCollections: () -> Unit,
    onOpenReadingList: (Int) -> Unit,
    onSeeAllReadingLists: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.newList.created) {
        state.newList.created?.let {
            viewModel.consumeCreated()
            onOpenReadingList(it)
        }
    }
    ListsScreen(
        state,
        ListsActions(
            onOpenWantToRead,
            onOpenCollection,
            onSeeAllCollections,
            onOpenReadingList,
            onSeeAllReadingLists,
            viewModel::openNew,
        ),
    )
    if (state.newList.open) {
        NewReadingListSheet(
            state.newList,
            viewModel.newName,
            viewModel::closeNew,
            viewModel::createReadingList,
        )
    }
}

/** [derived] "+": a new reading list (Kavita only creates collections by adding a series, from the series screen). */
@Composable
internal fun NewReadingListSheet(
    state: NewListState,
    name: TextFieldState,
    onClose: () -> Unit,
    onCreate: () -> Unit,
) = KavitaBottomSheet(onDismiss = onClose) {
    SheetHeader(
        stringResource(R.string.lists_new_reading_list),
        stringResource(R.string.lists_cancel),
        onClose,
        applyLabel = stringResource(R.string.lists_create),
        onApply = { if (!state.busy && name.text.isNotBlank()) onCreate() },
    )
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        GroupedList {
            GroupedInputRow(
                stringResource(R.string.lists_name),
                name,
                placeholder = stringResource(R.string.lists_new_reading_list_hint),
            )
        }
        if (state.failed) {
            Text(
                stringResource(R.string.lists_change_failed),
                Modifier.padding(top = 8.dp),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
    }
}

@Composable
fun AllReadingListsRoute(
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AllReadingListsScreen(state, onBack, onOpen)
}

@Composable
fun ReadingListRoute(
    onBack: () -> Unit,
    onRead: (seriesId: Int, chapterId: Int) -> Unit,
    viewModel: ReadingListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    ReadingListScreen(
        state,
        ReadingListActions(
            onBack,
            onRead,
            viewModel::toggleArranging,
            viewModel::move,
            viewModel::remove,
            viewModel::startEdit,
        ),
    )
    if (state.editing) {
        EditListSheet(
            EditListForm(
                stringResource(R.string.lists_edit_reading_list),
                stringResource(R.string.lists_delete_reading_list),
                state.busy,
                state.error != null,
                state.confirmDelete,
            ),
            viewModel.title,
            viewModel.summary,
            EditListActions(viewModel::closeEdit, viewModel::save, viewModel::delete),
        )
    }
}

@Composable
fun AllCollectionsRoute(
    onBack: () -> Unit,
    onOpenCollection: (Int) -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AllCollectionsScreen(state, ListsActions(onOpenCollection = onOpenCollection), onBack)
}

@Composable
fun CollectionRoute(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    CollectionScreen(
        state,
        viewModel.series.collectAsLazyPagingItems(),
        onBack,
        onOpenSeries,
        onEdit = viewModel::startEdit,
    )
    if (state.editing) {
        EditListSheet(
            EditListForm(
                stringResource(R.string.lists_edit_collection),
                stringResource(R.string.lists_delete),
                state.busy,
                state.error != null,
                state.confirmDelete,
            ),
            viewModel.title,
            viewModel.summary,
            EditListActions(viewModel::closeEdit, viewModel::save, viewModel::delete),
        )
    }
}

/** Screen 08 (DESIGN.md §7). */
@Composable
fun ListsScreen(
    state: ListsUiState,
    actions: ListsActions,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .fillMaxSize()
        .background(Kavita.colors.background)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = KavitaSpacing.screenHorizontal),
    verticalArrangement = Arrangement.spacedBy(20.dp),
) {
    LargeTitleHeader(stringResource(R.string.lists_title), Modifier.padding(top = KavitaSpacing.topBelowStatusBar)) {
        CircleIconButton(KavitaIcons.Plus, stringResource(R.string.lists_new_reading_list), actions.onNew)
    }
    WantToReadCard(state.wantToRead, state.server, actions.onOpenWantToRead)
    CollectionsSection(state, actions)
    ReadingListsSection(state, actions)
    Spacer(Modifier.height(KavitaSpacing.contentBottomPadding))
}

/**
 * r22 card: fanned 3-cover stack, filled accent bookmark + "Want to Read", "18 series · 3 updated this week", chevron.
 */
@Composable
internal fun WantToReadCard(
    summary: ListSummary,
    server: Server?,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .clip(KavitaShapes.heroCard)
        .background(Kavita.colors.surface)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    CoverStack(summary.coverIds, server)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KavitaIcons.BookmarkFilled, null, Modifier.size(16.dp), tint = Kavita.colors.accent)
            Text(
                stringResource(R.string.lists_want_to_read),
                style = Kavita.type.headline,
                color = Kavita.colors.label,
            )
        }
        Text(
            summaryText(summary),
            style = Kavita.type.subhead.copy(fontWeight = FontWeight.Normal),
            color = Kavita.colors.labelSecondary,
        )
    }
    Icon(KavitaIcons.ChevronRight, null, Modifier.size(14.dp), tint = Kavita.colors.labelTertiary)
}

@Composable
private fun summaryText(summary: ListSummary): String {
    val count =
        if (summary.more) {
            stringResource(R.string.lists_series_more, summary.count)
        } else {
            pluralStringResource(R.plurals.lists_series, summary.count, summary.count)
        }
    return if (summary.updatedThisWeek > 0) {
        stringResource(R.string.lists_summary_updated, count, summary.updatedThisWeek)
    } else {
        count
    }
}

private val COVER_SHAPE = RoundedCornerShape(7.dp)

private data class Fanned(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val angle: Float,
)

// Back left, back right, then the front cover on top (canvas positions within 104 × 96).
private val FAN =
    listOf(
        Fanned(0.dp, 14.dp, 52.dp, 78.dp, -10f),
        Fanned(50.dp, 14.dp, 52.dp, 78.dp, 10f),
        Fanned(25.dp, 4.dp, 54.dp, 82.dp, 0f),
    )
private val FAN_ORDER = listOf(1, 2, 0) // which summary cover sits in each FAN slot: the first cover in front

@Composable
private fun CoverStack(
    ids: List<Int>,
    server: Server?,
) = Box(Modifier.size(104.dp, 96.dp)) {
    FAN.forEachIndexed { slot, f ->
        val id = ids.getOrNull(FAN_ORDER[slot])
        Box(
            Modifier
                .offset(f.x, f.y)
                .size(f.width, f.height)
                .rotate(f.angle)
                .shadow(if (slot == FAN.lastIndex) 7.dp else 5.dp, COVER_SHAPE),
        ) {
            if (id != null) {
                CoverImage(
                    "",
                    coverColorFor(id.toLong()),
                    Modifier.fillMaxSize(),
                    shape = COVER_SHAPE,
                    titleSize = 0.sp,
                    imageUrl = server?.seriesCoverUrl(id),
                )
            } else {
                Box(Modifier.fillMaxSize().clip(COVER_SHAPE).background(Kavita.colors.track))
            }
        }
    }
}
