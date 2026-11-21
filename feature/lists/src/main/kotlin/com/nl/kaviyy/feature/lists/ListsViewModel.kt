package com.nl.kaviyy.feature.lists

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
import com.nl.kaviyy.core.data.wantToReadQuery
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L
const val COLLECTION_ID_ARG = "collectionId"

data class ListsUiState(
    val server: Server? = null,
    val wantToRead: ListSummary = ListSummary(0, false, emptyList(), 0),
    val collections: List<CollectionEntity> = emptyList(),
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
)

/** [derived] One collection: its series in the Library grid (Kavita's collection filter rule). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel
    @Inject
    constructor(
        session: AuthSession,
        repository: CatalogRepository,
        lists: ListsRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val id: Int = checkNotNull(saved.get<Int>(COLLECTION_ID_ARG)) { "missing $COLLECTION_ID_ARG" }
        private val server = session.activeServer.distinctUntilChangedBy { it?.id }

        val state: StateFlow<CollectionUiState> =
            server
                .flatMapLatest { s ->
                    if (s ==
                        null
                    ) {
                        flowOf(CollectionUiState())
                    } else {
                        lists.collection(s, id).map { CollectionUiState(s, it) }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionUiState())

        val series: Flow<PagingData<SeriesEntity>> =
            server
                .flatMapLatest { s -> if (s == null) emptyFlow() else repository.librarySeries(s, collectionQuery(id)) }
                .cachedIn(viewModelScope)
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
