package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ReadingListEntity
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.CblBookResultDto
import com.nl.kaviyy.core.network.CblFinalizeDto
import com.nl.kaviyy.core.network.CblImportSummaryDto
import com.nl.kaviyy.core.network.CblSavedFileDto
import com.nl.kaviyy.core.network.CblValidateDto
import com.nl.kaviyy.core.network.CreateReadingListDto
import com.nl.kaviyy.core.network.ReadingListDto
import com.nl.kaviyy.core.network.ReadingListItemDto
import com.nl.kaviyy.core.network.ReadingListPositionDto
import com.nl.kaviyy.core.network.ReadingListSeriesDto
import com.nl.kaviyy.core.network.UpdateReadingListDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MultipartBody
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
    private val calls = mutableListOf<String>()

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

            override suspend fun readingList(readingListId: Int) =
                ReadingListDto(readingListId, "Old", promoted = true, startingYear = 2006, tags = JsonPrimitive("kept"))

            override suspend fun createReadingList(body: CreateReadingListDto): ReadingListDto {
                calls += "create ${body.title}"
                return ReadingListDto(99, body.title)
            }

            override suspend fun updateReadingList(body: UpdateReadingListDto) {
                val b = body
                calls += "update ${b.readingListId} ${b.title} ${b.summary} ${b.promoted} ${b.startingYear} ${b.tags}"
            }

            override suspend fun deleteReadingList(readingListId: Int) {
                calls += "delete $readingListId"
            }

            override suspend fun moveReadingListItem(body: ReadingListPositionDto) {
                calls += "move ${body.readingListItemId} ${body.fromPosition}->${body.toPosition}"
            }

            override suspend fun deleteReadingListItem(body: ReadingListPositionDto) {
                calls += "remove ${body.readingListId}/${body.readingListItemId}"
            }

            override suspend fun addSeriesToReadingList(body: ReadingListSeriesDto) {
                calls += "add ${body.seriesId} to ${body.readingListId}"
            }

            override suspend fun readingListsWithSeries(seriesId: Int) = remote.take(2)

            override suspend fun saveCblFile(cblFile: MultipartBody.Part): CblSavedFileDto {
                calls += "save ${cblFile.headers?.get("Content-Disposition")}"
                return CblSavedFileDto("Civil War", "civil-war.cbl", provider = 1, sha = "abc")
            }

            override suspend fun validateCbl(body: CblValidateDto): CblImportSummaryDto {
                calls += "validate ${body.fileName}"
                return CblImportSummaryDto(
                    cblName = "Civil War",
                    results =
                        listOf(
                            CblBookResultDto(1, "Civil War", number = "1", reason = 8),
                            CblBookResultDto(2, "Iron Man", number = "13", reason = 0),
                            CblBookResultDto(3, "Civil War", number = "2", reason = 8),
                        ),
                    success = 1,
                )
            }

            override suspend fun finalizeCbl(body: CblFinalizeDto): CblImportSummaryDto {
                calls += "finalize ${body.fileName} ${body.provider} ${body.sha}"
                return CblImportSummaryDto(readingListId = 4)
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
    fun changesGoToKavitaPassingUneditedFieldsBack() =
        runTest {
            repo.refreshLists(server)
            val item = repo.items(server, 1).first()[2]
            assertEquals(AppResult.Success(setOf(1, 2)), repo.listsWith(server, 5))
            assertEquals(AppResult.Success(99), repo.create(server, " New "))
            repo.update(server, 1, "Renamed", " ")
            repo.move(server, item, 0)
            repo.remove(server, item)
            repo.addSeries(server, 1, 5)
            repo.delete(server, 1)
            assertEquals(
                listOf(
                    "create New",
                    "update 1 Renamed null true 2006 \"kept\"",
                    "move 23 2->0",
                    "remove 1/23",
                    "add 5 to 1",
                    "delete 1",
                ),
                calls,
            )
            assertEquals(null, repo.list(server, 1).first())
        }

    @Test
    fun cblIsPreviewedThenImportedWithTheSavedFile() =
        runTest {
            val preview = (repo.previewCbl(server, "civil-war.cbl", "<cbl/>".toByteArray()) as AppResult.Success).value
            assertEquals("Civil War" to (2 to 3), preview.name to (preview.found to preview.total))
            assertEquals(listOf("Iron Man #13"), preview.missing)
            assertEquals(true, preview.importable)
            assertEquals(AppResult.Success(4), repo.importCbl(server, preview))
            assertEquals(
                listOf(
                    "save form-data; name=\"cblFile\"; filename=\"civil-war.cbl\"",
                    "validate civil-war.cbl",
                    "finalize civil-war.cbl 1 abc",
                ),
                calls,
            )
        }

    @Test
    fun onlyTheOwnerEdits() {
        val me = server.copy(username = "Neo")
        assertEquals(true, ReadingListEntity(1, 1, "a", owner = "neo").editableBy(me))
        assertEquals(false, ReadingListEntity(1, 1, "a", owner = "admin").editableBy(me))
        assertEquals(false, ReadingListEntity(1, 1, "a").editableBy(me))
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
