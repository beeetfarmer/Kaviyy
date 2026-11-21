package com.nl.kaviyy.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Starts, pauses, resumes, retries and cancels chapter downloads (one unique WorkManager job per chapter). */
@Singleton
class Downloads
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val store: DownloadStore,
        private val downloader: ChapterDownloader,
    ) {
        private val work get() = WorkManager.getInstance(context)

        fun schedule(
            serverId: Long,
            chapterId: Int,
        ) {
            val request =
                OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
                    .setInputData(
                        workDataOf(
                            ChapterDownloadWorker.KEY_SERVER to serverId,
                            ChapterDownloadWorker.KEY_CHAPTER to chapterId,
                        ),
                    ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .addTag(TAG)
                    .build()
            work.enqueueUniqueWork(name(serverId, chapterId), ExistingWorkPolicy.KEEP, request)
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
            store.apply(serverId, chapterId, DownloadEvent.Resume)?.let { schedule(serverId, chapterId) }
        }

        suspend fun retry(
            serverId: Long,
            chapterId: Int,
        ) {
            store.apply(serverId, chapterId, DownloadEvent.Retry)?.let { schedule(serverId, chapterId) }
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

        /** Every download job, for sign-out / Delete Local Data. */
        fun cancelAll() {
            work.cancelAllWorkByTag(TAG)
        }

        private fun name(
            serverId: Long,
            chapterId: Int,
        ) = "download-$serverId-$chapterId"

        private companion object {
            const val TAG = "chapter-download"
            const val BACKOFF_SECONDS = 30L
        }
    }
