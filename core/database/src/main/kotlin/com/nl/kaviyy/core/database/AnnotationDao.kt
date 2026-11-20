package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** EPUB highlights and notes of one server, keyed by client UUID so they can be made offline. */
@Dao
interface AnnotationDao {
    @Query(
        "SELECT * FROM annotations WHERE serverId = :serverId AND chapterId = :chapterId " +
            "ORDER BY pageNumber, createdUtc",
    )
    fun observeChapter(
        serverId: Long,
        chapterId: Int,
    ): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE serverId = :serverId AND chapterId = :chapterId")
    suspend fun chapter(
        serverId: Long,
        chapterId: Int,
    ): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE serverId = :serverId AND clientUuid = :uuid")
    suspend fun get(
        serverId: Long,
        uuid: String,
    ): AnnotationEntity?

    @Upsert
    suspend fun upsert(rows: List<AnnotationEntity>)

    @Query("DELETE FROM annotations WHERE serverId = :serverId AND clientUuid = :uuid")
    suspend fun delete(
        serverId: Long,
        uuid: String,
    )

    @Query(
        "DELETE FROM annotations WHERE serverId = :serverId AND chapterId = :chapterId " +
            "AND clientUuid NOT IN (:keep)",
    )
    suspend fun deleteChapterExcept(
        serverId: Long,
        chapterId: Int,
        keep: List<String>,
    )

    /** The server's list wins, except annotations with a queued local change ([pending] client UUIDs). */
    @Transaction
    suspend fun replaceChapter(
        serverId: Long,
        chapterId: Int,
        rows: List<AnnotationEntity>,
        pending: List<String>,
    ) {
        val fresh = rows.filter { it.clientUuid !in pending }
        deleteChapterExcept(serverId, chapterId, fresh.map { it.clientUuid } + pending)
        upsert(fresh)
    }
}
