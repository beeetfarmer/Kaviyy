package com.nl.kaviyy.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.Home
import com.nl.kaviyy.core.data.HomeItem
import com.nl.kaviyy.core.data.HomeRepository
import com.nl.kaviyy.core.data.HomeSection
import com.nl.kaviyy.core.data.HomeSectionPref
import com.nl.kaviyy.core.data.ListSort
import com.nl.kaviyy.core.data.NetworkMonitor
import com.nl.kaviyy.core.data.parseHomeLayout
import com.nl.kaviyy.core.data.sortedReadingLists
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.datastore.AppSettingsStore
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** The user's sections, in order (hidden ones included, flagged). */
    val layout: List<HomeSectionPref> = parseHomeLayout(""),
    val readingListSort: ListSort = ListSort.Recent,
) {
    /** Offline, downloads always come first, whatever the layout says. */
    val downloadedRail: List<HomeItem> get() = if (offline) home?.downloaded.orEmpty() else emptyList()

    /** Visible sections in order; Downloaded is skipped offline (it's already first). */
    val sections: List<HomeSectionPref>
        get() = layout.filter { it.visible && !(offline && it.section == HomeSection.Downloaded) }

    val readingLists: List<ReadingListRow> get() = home?.readingLists.orEmpty().sortedReadingLists(readingListSort)

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
    val onSeeWantToRead: () -> Unit = {},
    val onOpenCollection: (Int) -> Unit = {},
    val onOpenReadingList: (Int) -> Unit = {},
    val onSeeAllReadingLists: () -> Unit = {},
    val onOpenLibrary: (Int) -> Unit = {},
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
        private val settings: AppSettingsStore,
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
                .combine(settings.settings) { ui, a ->
                    ui.copy(layout = parseHomeLayout(a.homeLayout), readingListSort = ListSort.of(a.readingListSort))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

        init {
            // Refresh when Home opens and whenever the user switches server.
            viewModelScope.launch { server.filterNotNull().distinctUntilChangedBy { it.id }.collectLatest(::refresh) }
            // Back online: catch up.
            viewModelScope.launch { online.drop(1).filter { it }.collect { refresh() } }
            // A collection or library rail switched on in You → Home Screen: fetch its series.
            viewModelScope.launch {
                settings.settings
                    .map { a -> parseHomeLayout(a.homeLayout).filter { it.visible && it.section.perItem }.toSet() }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { refresh() }
            }
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
