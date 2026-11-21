package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.SearchFileDto
import com.nl.kaviyy.core.network.SearchResultGroupDto
import com.nl.kaviyy.core.network.SearchSeriesDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
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
class SearchRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                KaviyyDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val api =
        object : FakeKavitaApi() {
            override suspend fun search(
                query: String,
                includeChapterAndFiles: Boolean,
            ) = SearchResultGroupDto(
                series = listOf(SearchSeriesDto(7, "Ember Road", localizedName = " ", libraryName = "Manga")),
                files = listOf(SearchFileDto(3, "/data/manga/Ember/v01.cbz"), SearchFileDto(4, "C:\\m\\v02.cbz")),
            )

            override suspend fun personRoles(personId: Int) = listOf(99, 4)
        }
    private val repo = KavitaSearchRepository({ api }, db.recentSearchDao())

    @After
    fun tearDown() = db.close()

    @Test
    fun resultsMapAndFilesShowOnlyTheirName() =
        runTest {
            val r = (repo.search(server, "ember") as AppResult.Success).value
            assertEquals("Ember Road", r.series.single().name)
            assertEquals(listOf("v01.cbz", "v02.cbz"), r.files.map { it.name })
            assertEquals(AppResult.Success(FilterField.Penciller), repo.personField(server, 1))
        }

    @Test
    fun recentsAreNewestFirstDedupedAndCapped() =
        runTest {
            (1..10).forEach { repo.addRecent(server, "q$it") }
            repo.addRecent(server, " q3 ")
            repo.addRecent(server, "  ")
            val list = repo.recents(server).first()
            assertEquals(8, list.size)
            assertEquals("q3", list.first())
            repo.clearRecents(server)
            assertEquals(emptyList(), repo.recents(server).first())
        }
}
