package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.OutdatedDownload
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.flow.Flow

/** One chapter to download, from whatever list the user picked (chapter, volume, series, reading list). */
data class DownloadRequest(
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    /** Kavita `MangaFormat`: EPUB downloads the original file, everything else page images. */
    val format: Int,
    /** Expected size, for the storage limit; 0 when unknown. */
    val bytes: Long = 0,
    /** Server `lastModified` of the chapter, kept for update detection. */
    val lastModified: Long = 0,
) {
    val pageImages: Boolean get() = format != FORMAT_EPUB
}

enum class EnqueueResult {
    Queued,

    /** Everything picked was already downloaded or queued. */
    NothingNew,

    /** The account lacks Kavita's Download role. */
    NoPermission,

    /** It would go over the storage limit set in Download settings. */
    OverLimit,
}

/** Queues downloads; ViewModels depend on this, [Downloads] implements it. */
interface DownloadQueue {
    suspend fun enqueue(
        server: Server,
        chapters: List<DownloadRequest>,
    ): EnqueueResult

    /** Downloaded chapters of a series that changed on the server ("Update available"). */
    fun outdated(
        server: Server,
        seriesId: Int,
    ): Flow<List<OutdatedDownload>>

    /** Downloads [chapters] again at their new version. */
    suspend fun update(
        server: Server,
        chapters: List<OutdatedDownload>,
    )
}

fun ChapterEntity.toDownload() = DownloadRequest(id, seriesId, volumeId, format, fileBytes, lastModified)

fun ReadingListItemEntity.toDownload() = DownloadRequest(chapterId, seriesId, volumeId, format)

private const val FORMAT_EPUB = 3
