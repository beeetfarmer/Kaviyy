package com.nl.kaviyy.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nl.kaviyy.core.data.canDownload
import com.nl.kaviyy.core.database.DownloadDao
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.OutdatedDownload
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Queues, pauses, resumes, retries and cancels chapter downloads (one unique WorkManager job per chapter). */
@Suppress("TooManyFunctions") // one facade for DownloadQueue + DownloadControls, each method a thin call
@Singleton
class Downloads
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val store: DownloadStore,
        private val downloader: ChapterDownloader,
        private val dao: DownloadDao,
        private val settings: AppSettingsStore,
    ) : DownloadQueue,
        DownloadControls {
        private val work get() = WorkManager.getInstance(context)

        override suspend fun enqueue(
            server: Server,
            chapters: List<DownloadRequest>,
        ): EnqueueResult {
            val prefs = settings.settings.first()
            val limit = prefs.storageLimitMb * BYTES_PER_MB
            return when {
                !server.canDownload -> {
                    EnqueueResult.NoPermission
                }

                limit > 0 && dao.totalBytes() + chapters.sumOf { it.bytes } > limit -> {
                    EnqueueResult.OverLimit
                }

                else -> {
                    val added = chapters.filter { store.enqueue(it.toEntity(server)) }
                    added.forEach { schedule(server.id.value, it.chapterId, prefs.downloadWifiOnly) }
                    if (added.isEmpty()) EnqueueResult.NothingNew else EnqueueResult.Queued
                }
            }
        }

        override fun downloads(server: Server): Flow<List<DownloadItem>> =
            combine(dao.observeWithSeries(server.id.value), dao.observeChapters(server.id.value)) { rows, chapters ->
                val byId = chapters.associateBy { it.id }
                rows.map { DownloadItem(it.download, it.seriesName, byId[it.download.chapterId], it.libraryType) }
            }

        override fun outdated(
            server: Server,
            seriesId: Int,
        ): Flow<List<OutdatedDownload>> = dao.observeOutdated(server.id.value, seriesId)

        override suspend fun update(
            server: Server,
            chapters: List<OutdatedDownload>,
        ) {
            chapters.forEach { c ->
                store.apply(server.id.value, c.chapterId, DownloadEvent.Update(c.lastModified))?.let {
                    schedule(server.id.value, c.chapterId)
                }
            }
        }

        override suspend fun pause(
            serverId: Long,
            chapterId: Int,
        ) {
            store.apply(serverId, chapterId, DownloadEvent.Pause)
            work.cancelUniqueWork(name(serverId, chapterId))
        }

        override suspend fun resume(
            serverId: Long,
            chapterId: Int,
        ) {
            store.apply(serverId, chapterId, DownloadEvent.Resume)?.let { schedule(serverId, chapterId) }
        }

        override suspend fun retry(
            serverId: Long,
            chapterId: Int,
        ) {
            store.apply(serverId, chapterId, DownloadEvent.Retry)?.let { schedule(serverId, chapterId) }
        }

        /** Stops it and deletes its row and files (finished or partial). */
        override suspend fun cancel(
            serverId: Long,
            chapterId: Int,
        ) {
            val row = store.get(serverId, chapterId) ?: return
            store.remove(serverId, chapterId)
            work.cancelUniqueWork(name(serverId, chapterId))
            downloader.delete(row)
        }

        override suspend fun setWifiOnly(on: Boolean) {
            settings.update { it.copy(downloadWifiOnly = on) }
            // Constraints are fixed per job, so swap the pending ones.
            // Running ones restart; pages already fetched are kept.
            (dao.inState(DownloadState.QUEUED.name) + dao.inState(DownloadState.RUNNING.name)).forEach {
                schedule(it.serverId, it.chapterId, on, ExistingWorkPolicy.REPLACE)
            }
        }

        override suspend fun removeRead(serverId: Long) {
            dao.observeRead().first().filter { it.download.serverId == serverId }.forEach {
                cancel(serverId, it.download.chapterId)
            }
        }

        override suspend fun clearAll() {
            work.cancelAllWorkByTag(TAG)
            dao.deleteAll()
            downloader.deleteAll()
        }

        private suspend fun schedule(
            serverId: Long,
            chapterId: Int,
            wifiOnly: Boolean? = null,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        ) {
            val unmetered = wifiOnly ?: settings.settings.first().downloadWifiOnly
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                    .setInputData(
                        workDataOf(
                            ChapterDownloadWorker.KEY_SERVER to serverId,
                            ChapterDownloadWorker.KEY_CHAPTER to chapterId,
                        ),
                    ).setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .addTag(TAG)
                    .build()
            work.enqueueUniqueWork(name(serverId, chapterId), policy, request)
        }

        internal companion object {
            const val TAG = "chapter-download"
            private const val BACKOFF_SECONDS = 30L
            private const val BYTES_PER_MB = 1024L * 1024

            fun name(
                serverId: Long,
                chapterId: Int,
            ) = "download-$serverId-$chapterId"
        }
    }

private fun DownloadRequest.toEntity(server: Server) =
    DownloadEntity(
        server.id.value,
        chapterId,
        seriesId,
        volumeId,
        DownloadState.QUEUED.name,
        pageImages,
        bytesTotal = bytes,
        serverLastModified = lastModified,
    )
