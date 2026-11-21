package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A downloaded chapter and the server's newer version of it. */
data class OutdatedDownload(
    val chapterId: Int,
    val lastModified: Long,
)

@Dao
interface DownloadDao {
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
