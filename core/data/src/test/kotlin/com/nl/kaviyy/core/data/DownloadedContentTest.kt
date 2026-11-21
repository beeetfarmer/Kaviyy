package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ChapterInfoDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Downloaded chapters open from disk in both readers, without the network or the Download role. */
@RunWith(RobolectricTestRunner::class)
class DownloadedContentTest {
    private val app = RuntimeEnvironment.getApplication()
    private val db =
        Room
            .inMemoryDatabaseBuilder(app, KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val id = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server = Server(ServerId(id), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val content = DownloadedContent(app, db.downloadDao(), Dispatchers.Unconfined)
    private val offline = ServerApis { object : FakeKavitaApi() {} } // every call throws

    @After
    fun tearDown() {
        db.close()
        File(app.filesDir, "downloads").deleteRecursively()
    }

    private suspend fun downloaded(
        chapterId: Int,
        manifest: DownloadManifest,
        state: String = "COMPLETED",
        files: List<String> = emptyList(),
    ) {
        db.downloadDao().upsert(DownloadEntity(id, chapterId, 2, 3, state, pageImages = manifest.book == null))
        val dir = content.dir(id, 2, chapterId).apply { mkdirs() }
        files.forEach { File(dir, it).writeText("x") }
        File(dir, DownloadedContent.MANIFEST).writeText(DownloadedContent.json.encodeToString(manifest))
    }

    @Test
    fun imageChaptersOpenFromTheirPageFiles() =
        runTest {
            val pages = listOf(ManifestPage(0, "0"), ManifestPage(1, "1"))
            downloaded(
                9,
                DownloadManifest(9, pages, info = ChapterInfoDto(seriesId = 2, pages = 2)),
                files = listOf("0", "1"),
            )
            val chapter = (KavitaImageReaderRepository(offline, content).chapter(server, 9) as AppResult.Success).value
            assertEquals(listOf("0", "1"), chapter.localPages?.map { it.name })
            assertEquals(2, chapter.pages)
        }

    @Test
    fun epubsOpenFromTheDownloadEvenWithoutTheRole() =
        runTest {
            downloaded(9, DownloadManifest(9, book = "book.epub"), files = listOf("book.epub"))
            val repo = CachedBookFileRepository(app, offline, content, Dispatchers.Unconfined)
            assertEquals("book.epub", (repo.file(server, 9, "epub") as AppResult.Success).value.name)
        }

    @Test
    fun unfinishedDownloadsAreIgnored() =
        runTest {
            downloaded(9, DownloadManifest(9, book = "book.epub"), state = "RUNNING", files = listOf("book.epub"))
            assertNull(content.chapter(server, 9))
        }

    @Test
    fun pathsCannotEscapeTheDownloadsFolder() {
        assertFailsWith<IllegalArgumentException> {
            guardedUnder(
                content.root,
                File(content.root, "1/../../shared_prefs"),
            )
        }
        assertFailsWith<IllegalArgumentException> { guardedUnder(content.root, content.root) }
    }
}
