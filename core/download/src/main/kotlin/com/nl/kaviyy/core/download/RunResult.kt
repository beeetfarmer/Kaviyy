package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.server.Server

/** What the worker tells WorkManager. */
enum class RunResult { Success, Retry, Failure }

/**
 * One attempt at a queued chapter, without WorkManager (so it can be tested). A row left RUNNING by a killed process
 * is picked up again. Paused or removed rows stop the run; a removed row's files are deleted here because the worker
 * may still have been writing when the user cancelled.
 */
suspend fun runDownload(
    store: DownloadStore,
    downloader: ChapterDownloader,
    server: Server,
    chapterId: Int,
    onProgress: suspend (DownloadEntity) -> Unit = {},
): RunResult {
    val serverId = server.id.value
    store.apply(serverId, chapterId, DownloadEvent.Interrupted)
    val row = store.apply(serverId, chapterId, DownloadEvent.Start) ?: return RunResult.Success
    val outcome =
        downloader.download(server, row) { p ->
            store.apply(serverId, chapterId, p)?.also { onProgress(it) } != null
        }
    return when (outcome) {
        DownloadOutcome.Done -> {
            store.apply(serverId, chapterId, DownloadEvent.Complete(row.serverLastModified))
            RunResult.Success
        }

        DownloadOutcome.Stopped -> {
            if (store.get(serverId, chapterId) == null) downloader.delete(row)
            RunResult.Success
        }

        is DownloadOutcome.Failed -> {
            if (outcome.error == AppError.Network) {
                store.apply(serverId, chapterId, DownloadEvent.Interrupted) // kept in the queue, progress kept
                RunResult.Retry
            } else {
                store.apply(serverId, chapterId, DownloadEvent.Fail(outcome.error.toString()))
                RunResult.Failure
            }
        }
    }
}
