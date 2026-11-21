package com.nl.kaviyy.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
)

data class DownloadsActions(
    val onSegment: (Int) -> Unit = {},
    val onOpenSeries: (seriesId: Int) -> Unit = {},
    val onBrowse: () -> Unit = {},
)

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
        session: AuthSession,
        private val controls: DownloadControls,
    ) : ViewModel() {
        private val segment = MutableStateFlow(0)

        val state: StateFlow<DownloadsUiState> =
            session.activeServer
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(DownloadsUiState(loading = false))
                    } else {
                        combine(controls.downloads(s), segment) { items, seg -> items.toUiState(s, seg) }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadsUiState())

        fun select(index: Int) {
            segment.value = index
        }
    }
