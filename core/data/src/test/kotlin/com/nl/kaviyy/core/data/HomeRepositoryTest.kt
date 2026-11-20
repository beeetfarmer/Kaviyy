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
import com.nl.kaviyy.core.network.DashboardStreamDto
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.GroupedSeriesDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    private var layout =
        listOf(
            DashboardStreamDto(10, "On Deck", 0, StreamType.ON_DECK),
            DashboardStreamDto(11, "Recently Updated", 1, StreamType.RECENTLY_UPDATED),
            DashboardStreamDto(12, "Newly Added", 2, StreamType.NEWLY_ADDED),
        )
    private var sentFilter: JsonObject? = null

    private val api =
        object : FakeKavitaApi() {
            override suspend fun dashboard(visibleOnly: Boolean) = if (offline) throw IOException("offline") else layout

            override suspend fun decodeFilter(body: DecodeFilterDto) =
                buildJsonObject {
                    put("name", body.encodedFilter)
                    put("entityType", if (body.encodedFilter == "people") 1 else 0)
                }

            override suspend fun seriesByFilter(
                filter: JsonObject,
                pageNumber: Int,
                pageSize: Int,
            ): List<SeriesDto> {
                sentFilter = filter
                return listOf(SeriesDto(7, name = "Filtered"))
            }

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
    fun followsServerLayoutAndDecodesSmartFilters() =
        runTest {
            layout =
                listOf(
                    DashboardStreamDto(20, "Favourites", 0, StreamType.SMART_FILTER, smartFilterEncoded = "fav"),
                    DashboardStreamDto(21, "People", 1, StreamType.SMART_FILTER, smartFilterEncoded = "people"),
                    DashboardStreamDto(22, "More in Genre", 2, streamType = 5),
                    DashboardStreamDto(10, "On Deck", 3, StreamType.ON_DECK),
                    DashboardStreamDto(12, "Hidden", 4, StreamType.NEWLY_ADDED, visible = false),
                )
            assertEquals(AppResult.Success(Unit), repo.refresh(server))
            val home = repo.home(server).first()
            assertEquals(listOf(20, 21, 10), home.rails.map { it.streamId })
            assertEquals(listOf("Filtered"), home.rails[0].items.map { it.title })
            assertEquals("fav", (sentFilter?.get("name") as JsonPrimitive).content) // decoded filter sent unchanged
            assertTrue(home.rails[1].items.isEmpty()) // non-series smart filter skipped
        }

    @Test
    fun offlineFirstRunFallsBackToBuiltInRails() =
        runTest {
            offline = true
            repo.refresh(server)
            val types = listOf(StreamType.ON_DECK, StreamType.RECENTLY_UPDATED, StreamType.NEWLY_ADDED)
            assertEquals(
                types,
                repo
                    .home(server)
                    .first()
                    .rails
                    .map { it.type },
            )
            offline = false
            repo.refresh(server)
            assertEquals(
                listOf(10, 11, 12),
                repo
                    .home(server)
                    .first()
                    .rails
                    .map { it.streamId },
            ) // built-ins replaced
        }

    @Test
    fun downloadedSeriesNewestFirstOnlyWhenCompleted() =
        runTest {
            repo.refresh(server) // caches series 1, 2, 3, 4
            val raw = db.openHelper.writableDatabase
            val rows = listOf(Triple(1, "COMPLETED", 10), Triple(4, "COMPLETED", 20), Triple(2, "RUNNING", 30))
            rows.forEach { (s, st, t) ->
                raw.execSQL(
                    "INSERT INTO downloads (serverId, chapterId, seriesId, volumeId, state, pageImages, pagesDone, " +
                        "pagesTotal, bytesDone, bytesTotal, serverLastModified, updatedAt) " +
                        "VALUES ($serverId, ${100 + s}, $s, 0, '$st', 1, 0, 0, 0, 0, 0, $t)",
                )
            }
            val home = repo.home(server).first()
            assertEquals(listOf(4, 1), home.downloaded.map { it.seriesId })
            assertEquals(setOf(4, 1), home.downloadedIds)
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
