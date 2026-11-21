package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.DownloadDao
import com.nl.kaviyy.core.database.ReadDownload
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Auto-delete after reading: re-checked whenever downloads, read state or the settings change. */
@Singleton
class ReadCleanup
    @Inject
    constructor(
        private val dao: DownloadDao,
        private val controls: DownloadControls,
        private val settings: AppSettingsStore,
        private val clock: Clock,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        // ponytail: day-based delays are re-checked on these changes and at app start, not by a timer.
        fun start() {
            combine(dao.observeRead(), settings.settings) { read, s ->
                expired(read, s.autoDeleteAfterDays, s.keepReadCount, clock.millis())
            }.onEach { rows -> rows.forEach { controls.cancel(it.serverId, it.chapterId) } }
                .launchIn(CoroutineScope(SupervisorJob() + io))
        }
    }

/**
 * Read downloads to delete: older than [days] (-1 = never), keeping the [keep] most recently read per series.
 * The read time is the local progress time, else when the download finished.
 */
internal fun expired(
    read: List<ReadDownload>,
    days: Int,
    keep: Int,
    now: Long,
): List<com.nl.kaviyy.core.database.DownloadEntity> {
    if (days < 0) return emptyList()
    val cutoff = now - days * DAY_MS
    return read
        .groupBy { it.download.serverId to it.download.seriesId }
        .values
        .flatMap { series ->
            series
                .sortedByDescending { it.readAt ?: it.download.updatedAt }
                .drop(keep)
                .filter { (it.readAt ?: it.download.updatedAt) <= cutoff }
                .map { it.download }
        }
}

private const val DAY_MS = 24L * 60 * 60 * 1000
