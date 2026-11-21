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
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Queues, pauses, resumes, retries and cancels chapter downloads (one unique WorkManager job per chapter). */
@Singleton
class Downloads
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val store: DownloadStore,
        private val downloader: ChapterDownloader,
        private val dao: DownloadDao,
        private val settings: AppSettingsStore,
    ) : DownloadQueue {
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
                    reschedule(server.id.value, c.chapterId)
                }
            }
        }

        suspend fun pause(
            serverId: Long,
            chapterId: Int,
        ) {
            store.apply(serverId, chapterId, DownloadEvent.Pause)
            work.cancelUniqueWork(name(serverId, chapterId))
        }

        suspend fun resume(
            serverId: Long,
            chapterId: Int,
        ) {
            store.apply(serverId, chapterId, DownloadEvent.Resume)?.let { reschedule(serverId, chapterId) }
        }

        suspend fun retry(
            serverId: Long,
            chapterId: Int,
        ) {
            store.apply(serverId, chapterId, DownloadEvent.Retry)?.let { reschedule(serverId, chapterId) }
        }

        /** Stops it and deletes its row and files (finished or partial). */
        suspend fun cancel(
            serverId: Long,
            chapterId: Int,
        ) {
            val row = store.get(serverId, chapterId) ?: return
            store.remove(serverId, chapterId)
            work.cancelUniqueWork(name(serverId, chapterId))
            downloader.delete(row)
        }

        private suspend fun reschedule(
            serverId: Long,
            chapterId: Int,
        ) = schedule(serverId, chapterId, settings.settings.first().downloadWifiOnly)

        // ponytail: constraints are fixed when a job is queued; changing Wi-Fi only later applies to new downloads.
        private fun schedule(
            serverId: Long,
            chapterId: Int,
            wifiOnly: Boolean,
        ) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
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
            work.enqueueUniqueWork(name(serverId, chapterId), ExistingWorkPolicy.KEEP, request)
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
