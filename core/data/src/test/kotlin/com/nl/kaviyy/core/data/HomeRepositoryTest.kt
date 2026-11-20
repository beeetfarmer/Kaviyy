package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.GroupedSeriesDto
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
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class HomeRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val url = (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url
    private val server = Server(ServerId(serverId), "k", url)

    private var offline = false
    private val onDeck =
        listOf(SeriesDto(1, libraryId = 2, name = "Ember Road", pages = 100, pagesRead = 62), SeriesDto(2, name = "B"))

    private val api =
        object : FakeKavitaApi() {
            override suspend fun onDeck(
                pageNumber: Int,
                pageSize: Int,
                libraryId: Int,
            ): List<SeriesDto> = if (offline) throw IOException("offline") else onDeck

            override suspend fun continuePoint(seriesId: Int) =
                if (seriesId == 1) ChapterDto(49, volumeId = 5, range = "49", pages = 20) else throw IOException("x")

            override suspend fun recentlyUpdated(
                pageNumber: Int,
                pageSize: Int,
            ) = listOf(GroupedSeriesDto(1, "stale name", count = 9), GroupedSeriesDto(3, "Fresh", count = 3))

            override suspend fun recentlyAdded(
                filter: SeriesFilterV2Dto,
                pageNumber: Int,
                pageSize: Int,
            ) = listOf(SeriesDto(4, name = "New"))
        }

    private val repo = OfflineFirstHomeRepository(db.dashboardDao(), db.seriesDao(), { api })

    @After
    fun tearDown() = db.close()

    @Test
    fun refreshFillsRailsAndContinueReading() =
        runTest {
            assertTrue(repo.home(server).first().isEmpty)
            assertEquals(AppResult.Success(Unit), repo.refresh(server))
            val home = repo.home(server).first()
            val types = listOf(StreamType.ON_DECK, StreamType.RECENTLY_UPDATED, StreamType.NEWLY_ADDED)
            assertEquals(types, home.rails.map { it.type })
            val cont = checkNotNull(home.continueReading)
            assertEquals("Ember Road", cont.title)
            assertEquals(0.62f, cont.progress)
            assertEquals("49", cont.chapter?.range)
            assertEquals(1, cont.chapter?.seriesId)
            // A failed continue-point lookup just means no chapter for that card.
            assertEquals(null, home.rails[0].items[1].chapter)
            val updated = home.rails[1].items
            assertEquals(listOf("Ember Road", "Fresh"), updated.map { it.title }) // partial row kept the full one
            assertEquals(listOf(9, 3), updated.map { it.newChapters })
            assertEquals(listOf("New"), home.rails[2].items.map { it.title })
        }

    @Test
    fun failedStreamKeepsItsCacheAndReportsTheError() =
        runTest {
            repo.refresh(server)
            offline = true
            assertEquals(AppResult.Failure(AppError.Network), repo.refresh(server))
            val home = repo.home(server).first()
            assertEquals(2, home.rails[0].items.size)
            assertEquals(2, home.rails[1].items.size) // other streams still refreshed
        }
}
