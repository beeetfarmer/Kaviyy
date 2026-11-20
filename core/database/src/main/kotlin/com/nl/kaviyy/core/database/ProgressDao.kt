package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** Local read positions; also keeps the catalog's pagesRead totals in step. */
@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE serverId = :serverId AND chapterId = :chapterId")
    suspend fun get(
        serverId: Long,
        chapterId: Int,
    ): ProgressEntity?

    @Upsert
    suspend fun upsert(row: ProgressEntity)

    @Query("SELECT pagesRead FROM chapters WHERE serverId = :serverId AND id = :chapterId")
    suspend fun chapterPagesRead(
        serverId: Long,
        chapterId: Int,
    ): Int?

    @Query("UPDATE chapters SET pagesRead = MIN(:pagesRead, pages) WHERE serverId = :serverId AND id = :chapterId")
    suspend fun setChapterPagesRead(
        serverId: Long,
        chapterId: Int,
        pagesRead: Int,
    )

    @Query(
        "UPDATE volumes SET pagesRead = (SELECT COALESCE(SUM(c.pagesRead), 0) FROM chapters c " +
            "WHERE c.serverId = volumes.serverId AND c.volumeId = volumes.id) " +
            "WHERE serverId = :serverId AND id = :volumeId",
    )
    suspend fun recountVolume(
        serverId: Long,
        volumeId: Int,
    )

    @Query(
        "UPDATE series SET pagesRead = (SELECT COALESCE(SUM(c.pagesRead), 0) FROM chapters c " +
            "WHERE c.serverId = series.serverId AND c.seriesId = series.id) " +
            "WHERE serverId = :serverId AND id = :seriesId",
    )
    suspend fun recountSeries(
        serverId: Long,
        seriesId: Int,
    )

    /** Saves a position and updates the chapter, volume and series pagesRead it implies. */
    @Transaction
    suspend fun save(row: ProgressEntity) {
        upsert(row)
        setChapterPagesRead(row.serverId, row.chapterId, row.pageNum)
        recountVolume(row.serverId, row.volumeId)
        recountSeries(row.serverId, row.seriesId)
    }
}
