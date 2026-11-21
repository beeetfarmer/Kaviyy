package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A Kavita reading profile as the server's full JSON (so unknown fields round-trip). Negative ids are local
 * implicit profiles not yet confirmed by the server.
 */
@Entity(
    tableName = "reading_profiles",
    primaryKeys = ["serverId", "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE)],
)
data class ReadingProfileEntity(
    val serverId: Long,
    val id: Int,
    /** `ReadingProfileKind`. */
    val kind: Int,
    val json: String,
)

/** Which profile applies to a series (as resolved by the server, or a local edit). */
@Entity(
    tableName = "series_reading_profiles",
    primaryKeys = ["serverId", "seriesId"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE)],
)
data class SeriesReadingProfileEntity(
    val serverId: Long,
    val seriesId: Int,
    val profileId: Int,
)

@Dao
interface ReadingProfileDao {
    /** The series' profile, else the cached default profile, else null. */
    @Query(
        "SELECT * FROM reading_profiles WHERE serverId = :serverId AND (" +
            "id = (SELECT profileId FROM series_reading_profiles " +
            "WHERE serverId = :serverId AND seriesId = :seriesId) " +
            "OR kind = 0) ORDER BY kind = 0 LIMIT 1",
    )
    fun observeForSeries(
        serverId: Long,
        seriesId: Int,
    ): Flow<ReadingProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: ReadingProfileEntity)

    @Upsert
    suspend fun upsertBinding(binding: SeriesReadingProfileEntity)

    @Query("DELETE FROM reading_profiles WHERE serverId = :serverId AND id = :id")
    suspend fun deleteProfile(
        serverId: Long,
        id: Int,
    )

    @Transaction
    suspend fun bind(
        profile: ReadingProfileEntity,
        seriesId: Int,
    ) {
        upsertProfile(profile)
        upsertBinding(SeriesReadingProfileEntity(profile.serverId, seriesId, profile.id))
    }
}

@Dao
interface OutboxDao {
    @Query("DELETE FROM outbox WHERE serverId = :serverId AND type = :type AND entityKey = :entityKey")
    suspend fun deleteFor(
        serverId: Long,
        type: String,
        entityKey: String,
    )

    @Upsert
    suspend fun insert(entry: OutboxEntity)

    /** Coalesces: only the latest pending change per (type, key) is kept. */
    @Transaction
    suspend fun enqueue(entry: OutboxEntity) {
        deleteFor(entry.serverId, entry.type, entry.entityKey)
        insert(entry)
    }

    @Query("SELECT * FROM outbox WHERE serverId = :serverId ORDER BY id")
    suspend fun pending(serverId: Long): List<OutboxEntity>

    /** By row id, so a newer coalesced change for the same key survives. */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)

    /** Changes waiting on every server. */
    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): kotlinx.coroutines.flow.Flow<Int>
}
