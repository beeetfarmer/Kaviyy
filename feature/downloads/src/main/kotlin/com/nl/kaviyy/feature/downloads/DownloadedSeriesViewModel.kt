package com.nl.kaviyy.feature.downloads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.download.DownloadControls
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val DOWNLOADED_SERIES_ARG = "seriesId"
private const val STOP_TIMEOUT_MS = 5_000L

data class DownloadedSeriesUiState(
    val server: Server? = null,
    val seriesName: String? = null,
    /** Finished downloads of this series, in reading order. */
    val chapters: List<DownloadItem> = emptyList(),
    val selecting: Boolean = false,
    val selected: Set<Int> = emptySet(),
    /** "Delete all" asks first. */
    val confirmDeleteAll: Boolean = false,
    val loading: Boolean = true,
)

data class DownloadedSeriesActions(
    val onBack: () -> Unit = {},
    val onToggleSelecting: () -> Unit = {},
    val onToggle: (chapterId: Int) -> Unit = {},
    val onDelete: (chapterIds: Set<Int>) -> Unit = {},
    val onMarkRead: (chapterIds: Set<Int>) -> Unit = {},
    val onDeleteAll: () -> Unit = {},
)

private data class Selection(
    val selecting: Boolean = false,
    val selected: Set<Int> = emptySet(),
    val confirmDeleteAll: Boolean = false,
)

/** [derived] One series' downloaded chapters: size and read state, swipe or select to delete / mark read. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadedSeriesViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val controls: DownloadControls,
        private val readState: ReadStateRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val seriesId: Int =
            checkNotNull(saved.get<Int>(DOWNLOADED_SERIES_ARG)) { "missing $DOWNLOADED_SERIES_ARG" }
        private val selection = MutableStateFlow(Selection())

        val state: StateFlow<DownloadedSeriesUiState> =
            session.activeServer
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(DownloadedSeriesUiState(loading = false))
                    } else {
                        combine(controls.downloads(s), selection) { items, sel ->
                            val mine =
                                items
                                    .filter { it.row.seriesId == seriesId && it.state == DownloadState.COMPLETED }
                                    .sortedWith(compareBy({ it.chapter?.sortOrder }, { it.row.chapterId }))
                            DownloadedSeriesUiState(
                                s,
                                mine.firstNotNullOfOrNull { it.seriesName },
                                mine,
                                sel.selecting,
                                sel.selected.filterTo(mutableSetOf()) { id -> mine.any { it.row.chapterId == id } },
                                sel.confirmDeleteAll,
                                loading = false,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadedSeriesUiState())

        fun toggleSelecting() = selection.update { Selection(selecting = !it.selecting) }

        fun toggle(chapterId: Int) =
            selection.update {
                it.copy(selected = if (chapterId in it.selected) it.selected - chapterId else it.selected + chapterId)
            }

        fun delete(chapterIds: Set<Int>) {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                chapterIds.forEach { controls.cancel(s.id.value, it) }
                selection.value = Selection()
            }
        }

        /** First tap asks, the second deletes every downloaded chapter of the series. */
        fun deleteAll() {
            if (!selection.value.confirmDeleteAll) {
                selection.update { it.copy(confirmDeleteAll = true) }
                return
            }
            delete(state.value.chapters.mapTo(mutableSetOf()) { it.row.chapterId })
        }

        fun markRead(chapterIds: Set<Int>) {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                // Saved locally first and synced later when offline (outbox).
                chapterIds.forEach { readState.setRead(s, ReadTarget.Chapter(seriesId, it), true) }
                selection.value = Selection()
            }
        }
    }
