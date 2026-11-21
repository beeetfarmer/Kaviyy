package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.OutdatedDownload
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import com.nl.kaviyy.core.testing.InMemoryDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AutoDownloaderTest {
    private fun chapter(
        id: Int,
        read: Int = 0,
        series: Int = 7,
    ) = ChapterEntity(1, id, 100 + id, series, pages = 10, pagesRead = read, format = 1)

    // Reading order 1 → 2 → 3 → 4 → 5, then the end.
    private val order = mapOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to -1)

    @Test
    fun takesUnreadChaptersFromTheContinuePointInServerOrder() =
        runTest {
            val chapters =
                listOf(
                    chapter(1, 10),
                    chapter(2, 4),
                    chapter(3, 10),
                    chapter(4),
                    chapter(5),
                ).associateBy { it.id }
            val picks = nextUnread(2, chapters, 2) { order.getValue(it.id) }
            assertEquals(listOf(2, 4), picks.map { it.id }) // 2 is half read, 3 already read
            assertEquals(listOf(4, 5), nextUnread(4, chapters, 5) { order.getValue(it.id) }.map { it.id })
            assertEquals(emptyList(), nextUnread(9, chapters, 3) { -1 }) // continue point not cached
        }

    @Test
    fun aLoopingNextChapterAnswerStops() =
        runTest {
            val chapters = listOf(chapter(1, 10), chapter(2, 10)).associateBy { it.id }
            assertEquals(emptyList(), nextUnread(1, chapters, 3) { if (it.id == 1) 2 else 1 })
        }

    @Test
    fun aPassQueuesTheNextChaptersOfEachOnDeckSeries() =
        runTest {
            val server =
                Server(
                    ServerId(1),
                    "k",
                    (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url,
                    roles = setOf("Download"),
                )
            val api =
                object : FakeKavitaApi() {
                    override suspend fun onDeck(
                        pageNumber: Int,
                        pageSize: Int,
                        libraryId: Int,
                    ) = listOf(SeriesDto(7))

                    override suspend fun continuePoint(seriesId: Int) = ChapterDto(3)

                    override suspend fun nextChapter(
                        seriesId: Int,
                        volumeId: Int,
                        chapterId: Int,
                    ) = order.getValue(chapterId)
                }
            val queued = mutableListOf<DownloadRequest>()
            val queue =
                object : DownloadQueue {
                    override suspend fun enqueue(
                        server: Server,
                        chapters: List<DownloadRequest>,
                    ): EnqueueResult {
                        queued += chapters
                        return EnqueueResult.Queued
                    }

                    override fun outdated(
                        server: Server,
                        seriesId: Int,
                    ) = emptyFlow<List<OutdatedDownload>>()

                    override suspend fun update(
                        server: Server,
                        chapters: List<OutdatedDownload>,
                    ) = Unit
                }
            val chapters = (1..5).map { chapter(it) }
            val auto =
                AutoDownloader(
                    RuntimeEnvironment.getApplication(),
                    proxy<AuthSession> { error("unused") },
                    ServerApis { api },
                    proxy<CatalogRepository> { name ->
                        when (name) {
                            "refreshSeries" -> AppResult.Success(Unit)
                            "seriesDetail" -> flowOf(SeriesDetail(null, emptyList(), chapters))
                            else -> error("unused: $name")
                        }
                    },
                    queue,
                    AppSettingsStore(InMemoryDataStore(AppSettings())),
                    Dispatchers.Unconfined,
                )
            auto.run(server, 2)
            assertEquals(listOf(3, 4), queued.map { it.chapterId })
        }
}

private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ -> answer(m.name) } as T
