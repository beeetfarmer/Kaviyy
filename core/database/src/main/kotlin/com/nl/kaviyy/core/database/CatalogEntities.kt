package com.nl.kaviyy.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/*
 * Server-owned rows: primary key starts with serverId (Kavita ids are only unique per server) and a cascading
 * foreign key to `servers`, so removing a server deletes all of its data. Enums are Kavita's int values; times are
 * epoch millis (UTC). Only reader-facing fields are cached; add columns with a migration when a screen needs them.
 */

private const val SERVER = "serverId"

@Entity(
    tableName = "libraries",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
)
data class LibraryEntity(
    val serverId: Long,
    val id: Int,
    val name: String,
    /** Kavita `LibraryType`. */
    val type: Int,
    val lastScanned: Long = 0,
)

@Entity(
    tableName = "series",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "libraryId")],
)
data class SeriesEntity(
    val serverId: Long,
    val id: Int,
    val libraryId: Int,
    val name: String,
    val sortName: String,
    val localizedName: String? = null,
    /** Kavita `MangaFormat`. */
    val format: Int,
    val pages: Int,
    val pagesRead: Int,
    val wordCount: Long = 0,
    val userRating: Float = 0f,
    val primaryColor: String? = null,
    val created: Long = 0,
    val lastChapterAdded: Long = 0,
    val latestReadDate: Long = 0,
)

@Entity(
    tableName = "volumes",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "seriesId")],
)
data class VolumeEntity(
    val serverId: Long,
    val id: Int,
    val seriesId: Int,
    val name: String,
    val minNumber: Float,
    val maxNumber: Float,
    val pages: Int,
    val pagesRead: Int,
    val lastModified: Long = 0,
)

@Entity(
    tableName = "chapters",
    primaryKeys = [SERVER, "id"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
    indices = [Index(SERVER, "volumeId"), Index(SERVER, "seriesId")],
)
data class ChapterEntity(
    val serverId: Long,
    val id: Int,
    val volumeId: Int,
    /** Not on ChapterDto; filled from the series detail call that returned it. */
    val seriesId: Int,
    val range: String? = null,
    val title: String? = null,
    val titleName: String? = null,
    val minNumber: Float = 0f,
    val sortOrder: Float = 0f,
    val pages: Int,
    val pagesRead: Int,
    val isSpecial: Boolean = false,
    /** Kavita `MangaFormat`: image chapters vs EPUB/PDF decide reader and download kind. */
    val format: Int,
    val wordCount: Long = 0,
    /** Sum of MangaFileDto sizes, for download estimates. */
    val fileBytes: Long = 0,
    val releaseDate: Long = 0,
    /** Server `lastModifiedUtc`: update detection for downloads. */
    val lastModified: Long = 0,
    /** Parent volume's title (e.g. "5"), for "Volume 5 · Chapter 49". */
    val volumeTitle: String? = null,
)

/** One library query's results in server order (sort + filters), so offline paging keeps that order. */
@Entity(
    tableName = "query_items",
    primaryKeys = [SERVER, "queryKey", "position"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
)
data class QueryItemEntity(
    val serverId: Long,
    val queryKey: String,
    val position: Int,
    val seriesId: Int,
)

/**
 * Series detail extras (screen 04), one row per series. List columns hold JSON (`[{"id":1,"name":"…"}]`) written and
 * read by core:data, so this module stays free of serialization.
 */
@Entity(
    tableName = "series_metadata",
    primaryKeys = [SERVER, "seriesId"],
    foreignKeys = [ForeignKey(ServerEntity::class, ["id"], [SERVER], onDelete = ForeignKey.CASCADE)],
)
data class SeriesMetadataEntity(
    val serverId: Long,
    val seriesId: Int,
    /** Raw server HTML; sanitize before display. */
    val summary: String? = null,
    val releaseYear: Int = 0,
    /** Kavita `AgeRating`. */
    val ageRating: Int = 0,
    /** Kavita `PublicationStatus`. */
    val publicationStatus: Int = 0,
    val genresJson: String = "[]",
    val tagsJson: String = "[]",
    val writersJson: String = "[]",
    val webLinks: String? = null,
    /** `api/Reader/time-left` average, hours. */
    val hoursLeft: Float = 0f,
    val wantToRead: Boolean = false,
    /** `api/Reader/continue-point`. */
    val continueChapterId: Int? = null,
)
