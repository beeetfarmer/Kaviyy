package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.AnnotationDto
import com.nl.kaviyy.core.network.BookmarkDto
import com.nl.kaviyy.core.network.MarkVolumesReadDto
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.network.UpdateRatingDto
import com.nl.kaviyy.core.network.UpdateWantToReadDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class OutboxReplayerTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val outbox = db.outboxDao()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val calls = mutableListOf<String>()
    private var offline = false

    /** Throws [failWith] for calls whose log line starts with it. */
    private var failWith: Pair<String, Exception>? = null

    private val api =
        object : FakeKavitaApi() {
            private fun log(s: String) {
                if (offline) throw IOException("offline")
                failWith?.let { (prefix, e) -> if (s.startsWith(prefix)) throw e }
                calls += s
            }

            override suspend fun markMultipleRead(body: MarkVolumesReadDto) = log("read ${body.chapterIds}")

            override suspend fun addWantToRead(body: UpdateWantToReadDto) = log("wtr+ ${body.seriesIds}")

            override suspend fun rateSeries(body: UpdateRatingDto) = log("rate ${body.userRating}")

            override suspend fun bookmark(body: BookmarkDto) = log("bookmark ${body.chapterId}:${body.page}")

            override suspend fun saveProgress(body: ProgressDto) = log("progress ${body.chapterId}@${body.pageNum}")

            override suspend fun createAnnotation(body: AnnotationDto): AnnotationDto {
                log("annotation+ ${body.selectedText}")
                return body.copy(id = 900)
            }
        }
    private val apis = ServerApis { api }
    private val replayer = OutboxReplayer(apis, outbox, db.annotationDao())
    private val chapter =
        ReaderChapter(10, 7, 5, 1, "S", "1", "10", null, false, pages = 20, format = 3)

    @After
    fun tearDown() = db.close()

    private suspend fun seed() =
        db.seriesDao().replaceSeriesDetail(
            SeriesEntity(serverId, 7, 1, "S", "s", format = 3, pages = 20, pagesRead = 0),
            listOf(VolumeEntity(serverId, 5, 7, "1", 1f, 1f, pages = 20, pagesRead = 0)),
            listOf(ChapterEntity(serverId, 10, 5, 7, pages = 20, pagesRead = 0, format = 3)),
        )

    @Test
    fun changesMadeOfflineReachTheServerInOrderOnceBackOnline() =
        runTest {
            seed()
            offline = true
            val readState = OfflineFirstReadStateRepository(apis, db.seriesDao(), outbox)
            readState.setRead(server, ReadTarget.Chapter(7, 10), true)
            readState.setWantToRead(server, 7, true)
            readState.setRating(server, 7, 4f)
            OfflineFirstBookmarkRepository(apis, db.bookmarkDao(), outbox).set(server, chapter, 3, true)
            val progress = OfflineFirstProgressRepository(apis, db.progressDao(), outbox)
            progress.record(server, chapter, 4, null)
            progress.record(server, chapter, 6, null) // coalesced: only the latest page is sent
            OfflineFirstAnnotationRepository(apis, db.annotationDao(), outbox)
                .create(server, chapter, AnnotationDraft(6, "a line", "//p[1]", 0))
            assertEquals(6, outbox.pending(serverId).size)
            assertEquals(AppResult.Failure(AppError.Network), replayer.flush(server)) // still offline: nothing lost
            assertEquals(6, outbox.pending(serverId).size)

            offline = false
            assertEquals(AppResult.Success(Unit), replayer.flush(server))
            assertEquals(
                listOf("read [10]", "wtr+ [7]", "rate 4.0", "bookmark 10:3", "progress 10@6", "annotation+ a line"),
                calls,
            )
            assertEquals(emptyList(), outbox.pending(serverId))
            // The created highlight now knows its server id, so a later edit updates instead of duplicating.
            assertEquals(
                900,
                db
                    .annotationDao()
                    .chapter(serverId, 10)
                    .single()
                    .remoteId,
            )
        }

    @Test
    fun aRejectedChangeIsDroppedAndTheRestStillGo() =
        runTest {
            queue("wantToRead", "series:7", """{"seriesId":7,"on":true}""")
            queue("rating", "series:7", """{"seriesId":7,"userRating":5.0}""")
            failWith = "wtr+" to HttpException(Response.error<Unit>(400, "".toResponseBody()))
            assertEquals(AppResult.Success(Unit), replayer.flush(server))
            assertEquals(listOf("rate 5.0"), calls)
            assertEquals(emptyList(), outbox.pending(serverId))
        }

    @Test
    fun aServerErrorStopsThePassKeepsOrderAndGivesUpEventually() =
        runTest {
            queue("wantToRead", "series:7", """{"seriesId":7,"on":true}""")
            queue("rating", "series:7", """{"seriesId":7,"userRating":5.0}""")
            failWith = "wtr+" to HttpException(Response.error<Unit>(503, "".toResponseBody()))
            assertEquals(AppResult.Failure(AppError.Server(503)), replayer.flush(server))
            assertEquals(emptyList(), calls) // the rating waits behind it
            assertEquals(listOf(1, 0), outbox.pending(serverId).map { it.attempts })
            repeat(9) { replayer.flush(server) }
            assertEquals(listOf("rate 5.0"), calls) // dropped after 10 attempts, then the rest went
        }

    @Test
    fun unreadableEntriesAreDropped() =
        runTest {
            queue("rating", "series:7", """{"seriesId":7}""")
            queue("nonsense", "x", "not json")
            assertEquals(AppResult.Success(Unit), replayer.flush(server))
            assertEquals(emptyList(), outbox.pending(serverId))
        }

    @Test
    fun aNewerChangeQueuedDuringTheFlushSurvives() =
        runTest {
            queue("rating", "series:7", """{"seriesId":7,"userRating":3.0}""")
            failWith = null
            val during =
                object : FakeKavitaApi() {
                    override suspend fun rateSeries(body: UpdateRatingDto) {
                        queue("rating", "series:7", """{"seriesId":7,"userRating":1.0}""")
                    }
                }
            OutboxReplayer(ServerApis { during }, outbox, db.annotationDao()).flush(server)
            assertEquals(listOf("""{"seriesId":7,"userRating":1.0}"""), outbox.pending(serverId).map { it.payload })
        }

    @Test
    fun readTargetsRoundTripThroughTheirKeys() {
        val targets = listOf(ReadTarget.Series(7), ReadTarget.Volume(7, 5), ReadTarget.Chapter(7, 10))
        assertEquals(targets, targets.map { ReadTarget.parse(it.key, 7) })
        assertEquals(null, ReadTarget.parse("shelf:1", 7))
    }

    private suspend fun queue(
        type: String,
        key: String,
        payload: String,
    ) = outbox.enqueue(
        OutboxEntity(
            0,
            serverId,
            type,
            key,
            payload,
            java.util.UUID
                .randomUUID()
                .toString(),
            0,
        ),
    )
}
