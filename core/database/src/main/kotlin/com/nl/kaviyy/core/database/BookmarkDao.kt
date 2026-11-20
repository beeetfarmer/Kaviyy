package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Page bookmarks of one server. */
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE serverId = :serverId AND chapterId = :chapterId ORDER BY page")
    fun observeChapter(
        serverId: Long,
        chapterId: Int,
    ): Flow<List<BookmarkEntity>>

    @Upsert
    suspend fun upsert(rows: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE serverId = :serverId AND chapterId = :chapterId AND page = :page")
    suspend fun delete(
        serverId: Long,
        chapterId: Int,
        page: Int,
    )

    @Query("DELETE FROM bookmarks WHERE serverId = :serverId AND chapterId = :chapterId AND page NOT IN (:keep)")
    suspend fun deleteChapterExcept(
        serverId: Long,
        chapterId: Int,
        keep: List<Int>,
    )

    /** The server's list for a chapter wins, except pages in [keep] (local changes not pushed yet). */
    @Transaction
    suspend fun replaceChapter(
        serverId: Long,
        chapterId: Int,
        rows: List<BookmarkEntity>,
        keep: List<Int>,
    ) {
        deleteChapterExcept(serverId, chapterId, rows.map { it.page } + keep)
        upsert(rows.filter { it.page !in keep })
    }
}
