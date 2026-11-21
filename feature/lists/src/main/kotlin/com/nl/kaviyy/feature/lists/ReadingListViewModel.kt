package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.data.editableBy
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.download.DownloadQueue
import com.nl.kaviyy.core.download.EnqueueResult
import com.nl.kaviyy.core.download.toDownload
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val READING_LIST_ID_ARG = "readingListId"
private const val STOP_TIMEOUT_MS = 5_000L

data class ReadingListActions(
    val onBack: () -> Unit = {},
    val onRead: (seriesId: Int, chapterId: Int) -> Unit = { _, _ -> },
    val onArrange: () -> Unit = {},
    val onMove: (index: Int, by: Int) -> Unit = { _, _ -> },
    val onRemove: (ReadingListItemEntity) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDownload: () -> Unit = {},
)

data class ReadingListUiState(
    val server: Server? = null,
    val list: ReadingListRow? = null,
    val items: List<ReadingListItemEntity> = emptyList(),
    /** The last refresh or change failed (the cached items are still shown). */
    val error: AppError? = null,
    /** The signed-in user owns it. */
    val editable: Boolean = false,
    /** Items show move / remove controls. */
    val arranging: Boolean = false,
    /** The name/summary/delete sheet is open. */
    val editing: Boolean = false,
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val deleted: Boolean = false,
    /** Outcome of the last "Download list". */
    val download: EnqueueResult? = null,
)

private data class Local(
    val error: AppError? = null,
    val arranging: Boolean = false,
    val editing: Boolean = false,
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val deleted: Boolean = false,
    val download: EnqueueResult? = null,
)

/** [derived] One reading list: items in order with progress; Continue opens the first unfinished one; owners edit. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingListViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val lists: ReadingListRepository,
        private val downloads: DownloadQueue,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val id: Int = checkNotNull(saved.get<Int>(READING_LIST_ID_ARG)) { "missing $READING_LIST_ID_ARG" }
        private val server = session.activeServer.distinctUntilChangedBy { it?.id }
        private val local = MutableStateFlow(Local())
        val title = TextFieldState()
        val summary = TextFieldState()

        val state: StateFlow<ReadingListUiState> =
            server
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(ReadingListUiState())
                    } else {
                        combine(lists.list(s, id), lists.items(s, id), local) { l, i, x ->
                            ReadingListUiState(
                                s,
                                l,
                                i,
                                x.error,
                                editable = l?.list?.editableBy(s) == true,
                                arranging = x.arranging,
                                editing = x.editing,
                                confirmDelete = x.confirmDelete,
                                busy = x.busy,
                                deleted = x.deleted,
                                download = x.download,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReadingListUiState())

        init {
            viewModelScope.launch {
                server.filterNotNull().collect { s ->
                    val e = (lists.refreshItems(s, id) as? AppResult.Failure)?.error
                    local.update { it.copy(error = e) }
                }
            }
        }

        fun toggleArranging() = local.update { it.copy(arranging = !it.arranging) }

        /** Moves the item at [index] by [by] places (−1 up, +1 down). */
        fun move(
            index: Int,
            by: Int,
        ) {
            val items = state.value.items
            val item = items.getOrNull(index) ?: return
            val to = items.getOrNull(index + by)?.position ?: return
            change { s -> lists.move(s, item, to) }
        }

        fun remove(item: ReadingListItemEntity) = change { s -> lists.remove(s, item) }

        fun startEdit() {
            val l = state.value.list?.list ?: return
            title.setTextAndPlaceCursorAtEnd(l.title)
            summary.setTextAndPlaceCursorAtEnd(l.summary.orEmpty())
            local.update { it.copy(editing = true, confirmDelete = false, error = null) }
        }

        fun closeEdit() = local.update { it.copy(editing = false, confirmDelete = false) }

        fun save() {
            val name = title.text.toString().trim()
            if (name.isEmpty()) return
            change { s ->
                lists.update(s, id, name, summary.text.toString()).also { if (it is AppResult.Success) closeEdit() }
            }
        }

        /** First tap asks, the second deletes. */
        fun delete() {
            if (!local.value.confirmDelete) {
                local.update { it.copy(confirmDelete = true) }
                return
            }
            change { s ->
                lists.delete(s, id).also { r -> if (r is AppResult.Success) local.update { it.copy(deleted = true) } }
            }
        }

        /** Queues every item of the list for offline reading. */
        fun download() {
            val items = state.value.items
            if (items.isEmpty()) return
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                val result = downloads.enqueue(s, items.map { it.toDownload() })
                local.update { it.copy(download = result) }
            }
        }

        private fun change(block: suspend (Server) -> AppResult<Unit>) {
            if (local.value.busy) return
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                local.update { it.copy(busy = true, error = null) }
                val r = block(s)
                local.update { it.copy(busy = false, error = (r as? AppResult.Failure)?.error) }
            }
        }
    }
