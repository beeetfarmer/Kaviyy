package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BookFileRepositoryTest {
    private val url = (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url
    private val reader = Server(ServerId(1), "k", url, roles = setOf(ROLE_DOWNLOAD))
    private val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)
    private var body = zip
    private val fetched = mutableListOf<Int>()

    private val api =
        object : FakeKavitaApi() {
            override suspend fun downloadChapter(chapterId: Int) = body.toResponseBody().also { fetched += chapterId }
        }

    private fun repo() =
        CachedBookFileRepository(RuntimeEnvironment.getApplication(), { api }, UnconfinedTestDispatcher())

    @Test
    fun withoutTheDownloadRoleNothingIsFetched() =
        runTest {
            assertEquals(AppResult.Failure(AppError.Forbidden), repo().file(reader.copy(roles = emptySet()), 1, "epub"))
            assertTrue(fetched.isEmpty())
            assertTrue(reader.copy(roles = setOf(ROLE_ADMIN)).canDownload)
        }

    @Test
    fun downloadsOnceThenServesTheCache() =
        runTest {
            val repo = repo()
            val first = (repo.file(reader, 7, "epub") as AppResult.Success).value
            assertEquals("7.epub", first.name)
            assertEquals("1", first.parentFile?.name) // server id, ids only in paths
            assertTrue(first.readBytes().contentEquals(zip))
            repo.file(reader, 7, "epub")
            assertEquals(listOf(7), fetched)
        }

    @Test
    fun aNonZipBodyIsRejectedAndLeavesNoFile() =
        runTest {
            body = "<html>error</html>".toByteArray()
            assertEquals(AppResult.Failure(AppError.InvalidData), repo().file(reader, 8, "epub"))
            assertFalse(
                RuntimeEnvironment.getApplication().cacheDir.resolve("books/1").listFiles().orEmpty().any {
                    it.name.startsWith("8.")
                },
            )
        }

    @Test
    fun onlyTheNewestBooksStayCached() =
        runTest {
            val repo = repo()
            (1..7).forEach { id ->
                val f = (repo.file(reader, 100 + id, "epub") as AppResult.Success).value
                f.setLastModified(1_000L * id)
            }
            val left =
                RuntimeEnvironment
                    .getApplication()
                    .cacheDir
                    .resolve("books/1")
                    .list()
                    .orEmpty()
                    .sorted()
            assertEquals(5, left.size)
            assertFalse("101.epub" in left)
        }
}
