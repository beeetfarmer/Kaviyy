package com.nl.kaviyy.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val server: Server? = null,
    /** Null until the cache has been read. */
    val libraries: List<LibraryEntity>? = null,
    val selectedId: Int? = null,
    val grid: Boolean = true,
    /** Libraries refresh failed (the grid reports its own errors through paging). */
    val error: AppError? = null,
) {
    val selectedIndex: Int get() = libraries.orEmpty().indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
}

private const val STOP_TIMEOUT_MS = 5_000L
private const val KEY_LIBRARY = "library"
private const val KEY_GRID = "grid"

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
        private val picked = saved.getStateFlow<Int?>(KEY_LIBRARY, null)
        private val grid = saved.getStateFlow(KEY_GRID, true)
        private val error = MutableStateFlow<AppError?>(null)

        private val libraries =
            server.flatMapLatest { s -> if (s == null) flowOf(null) else repository.libraries(s) }

        val state: StateFlow<LibraryUiState> =
            combine(server, libraries, picked, grid, error) { s, libs, pick, g, e ->
                // A library removed on the server (or a server switch) falls back to the first one.
                val selected = libs?.firstOrNull { it.id == pick }?.id ?: libs?.firstOrNull()?.id
                LibraryUiState(s, libs, selected, g, e)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

        /** The selected library's series; empty until there is a library. */
        val series: Flow<PagingData<SeriesEntity>> =
            state
                .map { it.server to it.selectedId }
                .distinctUntilChanged()
                .flatMapLatest { (s, id) ->
                    if (s == null || id == null) emptyFlow() else repository.librarySeries(s, id)
                }.cachedIn(viewModelScope)

        init {
            viewModelScope.launch {
                server.filterNotNull().distinctUntilChangedBy { it.id }.collectLatest { refreshLibraries(it) }
            }
        }

        fun select(libraryId: Int) {
            saved[KEY_LIBRARY] = libraryId
        }

        fun toggleLayout() {
            saved[KEY_GRID] = !grid.value
        }

        fun retry() {
            server.value?.let { s -> viewModelScope.launch { refreshLibraries(s) } }
        }

        private suspend fun refreshLibraries(s: Server) {
            val result = repository.refreshLibraries(s)
            error.update { (result as? AppResult.Failure)?.error }
        }
    }
