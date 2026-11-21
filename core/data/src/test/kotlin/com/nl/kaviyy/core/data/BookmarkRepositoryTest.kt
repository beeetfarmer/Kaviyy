package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.BookmarkDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
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
class BookmarkRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val chapter = ReaderChapter(49, 7, 5, 1, "Ember Road", "5", "49", null, false, pages = 32, format = 1)
    private val calls = mutableListOf<String>()
    private var offline = false
    private var remote = listOf<BookmarkDto>()

    private val api =
        object : FakeKavitaApi() {
            private fun log(s: String) {
                if (offline) throw IOException("offline")
                calls += s
            }

            override suspend fun chapterBookmarks(chapterId: Int) = remote

            override suspend fun bookmark(body: BookmarkDto) =
                log("+${body.chapterId}:${body.page}/${body.seriesId}/${body.volumeId}")

            override suspend fun unbookmark(body: BookmarkDto) = log("-${body.chapterId}:${body.page}")
        }
    private val outbox = db.outboxDao()
    private val repo = OfflineFirstBookmarkRepository({ api }, db.bookmarkDao(), outbox)

    @After
    fun tearDown() = db.close()

    @Test
    fun setWritesLocallyThenPushes() =
        runTest {
            repo.set(server, chapter, 3, on = true)
            repo.set(server, chapter, 8, on = true)
            repo.set(server, chapter, 3, on = false)
            assertEquals(setOf(8), repo.pages(server, 49).first())
            assertEquals(listOf("+49:3/7/5", "+49:8/7/5", "-49:3"), calls)
            assertTrue(outbox.pending(serverId).isEmpty())
        }

    @Test
    fun offlineChangesQueueAndSurviveARefresh() =
        runTest {
            remote = listOf(BookmarkDto(11, 1, 5, 7, 49), BookmarkDto(12, 2, 5, 7, 49))
            repo.refresh(server, 49)
            assertEquals(setOf(1, 2), repo.pages(server, 49).first())
            offline = true
            repo.set(server, chapter, 2, on = false) // queued removal
            repo.set(server, chapter, 9, on = true) // queued add
            assertEquals(listOf("49:2", "49:9"), outbox.pending(serverId).map { it.entityKey })
            offline = false
            assertEquals(AppResult.Success(Unit), repo.refresh(server, 49))
            // Server still has 2 and lacks 9, but the queued changes win locally.
            assertEquals(setOf(1, 9), repo.pages(server, 49).first())
        }
}
