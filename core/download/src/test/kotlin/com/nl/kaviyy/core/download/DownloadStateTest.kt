package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.download.DownloadEvent.Complete
import com.nl.kaviyy.core.download.DownloadEvent.Fail
import com.nl.kaviyy.core.download.DownloadEvent.Interrupted
import com.nl.kaviyy.core.download.DownloadEvent.Pause
import com.nl.kaviyy.core.download.DownloadEvent.Progress
import com.nl.kaviyy.core.download.DownloadEvent.Resume
import com.nl.kaviyy.core.download.DownloadEvent.Retry
import com.nl.kaviyy.core.download.DownloadEvent.Start
import com.nl.kaviyy.core.download.DownloadEvent.Update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadStateTest {
    private fun row(state: DownloadState) = DownloadEntity(1, 10, 2, 3, state.name, pageImages = true)

    private val allEvents =
        listOf(Start, Progress(1, 2, 3, 4), Pause, Resume, Interrupted, Fail("x"), Retry, Complete(5), Update)

    @Test
    fun onlyTheDocumentedTransitionsAreAllowed() {
        val allowed =
            mapOf(
                DownloadState.QUEUED to setOf(Start, Pause),
                DownloadState.RUNNING to setOf(Progress(1, 2, 3, 4), Pause, Interrupted, Fail("x"), Complete(5)),
                DownloadState.PAUSED to setOf(Resume),
                DownloadState.FAILED to setOf(Retry),
                DownloadState.COMPLETED to setOf(Update),
            )
        for ((state, ok) in allowed) {
            for (event in allEvents) {
                val next = row(state).on(event, 0)
                assertEquals(event in ok, next != null, "$state + $event")
            }
        }
    }

    @Test
    fun aHappyPathKeepsProgressAndMarksTheVersion() {
        val done =
            row(DownloadState.QUEUED)
                .on(Start, 1)
                ?.on(Progress(5, 20, 500, 0), 2)
                ?.on(Interrupted, 3)
                ?.on(Start, 4)
                ?.on(Progress(20, 20, 2000, 2000), 5)
                ?.on(Complete(99), 6)
        assertEquals(DownloadState.COMPLETED, done?.downloadState)
        assertEquals(20 to 99L, done?.pagesDone to done?.serverLastModified)
        assertEquals(6, done?.updatedAt)
    }

    @Test
    fun failureKeepsTheErrorUntilRetried() {
        val failed = row(DownloadState.RUNNING).on(Fail("disk full"), 1)
        assertEquals("disk full", failed?.error)
        assertNull(failed?.on(Retry, 2)?.error)
    }

    @Test
    fun updateRestartsProgress() {
        val again = row(DownloadState.COMPLETED).copy(pagesDone = 20, bytesDone = 9).on(Update, 1)
        assertEquals(
            Triple(DownloadState.QUEUED, 0, 0L),
            Triple(again?.downloadState, again?.pagesDone, again?.bytesDone),
        )
    }

    @Test
    fun unknownStoredStateReadsAsFailed() {
        assertEquals(DownloadState.FAILED, row(DownloadState.QUEUED).copy(state = "BOGUS").downloadState)
    }
}
