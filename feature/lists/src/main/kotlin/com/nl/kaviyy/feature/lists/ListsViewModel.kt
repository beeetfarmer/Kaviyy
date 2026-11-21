package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.FilterScope
import com.nl.kaviyy.core.data.LibraryFilterStore
import com.nl.kaviyy.core.data.ListSummary
import com.nl.kaviyy.core.data.ListsRepository
import com.nl.kaviyy.core.data.SeriesFilter
import com.nl.kaviyy.core.data.collectionQuery
import com.nl.kaviyy.core.data.editableBy
import com.nl.kaviyy.core.data.wantToReadQuery
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L
const val COLLECTION_ID_ARG = "collectionId"

data class ListsUiState(
    val server: Server? = null,
    val wantToRead: ListSummary = ListSummary(0, false, emptyList(), 0),
    val collections: List<CollectionEntity> = emptyList(),
)

data class EditCollectionActions(
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

data class ListsActions(
    val onOpenWantToRead: () -> Unit = {},
    val onOpenCollection: (Int) -> Unit = {},
    val onSeeAllCollections: () -> Unit = {},
)

/** Screen 08. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListsViewModel
    @Inject
    constructor(
        session: AuthSession,
        private val lists: ListsRepository,
    ) : ViewModel() {
        private val server = session.activeServer.distinctUntilChangedBy { it?.id }

        val state: StateFlow<ListsUiState> =
            server
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(ListsUiState())
                    } else {
                        combine(lists.wantToRead(s), lists.collections(s)) { w, c -> ListsUiState(s, w, c) }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ListsUiState())

        init {
            viewModelScope.launch {
                server.filterNotNull().collect {
                    lists.refreshWantToRead(it)
                    lists.refreshCollections(it)
                }
            }
        }
    }

data class CollectionUiState(
    val server: Server? = null,
    val collection: CollectionEntity? = null,
    /** The user owns it and it isn't synced from elsewhere. */
    val editable: Boolean = false,
    val editing: Boolean = false,
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    /** Deleted: the screen closes. */
    val deleted: Boolean = false,
)

private data class Edit(
    val editing: Boolean = false,
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val deleted: Boolean = false,
)

/** [derived] One collection: its series in the Library grid (Kavita's collection filter rule); owners edit it. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        repository: CatalogRepository,
        private val lists: ListsRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val id: Int = checkNotNull(saved.get<Int>(COLLECTION_ID_ARG)) { "missing $COLLECTION_ID_ARG" }
        private val server = session.activeServer.distinctUntilChangedBy { it?.id }
        private val edit = MutableStateFlow(Edit())
        val title = TextFieldState()
        val summary = TextFieldState()

        val state: StateFlow<CollectionUiState> =
            server
                .flatMapLatest { s -> if (s == null) flowOf(s to null) else lists.collection(s, id).map { s to it } }
                .combine(edit) { (s, c), e ->
                    CollectionUiState(
                        s,
                        c,
                        editable = s != null && c?.editableBy(s) == true,
                        editing = e.editing,
                        confirmDelete = e.confirmDelete,
                        busy = e.busy,
                        error = e.error,
                        deleted = e.deleted,
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionUiState())

        val series: Flow<PagingData<SeriesEntity>> =
            server
                .flatMapLatest { s -> if (s == null) emptyFlow() else repository.librarySeries(s, collectionQuery(id)) }
                .cachedIn(viewModelScope)

        fun startEdit() {
            val c = state.value.collection ?: return
            title.setTextAndPlaceCursorAtEnd(c.title)
            summary.setTextAndPlaceCursorAtEnd(c.summary.orEmpty())
            edit.value = Edit(editing = true)
        }

        fun closeEdit() = edit.update { Edit() }

        fun save() {
            val c = state.value.collection ?: return
            val name = title.text.toString().trim()
            if (name.isEmpty()) return
            change { s ->
                lists
                    .updateCollection(
                        s,
                        c,
                        name,
                        summary.text.toString(),
                    ).also { if (it is AppResult.Success) closeEdit() }
            }
        }

        /** First tap asks, the second deletes. */
        fun delete() {
            if (!edit.value.confirmDelete) {
                edit.update { it.copy(confirmDelete = true) }
                return
            }
            change { s ->
                lists.deleteCollection(s, id).also { r ->
                    if (r is AppResult.Success) edit.update { it.copy(deleted = true) }
                }
            }
        }

        private fun change(block: suspend (Server) -> AppResult<Unit>) {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                edit.update { it.copy(busy = true, error = null) }
                val r = block(s)
                edit.update { it.copy(busy = false, error = (r as? AppResult.Failure)?.error) }
            }
        }
    }

data class WantToReadUiState(
    val server: Server? = null,
    val filter: SeriesFilter = SeriesFilter(),
)

/** Want to Read [derived]: the Library grid pattern over Kavita's want-to-read list, with its own filter. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WantToReadViewModel
    @Inject
    constructor(
        session: AuthSession,
        repository: CatalogRepository,
        store: LibraryFilterStore,
    ) : ViewModel() {
        private val server = session.activeServer.distinctUntilChangedBy { it?.id }
        private val filter =
            server.flatMapLatest { s ->
                if (s == null) flowOf(SeriesFilter()) else store.filter(s.id, FilterScope.WantToRead)
            }

        val state: StateFlow<WantToReadUiState> =
            combine(server, filter, ::WantToReadUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WantToReadUiState())

        val series: Flow<PagingData<SeriesEntity>> =
            combine(server, filter) { s, f -> s to f }
                .flatMapLatest { (s, f) ->
                    if (s == null) emptyFlow() else repository.librarySeries(s, wantToReadQuery(f))
                }.cachedIn(viewModelScope)
    }
