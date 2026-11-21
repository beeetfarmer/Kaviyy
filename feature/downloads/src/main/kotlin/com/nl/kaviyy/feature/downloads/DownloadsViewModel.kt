package com.nl.kaviyy.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.download.DownloadControls
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.sync.SyncStatus
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

private const val STOP_TIMEOUT_MS = 5_000L

/** Downloaded chapters of one series, for the Downloaded segment. */
data class SeriesGroup(
    val seriesId: Int,
    val name: String?,
    val chapters: Int,
    val bytes: Long,
)

data class DownloadsUiState(
    val server: Server? = null,
    /** 0 Downloaded, 1 Queue. */
    val segment: Int = 0,
    val downloaded: List<SeriesGroup> = emptyList(),
    /** Not finished yet: running first, then waiting, paused, failed. */
    val queue: List<DownloadItem> = emptyList(),
    val loading: Boolean = true,
    /** Bytes on disk across all downloads. */
    val usedBytes: Long = 0,
    val storage: List<Pair<StorageKind, Long>> = emptyList(),
    /** Local changes not on the server yet. */
    val pendingChanges: Int = 0,
    /** Selecting downloaded series to mark read or delete together. */
    val selecting: Boolean = false,
    val selected: Set<Int> = emptySet(),
    /** Deleting selected series asks once more. */
    val confirmDelete: Boolean = false,
) {
    val allSelected: Boolean get() = downloaded.isNotEmpty() && downloaded.all { it.seriesId in selected }
}

data class DownloadsActions(
    val onSegment: (Int) -> Unit = {},
    val onOpenSeries: (seriesId: Int) -> Unit = {},
    val onBrowse: () -> Unit = {},
    val onPause: (chapterId: Int) -> Unit = {},
    val onResume: (chapterId: Int) -> Unit = {},
    val onRetry: (chapterId: Int) -> Unit = {},
    val onCancel: (chapterId: Int) -> Unit = {},
    val onPauseAll: () -> Unit = {},
    val onResumeAll: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRemoveRead: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onToggleSelecting: () -> Unit = {},
    val onToggleSeries: (seriesId: Int) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onMarkSelectedRead: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
)

private data class SeriesSelection(
    val selecting: Boolean = false,
    val selected: Set<Int> = emptySet(),
    val confirmDelete: Boolean = false,
)

/** Running or waiting: can be paused. */
internal val DownloadItem.pausable get() = state == DownloadState.RUNNING || state == DownloadState.QUEUED

private val QUEUE_ORDER =
    listOf(DownloadState.RUNNING, DownloadState.QUEUED, DownloadState.PAUSED, DownloadState.FAILED)

internal fun List<DownloadItem>.toUiState(
    server: Server,
    segment: Int,
): DownloadsUiState {
    val (done, pending) = partition { it.state == DownloadState.COMPLETED }
    val groups =
        done
            .groupBy { it.row.seriesId }
            .map { (seriesId, items) ->
                SeriesGroup(seriesId, items.first().seriesName, items.size, items.sumOf { it.row.bytesDone })
            }.sortedBy { it.name?.lowercase() }
    return DownloadsUiState(
        server,
        segment,
        groups,
        pending.sortedWith(compareBy({ QUEUE_ORDER.indexOf(it.state) }, { it.row.updatedAt })),
        loading = false,
        usedBytes = sumOf { it.row.bytesDone },
        storage = storageBreakdown(),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val controls: DownloadControls,
        private val sync: SyncStatus,
        private val readState: ReadStateRepository,
    ) : ViewModel() {
        private val segment = MutableStateFlow(0)
        private val selection = MutableStateFlow(SeriesSelection())

        val state: StateFlow<DownloadsUiState> =
            session.activeServer
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(DownloadsUiState(loading = false))
                    } else {
                        combine(
                            controls.downloads(s),
                            segment,
                            sync.pending(s),
                            selection,
                        ) { items, seg, pending, sel ->
                            val ui = items.toUiState(s, seg)
                            val ids = ui.downloaded.mapTo(HashSet()) { it.seriesId }
                            ui.copy(
                                pendingChanges = pending,
                                selecting = sel.selecting && seg == 0,
                                selected = sel.selected intersect ids,
                                confirmDelete = sel.confirmDelete,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadsUiState())

        fun select(index: Int) {
            segment.value = index
            selection.value = SeriesSelection()
        }

        fun toggleSelecting() = selection.update { SeriesSelection(selecting = !it.selecting) }

        fun toggleSeries(seriesId: Int) =
            selection.update {
                val selected = if (seriesId in it.selected) it.selected - seriesId else it.selected + seriesId
                it.copy(selected = selected, confirmDelete = false)
            }

        /** Selects every downloaded series, or clears the selection when all already are. */
        fun selectAll() =
            selection.update {
                val all = state.value.downloaded.mapTo(HashSet()) { g -> g.seriesId }
                it.copy(selected = if (state.value.allSelected) emptySet() else all, confirmDelete = false)
            }

        /** Marks the downloaded chapters of the selected series read (saved locally, synced when online). */
        fun markSelectedRead() {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                // ponytail: one call per chapter (offline-safe via the outbox); mark-multiple-read if it gets slow.
                selectedChapters().forEach { (seriesId, chapterId) ->
                    readState.setRead(server, ReadTarget.Chapter(seriesId, chapterId), true)
                }
                selection.value = SeriesSelection()
            }
        }

        /** First tap asks, the second deletes every downloaded chapter of the selected series. */
        fun deleteSelected() {
            if (!selection.value.confirmDelete) {
                selection.update { it.copy(confirmDelete = true) }
                return
            }
            act { id ->
                selectedChapters().forEach { (_, chapterId) -> controls.cancel(id, chapterId) }
                selection.value = SeriesSelection()
            }
        }

        /** (seriesId, chapterId) of every finished download in the selected series. */
        private suspend fun selectedChapters(): List<Pair<Int, Int>> {
            val server = session.activeServer.first() ?: return emptyList()
            val chosen = state.value.selected
            return controls
                .downloads(server)
                .first()
                .filter { it.state == DownloadState.COMPLETED && it.row.seriesId in chosen }
                .map { it.row.seriesId to it.row.chapterId }
        }

        fun pause(chapterId: Int) = act { controls.pause(it, chapterId) }

        fun resume(chapterId: Int) = act { controls.resume(it, chapterId) }

        fun retry(chapterId: Int) = act { controls.retry(it, chapterId) }

        fun cancel(chapterId: Int) = act { controls.cancel(it, chapterId) }

        fun pauseAll() = act { id -> queued().filter { it.pausable }.forEach { controls.pause(id, it.row.chapterId) } }

        fun resumeAll() =
            act { id ->
                queued().filter { it.state == DownloadState.PAUSED }.forEach { controls.resume(id, it.row.chapterId) }
            }

        fun removeRead() = act { controls.removeRead(it) }

        fun syncNow() = sync.syncNow()

        private fun queued() = state.value.queue

        private fun act(block: suspend (serverId: Long) -> Unit) {
            viewModelScope.launch { session.activeServer.first()?.let { block(it.id.value) } }
        }
    }
