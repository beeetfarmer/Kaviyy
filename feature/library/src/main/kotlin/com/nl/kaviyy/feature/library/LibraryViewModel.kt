package com.nl.kaviyy.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.LibraryFilterStore
import com.nl.kaviyy.core.data.LibraryQuery
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SavedFilter
import com.nl.kaviyy.core.data.SeriesFilter
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.data.SmartFilterRepository
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
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

/** Chip shortcuts: each one owns a well-known rule inside the shared [SeriesFilter]. */
internal val UNREAD_RULE = FilterRule(FilterField.ReadProgress, Comparison.LessThan, listOf(RuleValue("100")))

internal fun SeriesFilter.unreadOnly(): Boolean = UNREAD_RULE in rules

/** The chip's rule: the first "is any of" rule on [field]. */
internal fun SeriesFilter.chipRule(field: FilterField): FilterRule? =
    rules.firstOrNull { it.field == field && it.comparison == Comparison.Contains }

internal fun SeriesFilter.withChip(
    field: FilterField,
    value: RuleValue?,
): SeriesFilter {
    val existing = chipRule(field)
    val next = value?.let { FilterRule(field, Comparison.Contains, listOf(it)) }
    val rules =
        when {
            existing == null -> rules + listOfNotNull(next)
            next == null -> rules - existing
            else -> rules.map { if (it == existing) next else it }
        }
    return copy(rules = rules)
}

enum class LibrarySheet { Sort, Genre, Status }

/** Genre/Status sheet contents; loaded from the server when the sheet opens. */
sealed interface SheetOptions {
    data object Loading : SheetOptions

    data class Loaded(
        val options: List<RuleValue>,
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
    val filter: SeriesFilter = SeriesFilter(),
    val sheet: LibrarySheet? = null,
    val options: SheetOptions = SheetOptions.Loading,
    /** Smart filters pinned in Library ("Pin in Library"), shown as chips. */
    val pinned: List<SavedFilter> = emptyList(),
) {
    val selectedIndex: Int get() = libraries.orEmpty().indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    val query: LibraryQuery? get() = selectedId?.let { LibraryQuery(it, filter) }
}

/** What the user chose on this screen; picked library and layout survive process death (the filter too, below). */
private data class Local(
    val picked: Int? = null,
    val grid: Boolean = true,
    val sheet: LibrarySheet? = null,
    val options: SheetOptions = SheetOptions.Loading,
    val pinned: List<SavedFilter> = emptyList(),
)

private const val STOP_TIMEOUT_MS = 5_000L
private const val KEY_LIBRARY = "library"
private const val KEY_GRID = "grid"
private const val KEY_FILTER = "filter"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        session: AuthSession,
        private val repository: CatalogRepository,
        private val store: LibraryFilterStore,
        private val smartFilters: SmartFilterRepository,
        private val saved: SavedStateHandle,
    ) : ViewModel() {
        private val server = session.activeServer.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        private val error = MutableStateFlow<AppError?>(null)
        private val local = MutableStateFlow(saved.restore())

        private val libraries =
            server.flatMapLatest { s -> if (s == null) flowOf(null) else repository.libraries(s) }

        private val filter =
            server.flatMapLatest { s -> if (s == null) flowOf(SeriesFilter()) else store.filter(s.id) }

        val state: StateFlow<LibraryUiState> =
            combine(server, libraries, local, error, filter) { s, libs, l, e, f ->
                // A library removed on the server (or a server switch) falls back to the first one.
                val selected = libs?.firstOrNull { it.id == l.picked }?.id ?: libs?.firstOrNull()?.id
                LibraryUiState(s, libs, selected, l.grid, e, f, l.sheet, l.options, l.pinned)
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
                server.filterNotNull().distinctUntilChangedBy { it.id }.collectLatest { s ->
                    saved.get<String>(KEY_FILTER)?.let(SeriesFilter::decode)?.let { store.restore(s.id, it) }
                    refreshLibraries(s)
                }
            }
            viewModelScope.launch { filter.collect { saved[KEY_FILTER] = it.encode() } }
            // ponytail: re-reads pins on every filter change (the sheet may have pinned one); cheap GETs.
            viewModelScope.launch {
                server.filterNotNull().collectLatest { s ->
                    filter.collectLatest {
                        smartFilters.pinned(s).getOrNull()?.let { list -> local.update { l -> l.copy(pinned = list) } }
                    }
                }
            }
        }

        /** A pinned chip replaces the filter with the saved one. */
        fun openPinned(pinned: SavedFilter) {
            val s = server.value ?: return
            viewModelScope.launch { smartFilters.load(s, pinned).getOrNull()?.let { f -> store.update(s.id) { f } } }
        }

        fun select(libraryId: Int) = change { it.copy(picked = libraryId) }

        fun toggleLayout() = change { it.copy(grid = !it.grid) }

        fun toggleUnread() =
            filter {
                if (it.unreadOnly()) {
                    it.copy(rules = it.rules - UNREAD_RULE)
                } else {
                    it.copy(
                        rules =
                            it.rules + UNREAD_RULE,
                    )
                }
            }

        fun setSort(sort: SeriesSort) {
            filter { it.copy(sort = sort) }
            showSheet(null)
        }

        /** Null clears the chip. */
        fun pick(option: RuleValue?) {
            when (local.value.sheet) {
                LibrarySheet.Genre -> filter { it.withChip(FilterField.Genres, option) }
                LibrarySheet.Status -> filter { it.withChip(FilterField.PublicationStatus, option) }
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
                    val source =
                        if (sheet ==
                            LibrarySheet.Genre
                        ) {
                            OptionSource.Genres
                        } else {
                            OptionSource.PublicationStatus
                        }
                    val result = repository.filterOptions(s, source, listOf(libraryId))
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

        private fun filter(transform: (SeriesFilter) -> SeriesFilter) {
            server.value?.let { store.update(it.id, transform) }
        }

        private fun change(transform: (Local) -> Local) = saved.save(local.updateAndGet(transform))
    }

private fun SavedStateHandle.save(local: Local) {
    this[KEY_LIBRARY] = local.picked
    this[KEY_GRID] = local.grid
}

private fun SavedStateHandle.restore(): Local = Local(picked = get(KEY_LIBRARY), grid = get(KEY_GRID) ?: true)
