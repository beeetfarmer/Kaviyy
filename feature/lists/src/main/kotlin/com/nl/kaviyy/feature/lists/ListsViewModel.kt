package com.nl.kaviyy.feature.lists

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
import com.nl.kaviyy.core.data.wantToReadQuery
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

data class ListsUiState(
    val server: Server? = null,
    val wantToRead: ListSummary = ListSummary(0, false, emptyList(), 0),
)

data class ListsActions(
    val onOpenWantToRead: () -> Unit = {},
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
                    if (s == null) flowOf(ListsUiState()) else lists.wantToRead(s).map { ListsUiState(s, it) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ListsUiState())

        init {
            viewModelScope.launch { server.filterNotNull().collect { lists.refreshWantToRead(it) } }
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
