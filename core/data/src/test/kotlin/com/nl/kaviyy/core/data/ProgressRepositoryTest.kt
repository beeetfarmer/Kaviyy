package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ProgressDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val chapter = ReaderChapter(10, 7, 5, 1, "S", "1", "1", null, false, pages = 20, format = 0)
    private val sent = mutableListOf<ProgressDto>()
    private var offline = false
    private var remotePage = 0

    private val api =
        object : FakeKavitaApi() {
            override suspend fun progress(chapterId: Int): ProgressDto {
                if (offline) throw IOException("offline")
                return ProgressDto(5, chapterId, remotePage, 7, 1)
            }

            override suspend fun saveProgress(body: ProgressDto) {
                if (offline) throw IOException("offline")
                sent += body
            }
        }
    private val outbox = db.outboxDao()
    private val repo = OfflineFirstProgressRepository({ api }, db.progressDao(), outbox)

    @After
    fun tearDown() = db.close()

    private suspend fun seed() =
        db.seriesDao().replaceSeriesDetail(
            SeriesEntity(serverId, 7, 1, "S", "s", format = 0, pages = 40, pagesRead = 0),
            listOf(VolumeEntity(serverId, 5, 7, "1", 1f, 1f, pages = 40, pagesRead = 0)),
            listOf(
                ChapterEntity(serverId, 10, 5, 7, pages = 20, pagesRead = 0, format = 0),
                ChapterEntity(serverId, 11, 5, 7, pages = 20, pagesRead = 20, format = 0),
            ),
        )

    @Test
    fun recordIsLocalAndQueuedThenPushClearsTheQueue() =
        runTest {
            seed()
            repo.record(server, chapter, 4)
            repo.record(server, chapter, 6)
            assertEquals(listOf("10"), outbox.pending(serverId).map { it.entityKey }) // coalesced
            assertEquals(
                26,
                db
                    .seriesDao()
                    .observeSeries(serverId, 7)
                    .first()
                    ?.pagesRead,
            )
            assertEquals(AppResult.Success(Unit), repo.push(server, 10))
            assertEquals(listOf(6), sent.map { it.pageNum })
            assertTrue(
                sent
                    .single()
                    .lastModifiedUtc
                    .orEmpty()
                    .endsWith("Z"),
            )
            assertTrue(outbox.pending(serverId).isEmpty())
        }

    @Test
    fun lastPageMarksTheChapterRead() =
        runTest {
            seed()
            repo.record(server, chapter, 19)
            assertEquals(20, db.progressDao().chapterPagesRead(serverId, 10))
            repo.push(server, 10)
            assertEquals(20, sent.single().pageNum)
        }

    @Test
    fun resumePrefersQueuedLocalThenServerThenCache() =
        runTest {
            seed()
            remotePage = 12
            assertEquals(12, repo.resume(server, chapter)) // nothing local: server
            offline = true
            repo.record(server, chapter, 3) // queued
            offline = false
            assertEquals(3, repo.resume(server, chapter)) // unpushed local wins over the server
            repo.push(server, 10)
            assertEquals(12, repo.resume(server, chapter)) // pushed: server again (another device may have moved on)
            offline = true
            val read = chapter.copy(id = 11)
            assertEquals(0, repo.resume(server, read)) // offline, cached pagesRead = finished: start over
        }
}
