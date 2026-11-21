package com.nl.kaviyy.feature.series

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.data.editableBy
import com.nl.kaviyy.core.database.ReadingListEntity
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.flatMap
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

data class ReadingListsSheetState(
    /** The user's own reading lists. */
    val lists: List<ReadingListEntity> = emptyList(),
    /** Ids already holding some of this series; null until loaded. */
    val holding: Set<Int>? = null,
    val busy: Boolean = false,
    val error: AppError? = null,
)

private data class SheetLocal(
    val holding: Set<Int>? = null,
    val busy: Boolean = false,
    val error: AppError? = null,
)

/**
 * [derived] Add to reading list: appends the whole series to one of the user's lists, or to a new one. Kavita adds
 * series by chapter and has no "remove series", so lists already holding it show a check (remove items in the list).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingListsSheetViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val lists: ReadingListRepository,
    ) : ViewModel() {
        val newName = TextFieldState()
        private val local = MutableStateFlow(SheetLocal())
        private var seriesId = 0

        val state: StateFlow<ReadingListsSheetState> =
            session.activeServer
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(emptyList())
                    } else {
                        lists.lists(s).map { rows -> rows.map { it.list }.filter { it.editableBy(s) } }
                    }
                }.combine(local) { mine, l -> ReadingListsSheetState(mine, l.holding, l.busy, l.error) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReadingListsSheetState())

        fun open(series: Int) {
            seriesId = series
            run { s ->
                lists.refreshLists(s)
                reload(s)
            }
        }

        fun add(list: ReadingListEntity) {
            if (local.value.holding?.contains(list.id) != false) return
            run { s -> lists.addSeries(s, list.id, seriesId).flatMap { reload(s) } }
        }

        fun create() {
            val name = newName.text.toString().trim()
            if (name.isEmpty()) return
            run { s ->
                lists
                    .create(s, name)
                    .flatMap { id -> lists.addSeries(s, id, seriesId) }
                    .flatMap { reload(s) }
                    .also { if (it is AppResult.Success) newName.clearText() }
            }
        }

        private suspend fun reload(s: Server): AppResult<Unit> =
            lists
                .listsWith(s, seriesId)
                .also { r -> r.getOrNull()?.let { h -> local.update { it.copy(holding = h) } } }
                .flatMap { AppResult.Success(Unit) }

        private fun run(block: suspend (Server) -> AppResult<*>) {
            if (local.value.busy) return
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                local.update { it.copy(busy = true, error = null) }
                val r = block(s)
                local.update { it.copy(busy = false, error = (r as? AppResult.Failure)?.error) }
            }
        }
    }

@Composable
internal fun ReadingListsSheet(
    seriesId: Int,
    onDismiss: () -> Unit,
    viewModel: ReadingListsSheetViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.open(seriesId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReadingListsSheetContent(state, viewModel.newName, onDismiss, viewModel::add, viewModel::create)
}

@Composable
internal fun ReadingListsSheetContent(
    state: ReadingListsSheetState,
    newName: TextFieldState,
    onDismiss: () -> Unit,
    onAdd: (ReadingListEntity) -> Unit,
    onCreate: () -> Unit,
) = KavitaBottomSheet(onDismiss = onDismiss) {
    SheetHeader(stringResource(R.string.series_reading_lists_title), stringResource(R.string.series_done), onDismiss)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        if (state.lists.isNotEmpty()) {
            SectionHeader(stringResource(R.string.series_reading_lists_yours))
            GroupedList {
                state.lists.forEachIndexed { i, l ->
                    val has = state.holding?.contains(l.id) == true
                    GroupedRow(
                        l.title,
                        onClick = { onAdd(l) }.takeIf { state.holding != null && !has && !state.busy },
                        showChevron = false,
                        divider = i < state.lists.lastIndex,
                    ) {
                        if (has) {
                            Icon(
                                KavitaIcons.Check,
                                stringResource(R.string.series_reading_lists_in),
                                Modifier.size(20.dp),
                                tint = Kavita.colors.accent,
                            )
                        }
                    }
                }
            }
        }
        SectionHeader(stringResource(R.string.series_reading_lists_new), Modifier.padding(top = 12.dp))
        GroupedList {
            GroupedInputRow(
                stringResource(R.string.series_collections_name),
                newName,
                placeholder = stringResource(R.string.series_reading_lists_name_hint),
            )
        }
        state.error?.let {
            Text(
                stringResource(R.string.series_reading_lists_failed),
                Modifier.padding(top = 8.dp),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
        PrimaryButton(
            stringResource(R.string.series_collections_create),
            onCreate,
            Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = !state.busy && newName.text.isNotBlank(),
        )
    }
}
