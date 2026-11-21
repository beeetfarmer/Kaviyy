package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.DownloadEntity

/** Stored as the enum name in `downloads.state`. */
enum class DownloadState { QUEUED, RUNNING, PAUSED, FAILED, COMPLETED }

/** What can happen to a download. Cancelling deletes the row (and files), so it isn't a state. */
sealed interface DownloadEvent {
    /** The worker picked it up. */
    data object Start : DownloadEvent

    /** Progress so far; totals may become known on the first one. */
    data class Progress(
        val pagesDone: Int,
        val pagesTotal: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
    ) : DownloadEvent

    /** The user paused it. */
    data object Pause : DownloadEvent

    /** The user resumed a paused download. */
    data object Resume : DownloadEvent

    /** The worker was stopped by the system (constraints lost); it goes back in the queue, progress kept. */
    data object Interrupted : DownloadEvent

    data class Fail(
        val error: String,
    ) : DownloadEvent

    /** Retry a failed download. */
    data object Retry : DownloadEvent

    /** Files verified and moved into place. [serverLastModified] marks the version downloaded. */
    data class Complete(
        val serverLastModified: Long,
    ) : DownloadEvent

    /** The server's copy changed (now at [serverLastModified]): download it again. */
    data class Update(
        val serverLastModified: Long,
    ) : DownloadEvent
}

val DownloadEntity.downloadState: DownloadState
    get() = DownloadState.entries.firstOrNull { it.name == state } ?: DownloadState.FAILED

/**
 * The state machine: QUEUED → RUNNING → (PAUSED | FAILED | COMPLETED), PAUSED → QUEUED (resume), FAILED → QUEUED
 * (retry), RUNNING → QUEUED (interrupted), COMPLETED → QUEUED (update). Returns null for a transition that isn't
 * allowed (callers ignore it, e.g. a late progress report after a pause).
 */
fun DownloadEntity.on(
    event: DownloadEvent,
    now: Long,
): DownloadEntity? {
    val next = next(downloadState, event) ?: return null
    val base = copy(state = next.name, updatedAt = now, error = if (next == DownloadState.FAILED) error else null)
    return when (event) {
        is DownloadEvent.Progress -> {
            base.copy(
                pagesDone = event.pagesDone,
                pagesTotal = event.pagesTotal,
                bytesDone = event.bytesDone,
                bytesTotal = event.bytesTotal,
            )
        }

        is DownloadEvent.Fail -> {
            base.copy(error = event.error)
        }

        is DownloadEvent.Complete -> {
            base.copy(
                serverLastModified = event.serverLastModified,
                pagesDone = pagesTotal,
                bytesDone = maxOf(bytesDone, bytesTotal),
            )
        }

        is DownloadEvent.Update -> {
            base.copy(pagesDone = 0, bytesDone = 0, serverLastModified = event.serverLastModified)
        }

        else -> {
            base
        }
    }
}

/** (from-states, to-state) per event. */
private fun next(
    from: DownloadState,
    event: DownloadEvent,
): DownloadState? {
    val (allowed, to) =
        when (event) {
            DownloadEvent.Start -> setOf(DownloadState.QUEUED) to DownloadState.RUNNING
            is DownloadEvent.Progress -> setOf(DownloadState.RUNNING) to DownloadState.RUNNING
            DownloadEvent.Pause -> setOf(DownloadState.QUEUED, DownloadState.RUNNING) to DownloadState.PAUSED
            DownloadEvent.Resume -> setOf(DownloadState.PAUSED) to DownloadState.QUEUED
            DownloadEvent.Interrupted -> setOf(DownloadState.RUNNING) to DownloadState.QUEUED
            is DownloadEvent.Fail -> setOf(DownloadState.RUNNING) to DownloadState.FAILED
            DownloadEvent.Retry -> setOf(DownloadState.FAILED) to DownloadState.QUEUED
            is DownloadEvent.Complete -> setOf(DownloadState.RUNNING) to DownloadState.COMPLETED
            is DownloadEvent.Update -> setOf(DownloadState.COMPLETED) to DownloadState.QUEUED
        }
    return to.takeIf { from in allowed }
}
