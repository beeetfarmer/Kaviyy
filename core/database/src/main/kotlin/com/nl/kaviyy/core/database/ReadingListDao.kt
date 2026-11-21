package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ROW =
    "SELECT r.*, (SELECT COUNT(*) FROM reading_list_items i WHERE i.serverId = r.serverId AND " +
        "i.readingListId = r.id AND i.pagesTotal > 0 AND i.pagesRead >= i.pagesTotal) AS readCount FROM reading_lists r"

@Dao
interface ReadingListDao {
    @Query("$ROW WHERE r.serverId = :serverId ORDER BY r.position")
    fun observe(serverId: Long): Flow<List<ReadingListRow>>

    @Query("$ROW WHERE r.serverId = :serverId AND r.id = :id")
    fun observe(
        serverId: Long,
        id: Int,
    ): Flow<ReadingListRow?>

    @Query("SELECT * FROM reading_list_items WHERE serverId = :serverId AND readingListId = :listId ORDER BY position")
    fun items(
        serverId: Long,
        listId: Int,
    ): Flow<List<ReadingListItemEntity>>

    @Upsert
    suspend fun upsert(rows: List<ReadingListEntity>)

    @Upsert
    suspend fun upsertItems(rows: List<ReadingListItemEntity>)

    @Query("DELETE FROM reading_lists WHERE serverId = :serverId AND id NOT IN (:keep)")
    suspend fun deleteExcept(
        serverId: Long,
        keep: List<Int>,
    )

    @Query(
        "DELETE FROM reading_list_items WHERE serverId = :serverId AND readingListId = :listId AND id NOT IN (:keep)",
    )
    suspend fun deleteItemsExcept(
        serverId: Long,
        listId: Int,
        keep: List<Int>,
    )

    @Query("DELETE FROM reading_lists WHERE serverId = :serverId AND id = :id")
    suspend fun delete(
        serverId: Long,
        id: Int,
    )

    @Transaction
    suspend fun replaceAll(
        serverId: Long,
        rows: List<ReadingListEntity>,
    ) {
        deleteExcept(serverId, rows.map { it.id })
        upsert(rows)
    }

    @Transaction
    suspend fun replaceItems(
        serverId: Long,
        listId: Int,
        rows: List<ReadingListItemEntity>,
    ) {
        deleteItemsExcept(serverId, listId, rows.map { it.id })
        upsertItems(rows)
    }
}
