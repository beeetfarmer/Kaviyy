package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.CollectionAddDto
import com.nl.kaviyy.core.network.CollectionDto
import com.nl.kaviyy.core.network.CollectionRemoveDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.testing.FakeKavitaApi
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

            override suspend fun collections(sortByLastModified: Boolean) = collectionsRemote

            override suspend fun collectionsWithSeries(
                seriesId: Int,
                ownedOnly: Boolean,
            ) = collectionsRemote.take(1)

            override suspend fun addToCollection(body: CollectionAddDto) {
                calls += "add ${body.collectionTagId} '${body.collectionTagTitle}' ${body.seriesIds}"
            }

            override suspend fun removeFromCollection(body: CollectionRemoveDto) {
                calls += "remove ${body.tag.id} ${body.seriesIdsToRemove}"
            }

            override suspend fun updateCollection(body: CollectionDto): CollectionDto {
                calls += "update ${body.id} '${body.title}' ${body.summary}"
                return body
            }

            override suspend fun deleteCollection(tagId: Int) {
                calls += "delete $tagId"
            }
        }
    private var collectionsRemote =
        listOf(
            CollectionDto(7, "Comfort reads", itemCount = 12, owner = "neo"),
            CollectionDto(3, "MAL: Isekai", itemCount = 40, source = 2, promoted = true),
        )
    private val repo = OfflineFirstListsRepository({ api }, db.seriesDao(), db.collectionDao())

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
    fun collectionsAreCachedInServerOrderAndReplaced() =
        runTest {
            repo.refreshCollections(server)
            val first = repo.collections(server).first()
            assertEquals(listOf(7 to 0, 3 to 2), first.map { it.id to it.source })
            assertEquals("Comfort reads", repo.collection(server, 7).first()?.title)
            collectionsRemote = collectionsRemote.drop(1)
            repo.refreshCollections(server)
            assertEquals(listOf(3), repo.collections(server).first().map { it.id }) // deleted on the server
        }

    @Test
    fun collectionChangesGoToKavitaThenRefreshTheCache() =
        runTest {
            repo.refreshCollections(server)
            val mine = checkNotNull(repo.collection(server, 7).first())
            assertEquals(AppResult.Success(setOf(7)), repo.collectionsWith(server, 42))
            repo.setInCollection(server, mine, 42, inIt = true)
            repo.setInCollection(server, mine, 42, inIt = false)
            repo.createCollection(server, "  New one ", 42)
            repo.updateCollection(server, mine, "Comfort", "  ")
            repo.deleteCollection(server, 7)
            assertEquals(
                listOf(
                    "add 7 'Comfort reads' [42]",
                    "remove 7 [42]",
                    "add 0 'New one' [42]",
                    "update 7 'Comfort' null",
                    "delete 7",
                ),
                calls,
            )
            assertEquals(null, repo.collection(server, 7).first())
        }

    @Test
    fun onlyTheOwnerEditsAndNeverSyncedOnes() {
        val me = server.copy(username = "Neo")
        assertEquals(true, CollectionEntity(1, 1, "a", owner = "neo").editableBy(me))
        assertEquals(false, CollectionEntity(1, 1, "a", owner = "trinity").editableBy(me))
        assertEquals(false, CollectionEntity(1, 1, "a", owner = "neo", source = 2).editableBy(me))
    }

    @Test
    fun collectionQueryAddsTheCollectionRule() {
        val q = collectionQuery(7)
        assertEquals(null, q.libraryId)
        assertEquals(
            listOf("7"),
            q.filter.rules
                .single()
                .values
                .map { it.code },
        )
        assertEquals(
            FilterField.CollectionTags,
            q.filter.rules
                .single()
                .field,
        )
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
