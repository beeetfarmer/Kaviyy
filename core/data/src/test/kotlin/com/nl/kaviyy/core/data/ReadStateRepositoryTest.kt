package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.SeriesMetadataEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.MarkReadDto
import com.nl.kaviyy.core.network.MarkVolumeReadDto
import com.nl.kaviyy.core.network.MarkVolumesReadDto
import com.nl.kaviyy.core.network.UpdateRatingDto
import com.nl.kaviyy.core.network.UpdateWantToReadDto
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

@RunWith(RobolectricTestRunner::class)
class ReadStateRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                KaviyyDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private val dao = db.seriesDao()
    private val outbox = db.outboxDao()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val calls = mutableListOf<String>()
    private var offline = false

    private val api =
        object : FakeKavitaApi() {
            private fun log(s: String) {
                if (offline) throw IOException("offline")
                calls += s
            }

            override suspend fun markSeriesRead(body: MarkReadDto) = log("series+ ${body.seriesId}")

            override suspend fun markSeriesUnread(body: MarkReadDto) = log("series- ${body.seriesId}")

            override suspend fun markVolumeRead(body: MarkVolumeReadDto) = log("volume+ ${body.volumeId}")

            override suspend fun markVolumeUnread(body: MarkVolumeReadDto) = log("volume- ${body.volumeId}")

            override suspend fun markMultipleRead(body: MarkVolumesReadDto) = log("chapters+ ${body.chapterIds}")

            override suspend fun markMultipleUnread(body: MarkVolumesReadDto) = log("chapters- ${body.chapterIds}")

            override suspend fun addWantToRead(body: UpdateWantToReadDto) = log("wtr+ ${body.seriesIds}")

            override suspend fun removeWantToRead(body: UpdateWantToReadDto) = log("wtr- ${body.seriesIds}")

            override suspend fun rateSeries(body: UpdateRatingDto) = log("rate ${body.seriesId} ${body.userRating}")
        }

    private val repo = OfflineFirstReadStateRepository({ api }, dao, outbox)

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        dao.replaceSeriesDetail(
            SeriesEntity(serverId, 7, 1, "S", "s", format = 0, pages = 60, pagesRead = 5),
            listOf(
                VolumeEntity(serverId, 5, 7, "1", 1f, 1f, pages = 40, pagesRead = 5),
                VolumeEntity(serverId, 6, 7, "2", 2f, 2f, 20, 0),
            ),
            listOf(
                ChapterEntity(serverId, 10, 5, 7, pages = 20, pagesRead = 5, format = 0),
                ChapterEntity(serverId, 11, 5, 7, pages = 20, pagesRead = 0, format = 0),
                ChapterEntity(serverId, 12, 6, 7, pages = 20, pagesRead = 0, format = 0),
            ),
        )
        dao.upsertMetadata(SeriesMetadataEntity(serverId, 7))
    }

    private suspend fun detail() =
        OfflineFirstCatalogRepository(db.libraryDao(), dao, {
            api
        }).seriesDetail(server, 7).first()

    @Test
    fun marksUpdateRoomTotalsAndCallTheMatchingEndpoint() =
        runTest {
            seed()
            assertEquals(AppResult.Success(Unit), repo.setRead(server, ReadTarget.Volume(7, 5), read = true))
            var d = detail()
            assertEquals(listOf(20, 20, 0), d.chapters.map { it.pagesRead })
            assertEquals(listOf(40, 0), d.volumes.map { it.pagesRead })
            assertEquals(40, d.series?.pagesRead)
            repo.setRead(server, ReadTarget.Chapter(7, 11), read = false)
            repo.setRead(server, ReadTarget.Series(7), read = true)
            d = detail()
            assertEquals(60, d.series?.pagesRead)
            repo.setWantToRead(server, 7, on = true)
            assertEquals(true, detail().metadata?.wantToRead)
            assertEquals(listOf("volume+ 5", "chapters- [11]", "series+ 7", "wtr+ [7]"), calls)
            assertEquals(emptyList(), outbox.pending(serverId))
        }

    @Test
    fun offlineChangesApplyLocallyAndQueueOnlyTheLatestPerTarget() =
        runTest {
            seed()
            offline = true
            assertEquals(AppResult.Failure(AppError.Network), repo.setRead(server, ReadTarget.Series(7), read = true))
            repo.setRead(server, ReadTarget.Series(7), read = false)
            repo.setWantToRead(server, 7, on = true)
            assertEquals(0, detail().series?.pagesRead)
            assertEquals(true, detail().metadata?.wantToRead)
            val queued = outbox.pending(serverId)
            assertEquals(
                listOf(OUTBOX_MARK_READ to "series:7", OUTBOX_WANT_TO_READ to "series:7"),
                queued.map {
                    it.type to
                        it.entityKey
                },
            )
            assertEquals(true, queued.first().payload.contains("\"read\":false"))
            // Back online: a successful push clears the stale queued entry.
            offline = false
            repo.setRead(server, ReadTarget.Series(7), read = true)
            assertEquals(listOf(OUTBOX_WANT_TO_READ), outbox.pending(serverId).map { it.type })
        }

    @Test
    fun ratingIsClampedStoredAndQueuedWhenOffline() =
        runTest {
            seed()
            repo.setRating(server, 7, 9f)
            assertEquals(5f, detail().series?.userRating)
            assertEquals("rate 7 5.0", calls.last())
            offline = true
            repo.setRating(server, 7, 0f)
            assertEquals(0f, detail().series?.userRating)
            assertEquals(listOf(OUTBOX_RATING to "series:7"), outbox.pending(serverId).map { it.type to it.entityKey })
        }
}
