package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.ReadDownload
import org.junit.Test
import kotlin.test.assertEquals

class ReadCleanupTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun read(
        chapterId: Int,
        daysAgo: Long?,
        seriesId: Int = 1,
        finishedDaysAgo: Long = 50,
    ) = ReadDownload(
        DownloadEntity(
            1,
            chapterId,
            seriesId,
            1,
            DownloadState.COMPLETED.name,
            true,
            updatedAt =
                now - finishedDaysAgo * day,
        ),
        daysAgo?.let { now - it * day },
    )

    private val rows = listOf(read(1, 10), read(2, 3), read(3, 0), read(4, null), read(5, 2, seriesId = 2))

    @Test
    fun neverDeletesNothing() = assertEquals(emptyList(), expired(rows, -1, 0, now))

    @Test
    fun immediatelyDeletesEveryReadChapter() = assertEquals(listOf(1, 2, 3, 4, 5), ids(expired(rows, 0, 0, now)))

    @Test
    fun aDelayUsesTheReadTimeElseWhenTheDownloadFinished() =
        assertEquals(listOf(1, 4), ids(expired(rows, 7, 0, now))) // 4 has no progress row; finished 50 days ago

    @Test
    fun keepsTheMostRecentlyReadPerSeries() =
        // series 1 by recency: 3 (0d), 2 (3d), 1 (10d), 4 (50d) → keep 3 and 2; series 2 keeps 5
        assertEquals(listOf(1, 4), ids(expired(rows, 0, 2, now)))

    private fun ids(list: List<DownloadEntity>) = list.map { it.chapterId }.sorted()
}
