package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ListsRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val calls = mutableListOf<String>()
    private val recent = Instant.now().minus(2, ChronoUnit.DAYS).toString()
    private val old = Instant.now().minus(30, ChronoUnit.DAYS).toString()

    private val api =
        object : FakeKavitaApi() {
            override suspend fun wantToRead(
                filter: SeriesFilterV2Dto,
                pageNumber: Int,
                pageSize: Int,
            ): List<SeriesDto> {
                calls += "wtr page $pageNumber rules ${filter.statements.size}"
                return listOf(
                    SeriesDto(1, name = "A", lastChapterAddedUtc = recent),
                    SeriesDto(2, name = "B", lastChapterAddedUtc = old),
                    SeriesDto(3, name = "C", lastChapterAddedUtc = recent),
                    SeriesDto(4, name = "D"),
                )
            }
        }
    private val repo = OfflineFirstListsRepository({ api }, db.seriesDao())

    @After
    fun tearDown() = db.close()

    @Test
    fun wantToReadSummaryComesFromTheCachedFirstPage() =
        runTest {
            assertEquals(ListSummary(0, false, emptyList(), 0), repo.wantToRead(server).first())
            assertEquals(AppResult.Success(Unit), repo.refreshWantToRead(server))
            assertEquals(listOf("wtr page 1 rules 0"), calls) // Kavita's own list, no hidden rules
            assertEquals(ListSummary(4, false, listOf(1, 2, 3), 2), repo.wantToRead(server).first())
        }

    @Test
    fun wantToReadQueriesAreSeparateFromTheLibraryAndKeepTheFilter() {
        val filter =
            SeriesFilter(rules = listOf(FilterRule(FilterField.Genres, Comparison.Contains, listOf(RuleValue("3")))))
        val q = wantToReadQuery(filter)
        assertEquals(true, q.wantToRead)
        assertEquals(null, q.libraryId)
        assertEquals(1, q.toFilter().statements.size)
        assert(q.key != LibraryQuery(null, filter).key)
    }
}
