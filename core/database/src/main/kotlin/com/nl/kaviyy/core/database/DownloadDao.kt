package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A downloaded chapter and the server's newer version of it. */
data class OutdatedDownload(
    val chapterId: Int,
    val lastModified: Long,
)

/** A download with its series' name and library type (null until they are cached). */
data class DownloadWithSeries(
    @Embedded val download: DownloadEntity,
    val seriesName: String?,
    /** Kavita `LibraryType`. */
    val libraryType: Int?,
)

@Dao
interface DownloadDao {
    @Query(
        "SELECT d.*, s.name AS seriesName, l.type AS libraryType FROM downloads d " +
            "LEFT JOIN series s ON s.serverId = d.serverId AND s.id = d.seriesId " +
            "LEFT JOIN libraries l ON l.serverId = s.serverId AND l.id = s.libraryId " +
            "WHERE d.serverId = :serverId ORDER BY d.updatedAt",
    )
    fun observeWithSeries(serverId: Long): Flow<List<DownloadWithSeries>>

    /** Cached chapters that have a download row (for titles and read state). */
    @Query(
        "SELECT c.* FROM chapters c JOIN downloads d ON d.serverId = c.serverId AND d.chapterId = c.id " +
            "WHERE d.serverId = :serverId",
    )
    fun observeChapters(serverId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId ORDER BY updatedAt")
    fun observe(serverId: Long): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND seriesId = :seriesId")
    fun observeSeries(
        serverId: Long,
        seriesId: Int,
    ): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverId = :serverId AND chapterId = :chapterId")
    suspend fun get(
        serverId: Long,
        chapterId: Int,
    ): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state = :state")
    suspend fun inState(state: String): List<DownloadEntity>

    /** Bytes on disk (or written so far) across every server, for the storage limit. */
    @Query("SELECT COALESCE(SUM(bytesDone), 0) FROM downloads")
    suspend fun totalBytes(): Long

    /**
     * Finished downloads whose cached chapter changed on the server since (newer `lastModified`). Rows with no recorded
     * version (0) are skipped: nothing to compare against.
     */
    @Query(
        "SELECT d.chapterId, c.lastModified FROM downloads d JOIN chapters c ON c.serverId = d.serverId AND " +
            "c.id = d.chapterId WHERE d.serverId = :serverId AND d.seriesId = :seriesId AND d.state = 'COMPLETED' " +
            "AND d.serverLastModified > 0 AND c.lastModified > d.serverLastModified",
    )
    fun observeOutdated(
        serverId: Long,
        seriesId: Int,
    ): Flow<List<OutdatedDownload>>

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND chapterId = :chapterId")
    suspend fun delete(
        serverId: Long,
        chapterId: Int,
    )
}
