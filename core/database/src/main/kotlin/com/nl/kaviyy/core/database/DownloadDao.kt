package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND chapterId = :chapterId")
    suspend fun delete(
        serverId: Long,
        chapterId: Int,
    )
}
