package com.nl.kaviyy.core.download

import androidx.room.Room
import com.nl.kaviyy.core.data.DownloadedContent
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ChapterInfoDto
import com.nl.kaviyy.core.network.FileDimensionDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 1, 2)
private val EPUB = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(2000) { 7 }

@RunWith(RobolectricTestRunner::class)
class ChapterDownloaderTest {
    private val app = RuntimeEnvironment.getApplication()
    private val db =
        Room
            .inMemoryDatabaseBuilder(app, KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url, "0.9.0.2")
    private val store = DownloadStore(db.downloadDao(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

    private val pageCalls = mutableListOf<Int>()
    private var failPage = -1
    private var bookResponse: (String?) -> Response<ResponseBody> = { Response.success(EPUB.toResponseBody()) }
    private val ranges = mutableListOf<String?>()
    private val api =
        object : FakeKavitaApi() {
            override suspend fun chapterInfo(
                chapterId: Int,
                includeDimensions: Boolean,
                extractPdf: Boolean,
            ) = ChapterInfoDto(pages = 3, pageDimensions = listOf(FileDimensionDto(1, 800, 1200)))

            override suspend fun pageImage(
                chapterId: Int,
                page: Int,
                extractPdf: Boolean,
            ): ResponseBody {
                pageCalls += page
                if (page == failPage) throw IOException("offline")
                return JPEG.toResponseBody()
            }

            override suspend fun downloadChapterFrom(
                chapterId: Int,
                range: String?,
            ): Response<ResponseBody> {
                ranges += range
                return bookResponse(range)
            }
        }
    private val downloader =
        ChapterDownloader(
            DownloadedContent(app, db.downloadDao(), Dispatchers.Unconfined),
            ServerApis {
                api
            },
            Dispatchers.Unconfined,
        )

    @After
    fun tearDown() {
        db.close()
        File(app.filesDir, "downloads").deleteRecursively()
    }

    private fun row(
        chapterId: Int = 7,
        pageImages: Boolean = true,
    ) = DownloadEntity(serverId, chapterId, 2, 3, DownloadState.QUEUED.name, pageImages)

    @Test
    fun pagesAreDownloadedVerifiedAndMovedIntoPlace() =
        runTest {
            store.enqueue(row())
            assertEquals(RunResult.Success, runDownload(store, downloader, server, 7))
            val done = store.get(serverId, 7)
            assertEquals(DownloadState.COMPLETED to 3, done?.downloadState to done?.pagesDone)
            val manifest = downloader.manifest(row())
            assertEquals(listOf("0", "1", "2"), manifest?.pages?.map { it.file })
            assertEquals("image/jpeg" to 800, manifest?.pages?.get(1)?.let { it.mime to it.width })
            assertFalse(File(app.filesDir, "downloads/$serverId/2/7.part").exists())
        }

    @Test
    fun aNetworkDropKeepsFinishedPagesAndResumesFromThere() =
        runTest {
            store.enqueue(row())
            failPage = 2
            assertEquals(RunResult.Retry, runDownload(store, downloader, server, 7))
            assertEquals(DownloadState.QUEUED to 2, store.get(serverId, 7)?.let { it.downloadState to it.pagesDone })
            failPage = -1
            pageCalls.clear()
            assertEquals(RunResult.Success, runDownload(store, downloader, server, 7))
            assertEquals(listOf(2), pageCalls) // pages 0 and 1 were not fetched again
        }

    @Test
    fun pausingStopsTheRunAndCancellingDeletesFiles() =
        runTest {
            store.enqueue(row())
            var paused = false
            runDownload(store, downloader, server, 7) {
                if (!paused) {
                    paused = true
                    store.apply(serverId, 7, DownloadEvent.Pause)
                }
            }
            assertEquals(DownloadState.PAUSED, store.get(serverId, 7)?.downloadState)
            assertEquals(listOf(0, 1), pageCalls) // the report after page 1 saw the pause
            store.remove(serverId, 7)
            store.enqueue(row())
            runDownload(store, downloader, server, 7) { store.remove(serverId, 7) }
            assertFalse(File(app.filesDir, "downloads/$serverId/2/7.part").exists())
            assertNull(store.get(serverId, 7))
        }

    @Test
    fun serverErrorsFailTheDownload() =
        runTest {
            store.enqueue(row(pageImages = false))
            bookResponse = { Response.error(403, "".toResponseBody()) }
            assertEquals(RunResult.Failure, runDownload(store, downloader, server, 7))
            assertEquals(AppError.Forbidden.toString(), store.get(serverId, 7)?.error)
        }

    @Test
    fun booksResumeWithARangeRequest() =
        runTest {
            store.enqueue(row(pageImages = false))
            val partDir = File(app.filesDir, "downloads/$serverId/2/7.part").apply { mkdirs() }
            File(partDir, "book.epub.tmp").writeBytes(EPUB.copyOf(1000))
            bookResponse = { range ->
                Response
                    .success(
                        206,
                        EPUB.copyOfRange(1000, EPUB.size).toResponseBody(),
                    ).takeIf { range == "bytes=1000-" } ?: error("no range")
            }
            assertEquals(RunResult.Success, runDownload(store, downloader, server, 7))
            assertEquals(listOf<String?>("bytes=1000-"), ranges)
            val book = File(downloader.dir(serverId, 2, 7), "book.epub")
            assertTrue(book.readBytes().contentEquals(EPUB))
            assertEquals("book.epub", downloader.manifest(row())?.book)
        }

    @Test
    fun aFullResponseToARangeRequestReplacesThePartialFile() =
        runTest {
            store.enqueue(row(pageImages = false))
            val partDir = File(app.filesDir, "downloads/$serverId/2/7.part").apply { mkdirs() }
            File(partDir, "book.epub.tmp").writeBytes(byteArrayOf(1, 2, 3))
            assertEquals(RunResult.Success, runDownload(store, downloader, server, 7))
            assertTrue(File(downloader.dir(serverId, 2, 7), "book.epub").readBytes().contentEquals(EPUB))
        }

    @Test
    fun aNonZipBookIsRejected() =
        runTest {
            store.enqueue(row(pageImages = false))
            bookResponse = { Response.success("<html>".toResponseBody(), headersOf()) }
            assertEquals(RunResult.Failure, runDownload(store, downloader, server, 7))
            assertEquals(AppError.InvalidData.toString(), store.get(serverId, 7)?.error)
        }

    @Test
    fun pathsStayUnderTheDownloadsFolder() {
        assertTrue(downloader.dir(1, 2, 3).path.endsWith("downloads/1/2/3"))
        val root = File(app.filesDir, "downloads")
        assertFailsWith<IllegalArgumentException> {
            com.nl.kaviyy.core.data
                .guardedUnder(root, File(root, "1/../../shared_prefs"))
        }
        assertFailsWith<IllegalArgumentException> {
            com.nl.kaviyy.core.data
                .guardedUnder(root, root)
        }
    }
}
