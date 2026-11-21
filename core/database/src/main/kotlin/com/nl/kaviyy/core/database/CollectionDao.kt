package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections WHERE serverId = :serverId ORDER BY position")
    fun observe(serverId: Long): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE serverId = :serverId AND id = :id")
    fun observe(
        serverId: Long,
        id: Int,
    ): Flow<CollectionEntity?>

    @Upsert
    suspend fun upsert(rows: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE serverId = :serverId AND id NOT IN (:keep)")
    suspend fun deleteExcept(
        serverId: Long,
        keep: List<Int>,
    )

    @Query("DELETE FROM collections WHERE serverId = :serverId AND id = :id")
    suspend fun delete(
        serverId: Long,
        id: Int,
    )

    /** The server's list replaces the cached one. */
    @Transaction
    suspend fun replaceAll(
        serverId: Long,
        rows: List<CollectionEntity>,
    ) {
        deleteExcept(serverId, rows.map { it.id })
        upsert(rows)
    }
}
