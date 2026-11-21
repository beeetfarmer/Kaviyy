package com.nl.kaviyy.core.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nl.kaviyy.core.data.AuthSession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Downloads one chapter (unique work per chapter, see [Downloads]) as a foreground `dataSync` job. */
class ChapterDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun store(): DownloadStore

        fun downloader(): ChapterDownloader

        fun session(): AuthSession

        fun notifications(): DownloadNotifications
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val serverId = inputData.getLong(KEY_SERVER, -1)
        val chapterId = inputData.getInt(KEY_CHAPTER, -1)
        // Signed out of this server: nothing to authenticate with, the row stays queued for when they're back.
        val server =
            deps
                .session()
                .servers
                .first()
                .firstOrNull { it.id.value == serverId } ?: return Result.failure()
        val notifications = deps.notifications()
        return try {
            val result =
                runDownload(deps.store(), deps.downloader(), server, chapterId) { row ->
                    // Android 12+ may refuse a foreground start from the background; the download still runs.
                    runCatching { setForeground(foregroundInfo(notifications, row)) }
                }
            when (result) {
                RunResult.Success -> Result.success()
                RunResult.Retry -> Result.retry()
                RunResult.Failure -> Result.failure()
            }
        } catch (e: CancellationException) {
            // Stopped by the system (constraints lost) or by pause/cancel: back to the queue unless the user acted.
            withContext(NonCancellable) { deps.store().apply(serverId, chapterId, DownloadEvent.Interrupted) }
            throw e
        }
    }

    private fun foregroundInfo(
        notifications: DownloadNotifications,
        row: com.nl.kaviyy.core.database.DownloadEntity,
    ): ForegroundInfo {
        val (id, notification) = notifications.progress(row)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val KEY_SERVER = "serverId"
        const val KEY_CHAPTER = "chapterId"
    }
}
