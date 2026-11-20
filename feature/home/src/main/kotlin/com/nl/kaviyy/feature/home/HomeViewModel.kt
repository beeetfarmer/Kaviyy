package com.nl.kaviyy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.Home
import com.nl.kaviyy.core.data.HomeItem
import com.nl.kaviyy.core.data.HomeRepository
import com.nl.kaviyy.core.data.NetworkMonitor
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val server: Server? = null,
    /** Null until the cache has been read. */
    val home: Home? = null,
    val refreshing: Boolean = false,
    /** Last refresh failed; cached content (if any) is still shown. */
    val error: AppError? = null,
    /** No validated network: Downloaded rail first, grey "Offline" pill, other covers dimmed. */
    val offline: Boolean = false,
) {
    val downloadedRail: List<HomeItem> get() = if (offline) home?.downloaded.orEmpty() else emptyList()

    /** Offline already explains itself via the pill. */
    val showStaleNotice: Boolean get() = error != null && !offline && home?.isEmpty == false

    val showEmpty: Boolean get() = home?.isEmpty == true && !refreshing

    /** Offline, only downloaded series can be opened (DESIGN.md §8). */
    fun isDimmed(seriesId: Int): Boolean = offline && seriesId !in home?.downloadedIds.orEmpty()
}

/** Navigation out of Home; wired in `app`. */
data class HomeActions(
    val onServerPill: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onSeeAll: (com.nl.kaviyy.core.data.HomeRail) -> Unit = {},
    val onSeeDownloads: () -> Unit = {},
    val onOpenSeries: (seriesId: Int) -> Unit = {},
    val onResume: (com.nl.kaviyy.core.data.HomeItem) -> Unit = {},
)

private data class RefreshStatus(
    val running: Boolean = false,
    val error: AppError? = null,
)

private const val STOP_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        session: AuthSession,
        private val repository: HomeRepository,
        network: NetworkMonitor,
    ) : ViewModel() {
        private val online = network.isOnline().stateIn(viewModelScope, SharingStarted.Eagerly, true)
        private val server = session.activeServer.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        private val status = MutableStateFlow(RefreshStatus())

        val state: StateFlow<HomeUiState> =
            server
                .flatMapLatest { s ->
                    if (s == null) flowOf(HomeUiState()) else repository.home(s).map { home -> HomeUiState(s, home) }
                }.combine(status) { ui, st -> ui.copy(refreshing = st.running, error = st.error) }
                .combine(online) { ui, on -> ui.copy(offline = !on) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

        init {
            // Refresh when Home opens and whenever the user switches server.
            viewModelScope.launch { server.filterNotNull().distinctUntilChangedBy { it.id }.collectLatest(::refresh) }
            // Back online: catch up.
            viewModelScope.launch { online.drop(1).filter { it }.collect { refresh() } }
        }

        fun refresh() {
            val s = server.value ?: return
            if (!status.value.running) viewModelScope.launch { refresh(s) }
        }

        private suspend fun refresh(s: Server) {
            status.value = RefreshStatus(running = true)
            val result = repository.refresh(s)
            status.value = RefreshStatus(error = (result as? AppResult.Failure)?.error)
        }
    }
