package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.OutdatedDownload
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    /** This series' download rows (any state), for the series button and chapter rows. */
    fun seriesDownloads(
        server: Server,
        seriesId: Int,
    ): Flow<List<DownloadEntity>> = flowOf(emptyList())

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

/** One download as the Downloads tab shows it. */
data class DownloadItem(
    val row: DownloadEntity,
    val seriesName: String?,
    /** The cached chapter, for its title and read state; null if not cached. */
    val chapter: ChapterEntity?,
    /** Kavita `LibraryType` of the series' library, when cached. */
    val libraryType: Int? = null,
) {
    val state: DownloadState get() = row.downloadState
}

/** The Downloads tab's view of downloads and its controls; [Downloads] implements it. */
interface DownloadControls {
    /** Saves Wi-Fi only and re-queues waiting downloads under the new network rule. */
    suspend fun setWifiOnly(on: Boolean) = Unit

    /** Every download on every server: rows, work and files. */
    suspend fun clearAll() = Unit

    /** Deletes this server's downloaded chapters that are fully read. */
    suspend fun removeRead(serverId: Long) = Unit

    fun downloads(server: Server): Flow<List<DownloadItem>>

    suspend fun pause(
        serverId: Long,
        chapterId: Int,
    )

    suspend fun resume(
        serverId: Long,
        chapterId: Int,
    )

    suspend fun retry(
        serverId: Long,
        chapterId: Int,
    )

    suspend fun cancel(
        serverId: Long,
        chapterId: Int,
    )
}

fun ChapterEntity.toDownload() = DownloadRequest(id, seriesId, volumeId, format, fileBytes, lastModified)

fun ReadingListItemEntity.toDownload() = DownloadRequest(chapterId, seriesId, volumeId, format)

private const val FORMAT_EPUB = 3
