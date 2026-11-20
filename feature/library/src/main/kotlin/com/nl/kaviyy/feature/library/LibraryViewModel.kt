package com.nl.kaviyy.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.FilterOption
import com.nl.kaviyy.core.data.LibraryQuery
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.database.LibraryEntity
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sort + quick chips, independent of the library (genre/status ids are server-global). */
data class QuickFilters(
    val sort: SeriesSort = SeriesSort.RecentlyAdded,
    val unreadOnly: Boolean = false,
    val genre: FilterOption? = null,
    val status: FilterOption? = null,
)

enum class LibrarySheet { Sort, Genre, Status }

/** Genre/Status sheet contents; loaded from the server when the sheet opens. */
sealed interface SheetOptions {
    data object Loading : SheetOptions

    data class Loaded(
        val options: List<FilterOption>,
    ) : SheetOptions

    data class Failed(
        val error: AppError,
    ) : SheetOptions
}

data class LibraryUiState(
    val server: Server? = null,
    /** Null until the cache has been read. */
    val libraries: List<LibraryEntity>? = null,
    val selectedId: Int? = null,
    val grid: Boolean = true,
    /** Libraries refresh failed (the grid reports its own errors through paging). */
    val error: AppError? = null,
    val filters: QuickFilters = QuickFilters(),
    val sheet: LibrarySheet? = null,
    val options: SheetOptions = SheetOptions.Loading,
) {
    val selectedIndex: Int get() = libraries.orEmpty().indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    val query: LibraryQuery?
        get() = selectedId?.let { LibraryQuery(it, filters.sort, filters.unreadOnly, filters.genre, filters.status) }
}

/** What the user chose on this screen; picked library, layout and filters survive process death. */
private data class Local(
    val picked: Int? = null,
    val grid: Boolean = true,
    val filters: QuickFilters = QuickFilters(),
    val sheet: LibrarySheet? = null,
    val options: SheetOptions = SheetOptions.Loading,
)

private const val STOP_TIMEOUT_MS = 5_000L
private const val KEY_LIBRARY = "library"
private const val KEY_GRID = "grid"
private const val KEY_SORT = "sort"
private const val KEY_UNREAD = "unread"
private const val KEY_GENRE = "genre"
private const val KEY_STATUS = "status"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        session: AuthSession,
        private val repository: CatalogRepository,
        private val saved: SavedStateHandle,
    ) : ViewModel() {
        private val server = session.activeServer.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        private val error = MutableStateFlow<AppError?>(null)
        private val local = MutableStateFlow(saved.restore())

        private val libraries =
            server.flatMapLatest { s -> if (s == null) flowOf(null) else repository.libraries(s) }

        val state: StateFlow<LibraryUiState> =
            combine(server, libraries, local, error) { s, libs, l, e ->
                // A library removed on the server (or a server switch) falls back to the first one.
                val selected = libs?.firstOrNull { it.id == l.picked }?.id ?: libs?.firstOrNull()?.id
                LibraryUiState(s, libs, selected, l.grid, e, l.filters, l.sheet, l.options)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

        /** The current query's series; empty until there is a library. */
        val series: Flow<PagingData<SeriesEntity>> =
            state
                .map { it.server to it.query }
                .distinctUntilChanged()
                .flatMapLatest { (s, q) -> if (s == null || q == null) emptyFlow() else repository.librarySeries(s, q) }
                .cachedIn(viewModelScope)

        init {
            viewModelScope.launch {
                server.filterNotNull().distinctUntilChangedBy { it.id }.collectLatest { refreshLibraries(it) }
            }
        }

        fun select(libraryId: Int) = change { it.copy(picked = libraryId) }

        fun toggleLayout() = change { it.copy(grid = !it.grid) }

        fun toggleUnread() = filters { it.copy(unreadOnly = !it.unreadOnly) }

        fun setSort(sort: SeriesSort) {
            filters { it.copy(sort = sort) }
            showSheet(null)
        }

        /** Null clears the chip. */
        fun pick(option: FilterOption?) {
            when (local.value.sheet) {
                LibrarySheet.Genre -> filters { it.copy(genre = option) }
                LibrarySheet.Status -> filters { it.copy(status = option) }
                else -> Unit
            }
            showSheet(null)
        }

        /** Opens (null closes) a sheet; Genre/Status load their options from the server. */
        fun showSheet(sheet: LibrarySheet?) {
            change { it.copy(sheet = sheet, options = SheetOptions.Loading) }
            val s = server.value ?: return
            val libraryId = state.value.selectedId ?: return
            if (sheet == LibrarySheet.Genre || sheet == LibrarySheet.Status) {
                viewModelScope.launch {
                    val result =
                        if (sheet == LibrarySheet.Genre) {
                            repository.genres(s, libraryId)
                        } else {
                            repository.publicationStatuses(s, libraryId)
                        }
                    val options =
                        when (result) {
                            is AppResult.Success -> SheetOptions.Loaded(result.value)
                            is AppResult.Failure -> SheetOptions.Failed(result.error)
                        }
                    local.update { if (it.sheet == sheet) it.copy(options = options) else it }
                }
            }
        }

        fun retry() {
            server.value?.let { s -> viewModelScope.launch { refreshLibraries(s) } }
        }

        private suspend fun refreshLibraries(s: Server) {
            val result = repository.refreshLibraries(s)
            error.update { (result as? AppResult.Failure)?.error }
        }

        private fun filters(transform: (QuickFilters) -> QuickFilters) =
            change { it.copy(filters = transform(it.filters)) }

        private fun change(transform: (Local) -> Local) = saved.save(local.updateAndGet(transform))
    }

private fun SavedStateHandle.save(local: Local) {
    this[KEY_LIBRARY] = local.picked
    this[KEY_GRID] = local.grid
    this[KEY_SORT] = local.filters.sort.name
    this[KEY_UNREAD] = local.filters.unreadOnly
    this[KEY_GENRE] = local.filters.genre?.let { arrayListOf(it.id.toString(), it.title) }
    this[KEY_STATUS] = local.filters.status?.let { arrayListOf(it.id.toString(), it.title) }
}

private fun SavedStateHandle.restore(): Local {
    fun option(key: String) =
        get<ArrayList<String>>(key)?.let { (id, title) -> id.toIntOrNull()?.let { FilterOption(it, title) } }
    val sort = get<String>(KEY_SORT)?.let { name -> SeriesSort.entries.firstOrNull { it.name == name } }
    return Local(
        picked = get(KEY_LIBRARY),
        grid = get(KEY_GRID) ?: true,
        filters =
            QuickFilters(
                sort ?: SeriesSort.RecentlyAdded,
                get(KEY_UNREAD) ?: false,
                option(KEY_GENRE),
                option(KEY_STATUS),
            ),
    )
}
