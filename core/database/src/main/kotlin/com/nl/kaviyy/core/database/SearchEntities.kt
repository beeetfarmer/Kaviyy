package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A query the user searched on one server (screen 05 recents). */
@Entity(
    tableName = "recent_searches",
    primaryKeys = ["serverId", "query"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE)],
)
data class RecentSearchEntity(
    val serverId: Long,
    val query: String,
    val usedAt: Long,
)

@Dao
interface RecentSearchDao {
    @Query("SELECT `query` FROM recent_searches WHERE serverId = :serverId ORDER BY usedAt DESC LIMIT :limit")
    fun observe(
        serverId: Long,
        limit: Int,
    ): Flow<List<String>>

    @Upsert
    suspend fun upsert(row: RecentSearchEntity)

    @Query(
        "DELETE FROM recent_searches WHERE serverId = :serverId AND `query` NOT IN " +
            "(SELECT `query` FROM recent_searches WHERE serverId = :serverId ORDER BY usedAt DESC LIMIT :keep)",
    )
    suspend fun trim(
        serverId: Long,
        keep: Int,
    )

    @Query("DELETE FROM recent_searches WHERE serverId = :serverId")
    suspend fun clear(serverId: Long)

    @Query("SELECT MAX(usedAt) FROM recent_searches WHERE serverId = :serverId")
    suspend fun latest(serverId: Long): Long?

    /** Records [row] and keeps only the newest [keep]. */
    @Transaction
    suspend fun add(
        row: RecentSearchEntity,
        keep: Int,
    ) {
        // Strictly newer than the last one, so two searches in the same millisecond still order correctly.
        upsert(row.copy(usedAt = maxOf(row.usedAt, (latest(row.serverId) ?: 0L) + 1)))
        trim(row.serverId, keep)
    }
}
