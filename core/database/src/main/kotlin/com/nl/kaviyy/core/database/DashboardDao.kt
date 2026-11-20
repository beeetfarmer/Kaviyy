package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Cached Home: the user's streams, their items, and the series/chapters those items point at. */
@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_streams WHERE serverId = :serverId AND visible = 1 ORDER BY position, id")
    fun observeStreams(serverId: Long): Flow<List<DashboardStreamEntity>>

    @Query("SELECT * FROM dashboard_streams WHERE serverId = :serverId ORDER BY position, id")
    suspend fun streams(serverId: Long): List<DashboardStreamEntity>

    @Query("SELECT * FROM dashboard_items WHERE serverId = :serverId ORDER BY streamId, position")
    fun observeItems(serverId: Long): Flow<List<DashboardItemEntity>>

    @Query(
        "SELECT * FROM series WHERE serverId = :serverId AND id IN " +
            "(SELECT seriesId FROM dashboard_items WHERE serverId = :serverId)",
    )
    fun observeSeries(serverId: Long): Flow<List<SeriesEntity>>

    @Query(
        "SELECT * FROM chapters WHERE serverId = :serverId AND id IN " +
            "(SELECT chapterId FROM dashboard_items WHERE serverId = :serverId)",
    )
    fun observeChapters(serverId: Long): Flow<List<ChapterEntity>>

    /** For list endpoints that return partial series: never overwrite a full cached row with less. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeriesIfAbsent(rows: List<SeriesEntity>)

    @Query("DELETE FROM dashboard_streams WHERE serverId = :serverId AND id NOT IN (:keep)")
    suspend fun deleteStreamsExcept(
        serverId: Long,
        keep: List<Int>,
    )

    @Upsert
    suspend fun upsertStreams(rows: List<DashboardStreamEntity>)

    /** Streams removed on the server disappear with their items (cascade). */
    @Transaction
    suspend fun replaceStreams(
        serverId: Long,
        rows: List<DashboardStreamEntity>,
    ) {
        deleteStreamsExcept(serverId, rows.map { it.id })
        upsertStreams(rows)
    }

    @Query("DELETE FROM dashboard_items WHERE serverId = :serverId AND streamId = :streamId")
    suspend fun clearItems(
        serverId: Long,
        streamId: Int,
    )

    @Upsert
    suspend fun upsertItems(rows: List<DashboardItemEntity>)

    @Transaction
    suspend fun replaceItems(
        serverId: Long,
        streamId: Int,
        rows: List<DashboardItemEntity>,
    ) {
        clearItems(serverId, streamId)
        upsertItems(rows)
    }
}
