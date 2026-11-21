package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ReadingListDto
import com.nl.kaviyy.core.network.ReadingListItemDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ReadingListRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private var remote = (1..7).map { ReadingListDto(it, "List $it", itemCount = 3) }
    private val fetched = mutableListOf<Int>()

    private val api =
        object : FakeKavitaApi() {
            override suspend fun readingLists(
                pageNumber: Int,
                pageSize: Int,
                includePromoted: Boolean,
                sortByLastModified: Boolean,
            ) = remote

            override suspend fun readingListItems(readingListId: Int): List<ReadingListItemDto> {
                fetched += readingListId
                return listOf(
                    ReadingListItemDto(11 + readingListId * 10, 0, 100, 5, pagesRead = 20, pagesTotal = 20),
                    ReadingListItemDto(12 + readingListId * 10, 1, 101, 5, pagesRead = 3, pagesTotal = 20),
                    ReadingListItemDto(13 + readingListId * 10, 2, 102, 6, seriesName = "Other", pagesTotal = 0),
                )
            }
        }
    private val repo = OfflineFirstReadingListRepository({ api }, db.readingListDao())

    @After
    fun tearDown() = db.close()

    @Test
    fun refreshCachesListsInOrderWithProgressForThePreviewOnes() =
        runTest {
            assertEquals(AppResult.Success(Unit), repo.refreshLists(server))
            val rows = repo.lists(server).first()
            assertEquals((1..7).map { "List $it" }, rows.map { it.list.title })
            assertEquals((1..READING_LISTS_PREVIEW).toList(), fetched) // items only for the shown lists
            assertEquals(1, rows.first().readCount) // only the fully read item counts
            assertEquals(0, rows.last().readCount)
            remote = remote.take(2)
            repo.refreshLists(server)
            assertEquals(2, repo.lists(server).first().size) // deleted on the server: gone here
        }

    @Test
    fun itemsAreOrderedAndNextIsTheFirstUnfinished() =
        runTest {
            repo.refreshLists(server)
            val items = repo.items(server, 1).first()
            assertEquals(listOf(100, 101, 102), items.map { it.chapterId })
            assertEquals(101, items.next()?.chapterId)
            assertEquals(
                100,
                listOf(ReadingListItemEntity(1, 1, 1, 0, 100, 0, 5, pagesRead = 5, pagesTotal = 5)).next()?.chapterId,
            ) // all read: start again
        }
}
