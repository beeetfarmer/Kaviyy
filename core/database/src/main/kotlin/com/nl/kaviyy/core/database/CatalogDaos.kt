package com.nl.kaviyy.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Libraries of one server. */
@Dao
interface LibraryDao {
    @Query("SELECT * FROM libraries WHERE serverId = :serverId ORDER BY name COLLATE NOCASE")
    fun observeLibraries(serverId: Long): Flow<List<LibraryEntity>>

    @Upsert
    suspend fun upsertLibraries(rows: List<LibraryEntity>)

    @Query("DELETE FROM libraries WHERE serverId = :serverId AND id NOT IN (:keep)")
    suspend fun deleteLibrariesExcept(
        serverId: Long,
        keep: List<Int>,
    )

    /** The server's library list is small and complete: replace it. */
    @Transaction
    suspend fun replaceLibraries(
        serverId: Long,
        rows: List<LibraryEntity>,
    ) {
        deleteLibrariesExcept(serverId, rows.map { it.id })
        upsertLibraries(rows)
    }
}

/** Series with their volumes and chapters, always scoped to one server. */
@Dao
interface SeriesDao {
    @Query(
        "SELECT s.* FROM query_items q JOIN series s ON s.serverId = q.serverId AND s.id = q.seriesId " +
            "WHERE q.serverId = :serverId AND q.queryKey = :queryKey ORDER BY q.position",
    )
    fun seriesForQuery(
        serverId: Long,
        queryKey: String,
    ): PagingSource<Int, SeriesEntity>

    @Query("DELETE FROM query_items WHERE serverId = :serverId AND queryKey = :queryKey")
    suspend fun clearQuery(
        serverId: Long,
        queryKey: String,
    )

    @Upsert
    suspend fun upsertQueryItems(rows: List<QueryItemEntity>)

    /** One server page of a query: a refresh replaces the query's rows, an append adds after [offset]. */
    @Transaction
    suspend fun saveQueryPage(
        serverId: Long,
        queryKey: String,
        offset: Int,
        series: List<SeriesEntity>,
    ) {
        if (offset == 0) clearQuery(serverId, queryKey)
        upsertSeries(series)
        upsertQueryItems(series.mapIndexed { i, s -> QueryItemEntity(serverId, queryKey, offset + i, s.id) })
    }

    @Upsert
    suspend fun upsertSeries(rows: List<SeriesEntity>)

    @Query("SELECT id FROM chapters WHERE serverId = :serverId AND seriesId = :seriesId")
    suspend fun chapterIdsOfSeries(
        serverId: Long,
        seriesId: Int,
    ): List<Int>

    @Query("SELECT id FROM chapters WHERE serverId = :serverId AND volumeId = :volumeId")
    suspend fun chapterIdsOfVolume(
        serverId: Long,
        volumeId: Int,
    ): List<Int>

    @Query(
        "UPDATE chapters SET pagesRead = CASE WHEN :read THEN pages ELSE 0 END " +
            "WHERE serverId = :serverId AND id IN (:ids)",
    )
    suspend fun setChaptersRead(
        serverId: Long,
        ids: List<Int>,
        read: Boolean,
    )

    @Query(
        "UPDATE volumes SET pagesRead = (SELECT COALESCE(SUM(c.pagesRead), 0) FROM chapters c " +
            "WHERE c.serverId = volumes.serverId AND c.volumeId = volumes.id) " +
            "WHERE serverId = :serverId AND seriesId = :seriesId",
    )
    suspend fun recountVolumes(
        serverId: Long,
        seriesId: Int,
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

    /** Marks [ids] fully read or unread and recomputes their volume and series totals. */
    @Transaction
    suspend fun applyRead(
        serverId: Long,
        seriesId: Int,
        ids: List<Int>,
        read: Boolean,
    ) {
        setChaptersRead(serverId, ids, read)
        recountVolumes(serverId, seriesId)
        recountSeries(serverId, seriesId)
    }

    @Query("UPDATE series SET userRating = :rating WHERE serverId = :serverId AND id = :seriesId")
    suspend fun setUserRating(
        serverId: Long,
        seriesId: Int,
        rating: Float,
    )

    @Query("UPDATE series_metadata SET wantToRead = :on WHERE serverId = :serverId AND seriesId = :seriesId")
    suspend fun setWantToRead(
        serverId: Long,
        seriesId: Int,
        on: Boolean,
    )

    @Query("SELECT * FROM series_metadata WHERE serverId = :serverId AND seriesId = :seriesId")
    fun observeMetadata(
        serverId: Long,
        seriesId: Int,
    ): Flow<SeriesMetadataEntity?>

    @Upsert
    suspend fun upsertMetadata(row: SeriesMetadataEntity)

    @Query("SELECT * FROM series WHERE serverId = :serverId AND id = :seriesId")
    fun observeSeries(
        serverId: Long,
        seriesId: Int,
    ): Flow<SeriesEntity?>

    @Query("SELECT * FROM volumes WHERE serverId = :serverId AND seriesId = :seriesId ORDER BY minNumber, id")
    fun observeVolumes(
        serverId: Long,
        seriesId: Int,
    ): Flow<List<VolumeEntity>>

    @Query("SELECT * FROM chapters WHERE serverId = :serverId AND seriesId = :seriesId ORDER BY sortOrder, id")
    fun observeChapters(
        serverId: Long,
        seriesId: Int,
    ): Flow<List<ChapterEntity>>

    @Upsert
    suspend fun upsertVolumes(rows: List<VolumeEntity>)

    @Upsert
    suspend fun upsertChapters(rows: List<ChapterEntity>)

    @Query("DELETE FROM volumes WHERE serverId = :serverId AND seriesId = :seriesId AND id NOT IN (:keep)")
    suspend fun deleteVolumesExcept(
        serverId: Long,
        seriesId: Int,
        keep: List<Int>,
    )

    @Query("DELETE FROM chapters WHERE serverId = :serverId AND seriesId = :seriesId AND id NOT IN (:keep)")
    suspend fun deleteChaptersExcept(
        serverId: Long,
        seriesId: Int,
        keep: List<Int>,
    )

    /** Series detail is fetched whole, so volumes/chapters no longer on the server are dropped. */
    @Transaction
    suspend fun replaceSeriesDetail(
        series: SeriesEntity,
        volumes: List<VolumeEntity>,
        chapters: List<ChapterEntity>,
    ) {
        upsertSeries(listOf(series))
        deleteVolumesExcept(series.serverId, series.id, volumes.map { it.id })
        deleteChaptersExcept(series.serverId, series.id, chapters.map { it.id })
        upsertVolumes(volumes)
        upsertChapters(chapters)
    }
}
