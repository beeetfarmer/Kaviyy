package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.database.ProgressEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ProgressPullerTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val asked = mutableListOf<Int>()
    private var offline = false

    /** chapterId → (page, time) the server reports. */
    private val remote = mutableMapOf<Int, Pair<Int, String?>>()

    private val api =
        object : FakeKavitaApi() {
            override suspend fun progress(chapterId: Int): ProgressDto {
                if (offline) throw IOException("offline")
                asked += chapterId
                val (page, time) = remote[chapterId] ?: (0 to null)
                return ProgressDto(5, chapterId, page, 7, 1, lastModifiedUtc = time)
            }
        }
    private val puller = ProgressPuller({ api }, db.progressDao(), db.outboxDao())

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.seriesDao().replaceSeriesDetail(
            SeriesEntity(serverId, 7, 1, "S", "s", format = 0, pages = 80, pagesRead = 0),
            listOf(VolumeEntity(serverId, 5, 7, "1", 1f, 1f, pages = 80, pagesRead = 0)),
            listOf(
                ChapterEntity(serverId, 10, 5, 7, pages = 20, pagesRead = 5, format = 0), // in progress
                ChapterEntity(serverId, 11, 5, 7, pages = 20, pagesRead = 0, format = 0), // downloaded, unread
                ChapterEntity(serverId, 12, 5, 7, pages = 20, pagesRead = 0, format = 0), // neither: skipped
                ChapterEntity(serverId, 13, 5, 7, pages = 20, pagesRead = 3, format = 0), // in progress, queued
            ),
        )
        db.downloadDao().upsert(DownloadEntity(serverId, 11, 7, 5, "COMPLETED", true))
        db.progressDao().save(ProgressEntity(serverId, 10, 5, 7, 1, 5, null, lastModifiedUtc = 10_000))
        db.outboxDao().enqueue(OutboxEntity(0, serverId, "progress", "13", "{}", "u", 0))
    }

    @Test
    fun newerServerPositionsLandInRoomAndOlderOnesDont() =
        runTest {
            seed()
            remote[10] = 9 to "1970-01-01T00:00:05Z" // older than our 10 s: ignored
            remote[11] = 20 to "1970-01-01T00:01:00Z" // finished on another device
            assertEquals(AppResult.Success(Unit), puller.pull(server))
            assertEquals(listOf(10, 11), asked.sorted()) // 12 isn't relevant, 13 has our change queued
            assertEquals(5, db.progressDao().chapterPagesRead(serverId, 10))
            assertEquals(20, db.progressDao().chapterPagesRead(serverId, 11))

            remote[10] = 9 to "1970-01-01T00:00:30Z" // now newer than ours
            puller.pull(server)
            assertEquals(9, db.progressDao().get(serverId, 10)?.pageNum)
        }

    @Test
    fun offlineStopsWithoutChangingAnything() =
        runTest {
            seed()
            offline = true
            assertEquals(AppResult.Failure(AppError.Network), puller.pull(server))
            assertEquals(5, db.progressDao().chapterPagesRead(serverId, 10))
        }
}
