package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ChapterInfoDto
import com.nl.kaviyy.core.network.FileDimensionDto
import com.nl.kaviyy.core.network.pageUrl
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageReaderRepositoryTest {
    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val api =
        object : FakeKavitaApi() {
            override suspend fun chapterInfo(
                chapterId: Int,
                includeDimensions: Boolean,
                extractPdf: Boolean,
            ) = ChapterInfoDto(
                seriesId = 7,
                volumeId = 5,
                seriesName = "Ember Road",
                volumeNumber = "-100000",
                chapterNumber = "49",
                pages = 3,
                pageDimensions = listOf(FileDimensionDto(1, 1600, 1200, isWide = true), FileDimensionDto(0, 800, 1200)),
            )

            override suspend fun nextChapter(
                seriesId: Int,
                volumeId: Int,
                chapterId: Int,
            ) = -1

            override suspend fun prevChapter(
                seriesId: Int,
                volumeId: Int,
                chapterId: Int,
            ) = 48
        }
    private val repo = KavitaImageReaderRepository { api }

    @Test
    fun sentinelsBecomeNullAndMinusOneMeansNoNeighbour() =
        runTest {
            val c = (repo.chapter(server, 49) as AppResult.Success).value
            assertNull(c.volumeNumber)
            assertEquals("49", c.chapterNumber)
            assertEquals(listOf(PageSize(800, 1200, false), PageSize(1600, 1200, true), null), c.pageSizes)
            assertEquals(AppResult.Success(null), repo.next(server, c))
            assertEquals(AppResult.Success(48), repo.previous(server, c))
        }

    @Test
    fun epubsHaveTheirOwnReaderPdfsAreServerRenderedPagesAndUrlsCarryNoCredentials() {
        val c = ChapterInfoDto(seriesFormat = 3).toReaderChapter(1)
        assertFalse(c.isImage)
        assertTrue(ChapterInfoDto(seriesFormat = 1).toReaderChapter(1).isImage)
        val pdf = ChapterInfoDto(seriesFormat = 4).toReaderChapter(1)
        assertTrue(pdf.isImage && pdf.isPdf)
        assertEquals("https://s.lan/api/Reader/image?chapterId=49&page=0&extractPdf=true", server.pageUrl(49, 0))
    }
}
