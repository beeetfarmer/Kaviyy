package com.nl.kaviyy.core.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.data.canDownload
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.apiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Queues the next unread chapters of On Deck series, and keeps the periodic job in step with the settings. */
@Singleton
class AutoDownloader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val session: AuthSession,
        private val apis: ServerApis,
        private val catalog: CatalogRepository,
        private val queue: DownloadQueue,
        private val settings: AppSettingsStore,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        /** Call once from the Application: (re)schedules or cancels the periodic job when the settings change. */
        fun start() {
            settings.settings
                .map { Policy(it) }
                .distinctUntilChanged()
                .onEach(::schedule)
                .launchIn(CoroutineScope(SupervisorJob() + io))
        }

        /** One pass over every signed-in server the user may download from. */
        suspend fun runAll() {
            val count = settings.settings.first().autoDownloadCount
            if (count <= 0) return
            session.servers
                .first()
                .filter { it.canDownload }
                .forEach { run(it, count) }
        }

        suspend fun run(
            server: Server,
            count: Int,
        ) {
            val api = apis.api(server)
            val deck = (apiCall { api.onDeck(1, ON_DECK_SERIES) } as? AppResult.Success)?.value.orEmpty()
            for (series in deck) {
                val picks = picks(server, api, series.id, count)
                if (queue.enqueue(server, picks.map { it.toDownload() }) == EnqueueResult.OverLimit) return
            }
        }

        /** Empty when the series can't be refreshed or has no continue point. */
        private suspend fun picks(
            server: Server,
            api: KavitaApi,
            seriesId: Int,
            count: Int,
        ): List<ChapterEntity> {
            val refreshed = catalog.refreshSeries(server, seriesId) is AppResult.Success
            val point = if (refreshed) apiCall { api.continuePoint(seriesId) } else null
            val start = (point as? AppResult.Success)?.value?.id ?: return emptyList()
            val chapters =
                catalog
                    .seriesDetail(server, seriesId)
                    .first()
                    .chapters
                    .associateBy { it.id }
            return nextUnread(start, chapters, count) { c ->
                (apiCall { api.nextChapter(c.seriesId, c.volumeId, c.id) } as? AppResult.Success)?.value ?: -1
            }
        }

        private fun schedule(policy: Policy) {
            // Not initialised in host-side tests of the Application; the app's startup provider always initialises it.
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (policy.count <= 0) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(if (policy.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresCharging(policy.charging)
                    .setRequiresStorageNotLow(true)
                    .build()
            val request =
                PeriodicWorkRequestBuilder<AutoDownloadWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private data class Policy(
            val count: Int,
            val wifiOnly: Boolean,
            val charging: Boolean,
        ) {
            constructor(s: AppSettings) : this(s.autoDownloadCount, s.downloadWifiOnly, s.autoDownloadChargingOnly)
        }

        internal companion object {
            const val WORK_NAME = "auto-download"
            private const val PERIOD_HOURS = 6L
            private const val ON_DECK_SERIES = 10
        }
    }

/**
 * From [start] (the server's continue point) follow the server's next-chapter order and keep up to [count] chapters
 * that are cached and not fully read. Stops at the end of the series or an uncached chapter.
 */
internal suspend fun nextUnread(
    start: Int,
    chapters: Map<Int, ChapterEntity>,
    count: Int,
    next: suspend (ChapterEntity) -> Int,
): List<ChapterEntity> {
    val picks = mutableListOf<ChapterEntity>()
    var chapter = chapters[start]
    var steps = 0
    while (chapter != null && picks.size < count && steps++ < chapters.size) {
        if (chapter.pages == 0 || chapter.pagesRead < chapter.pages) picks += chapter
        chapter = chapters[next(chapter)]
    }
    return picks
}
