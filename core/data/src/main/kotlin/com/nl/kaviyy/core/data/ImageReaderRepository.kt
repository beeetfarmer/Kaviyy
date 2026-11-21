package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ChapterInfoDto
import com.nl.kaviyy.core.network.apiCall
import java.io.File
import javax.inject.Inject

/** What the reader needs to show one chapter. Null numbers = none (loose leaf volume, default chapter). */
data class ReaderChapter(
    val id: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryId: Int,
    val seriesName: String,
    val volumeNumber: String?,
    val chapterNumber: String?,
    val title: String?,
    val isSpecial: Boolean,
    val pages: Int,
    /** Kavita `MangaFormat`: EPUB (3) has its own reader; PDF (4) pages are rasterised by Kavita (`extractPdf`). */
    val format: Int,
    /** Per page (index = page); null when the server sent none for it. */
    val pageSizes: List<PageSize?> = emptyList(),
    /** Downloaded page files (index = page); null when the chapter isn't downloaded. */
    val localPages: List<File>? = null,
) {
    /** Shown in the image reader: archives, images, and PDFs (as server-rendered pages). */
    val isImage: Boolean get() = format != FORMAT_EPUB
    val isEpub: Boolean get() = format == FORMAT_EPUB
    val isPdf: Boolean get() = format == FORMAT_PDF
}

data class PageSize(
    val width: Int,
    val height: Int,
    /** A two-page spread: shown alone in double page mode. */
    val isWide: Boolean,
)

private const val FORMAT_EPUB = 3
private const val FORMAT_PDF = 4

/** Kavita's "no number" sentinels (loose leaf volume / default chapter / special volume). */
private val NO_NUMBER = setOf("-100000", "100000", "0", "")

/** Chapter metadata and neighbours for the image reader; a downloaded chapter opens from its files. */
interface ImageReaderRepository {
    suspend fun chapter(
        server: Server,
        chapterId: Int,
    ): AppResult<ReaderChapter>

    /** The next chapter's id, or null at the end of the series. */
    suspend fun next(
        server: Server,
        chapter: ReaderChapter,
    ): AppResult<Int?>

    /** The previous chapter's id, or null at the start. */
    suspend fun previous(
        server: Server,
        chapter: ReaderChapter,
    ): AppResult<Int?>
}

class KavitaImageReaderRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val local: DownloadedContent,
    ) : ImageReaderRepository {
        override suspend fun chapter(
            server: Server,
            chapterId: Int,
        ): AppResult<ReaderChapter> {
            val downloaded = local.chapter(server, chapterId)
            val info = downloaded?.manifest?.info
            return if (downloaded != null && info != null && downloaded.pageFiles.size == info.pages) {
                AppResult.Success(info.toReaderChapter(chapterId).copy(localPages = downloaded.pageFiles))
            } else {
                apiCall { apis.api(server).chapterInfo(chapterId) }.map { it.toReaderChapter(chapterId) }
            }
        }

        override suspend fun next(
            server: Server,
            chapter: ReaderChapter,
        ): AppResult<Int?> =
            apiCall { apis.api(server).nextChapter(chapter.seriesId, chapter.volumeId, chapter.id) }.map {
                it.takeIf { id ->
                    id >
                        0
                }
            }

        override suspend fun previous(
            server: Server,
            chapter: ReaderChapter,
        ): AppResult<Int?> =
            apiCall { apis.api(server).prevChapter(chapter.seriesId, chapter.volumeId, chapter.id) }.map {
                it.takeIf { id ->
                    id >
                        0
                }
            }
    }

internal fun ChapterInfoDto.toReaderChapter(id: Int) =
    ReaderChapter(
        id = id,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        seriesName = seriesName.orEmpty(),
        volumeNumber = volumeNumber?.trim()?.takeUnless { it in NO_NUMBER },
        chapterNumber = chapterNumber?.trim()?.takeUnless { it in NO_NUMBER },
        title = chapterTitle?.takeIf { it.isNotBlank() },
        isSpecial = isSpecial,
        pages = pages,
        format = seriesFormat,
        pageSizes =
            pageDimensions.orEmpty().associateBy { it.pageNumber }.let { byPage ->
                List(pages) { i ->
                    byPage[i]
                        ?.takeIf {
                            it.width > 0 && it.height > 0
                        }?.let { PageSize(it.width, it.height, it.isWide) }
                }
            },
    )
