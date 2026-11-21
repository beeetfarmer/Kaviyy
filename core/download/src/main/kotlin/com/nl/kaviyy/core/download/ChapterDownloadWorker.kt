package com.nl.kaviyy.core.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.database.DownloadDao
import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.server.Server
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

        fun settings(): AppSettingsStore

        fun dao(): DownloadDao

        fun catalog(): CatalogRepository

        fun series(): SeriesDao
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
            val limit =
                suspend {
                    deps
                        .settings()
                        .settings
                        .first()
                        .concurrentDownloads
                }
            val result =
                DownloadSlots.shared.withSlot(limit) {
                    runDownload(deps.store(), deps.downloader(), server, chapterId) { row ->
                        // Android 12+ may refuse a foreground start from the background; the download still runs.
                        runCatching { setForeground(foregroundInfo(notifications, row)) }
                    }
                }
            if (result == RunResult.Success) keepSeriesDetails(deps, server, chapterId)
            if (result != RunResult.Retry) {
                deps.dao().get(serverId, chapterId)?.let { row ->
                    notifications.finished(
                        row,
                        deps.dao().seriesName(serverId, row.seriesId),
                        deps.dao().chapter(serverId, chapterId),
                        result == RunResult.Success,
                    )
                }
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

    /** Summary, genres, tags etc. cached once, so the series page has them offline (whatever queued the download). */
    private suspend fun keepSeriesDetails(
        deps: Deps,
        server: Server,
        chapterId: Int,
    ) {
        val seriesId = deps.dao().get(server.id.value, chapterId)?.seriesId ?: return
        if (deps.series().observeMetadata(server.id.value, seriesId).first() == null) {
            deps.catalog().refreshSeries(server, seriesId) // best effort: a failure leaves what's cached
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
