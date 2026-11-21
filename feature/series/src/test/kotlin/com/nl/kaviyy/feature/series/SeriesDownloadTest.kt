package com.nl.kaviyy.feature.series

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.download.DownloadState
import org.junit.Test
import kotlin.test.assertEquals

class SeriesDownloadTest {
    private val chapters = (1..2).map { ChapterEntity(1, it, 1, 1, pages = 10, pagesRead = 0, format = 1) }

    private fun row(
        id: Int,
        state: DownloadState,
        done: Int = 0,
    ) = DownloadEntity(1, id, 1, 1, state.name, true, pagesDone = done, pagesTotal = 10)

    @Test
    fun statesFollowTheRows() {
        assertEquals(SeriesDownload.Idle, seriesDownload(chapters, emptyList()))
        assertEquals(SeriesDownload.Idle, seriesDownload(chapters, listOf(row(1, DownloadState.COMPLETED)))) // partial
        assertEquals(
            SeriesDownload.Done,
            seriesDownload(chapters, chapters.map { row(it.id, DownloadState.COMPLETED) }),
        )
        assertEquals(
            SeriesDownload.Active(0.75f),
            seriesDownload(chapters, listOf(row(1, DownloadState.COMPLETED), row(2, DownloadState.RUNNING, done = 5))),
        )
        assertEquals(
            SeriesDownload.Error,
            seriesDownload(chapters, listOf(row(1, DownloadState.COMPLETED), row(2, DownloadState.FAILED))),
        )
        // anything still moving wins over a failure
        assertEquals(
            SeriesDownload.Active(0f),
            seriesDownload(chapters, listOf(row(1, DownloadState.FAILED), row(2, DownloadState.PAUSED))),
        )
    }

    @Test
    fun chapterRowLabels() {
        assertEquals(R.string.series_downloaded, downloadLabel(DownloadState.COMPLETED))
        assertEquals(R.string.series_downloading, downloadLabel(DownloadState.RUNNING))
        assertEquals(null, downloadLabel(DownloadState.FAILED))
        assertEquals(null, downloadLabel(null))
    }
}
