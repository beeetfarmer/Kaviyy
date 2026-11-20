package com.nl.kaviyy.core.network

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * Only the fields we cache (see core:database entities). Every field has a default so a server that omits one still
 * parses; unknown fields are ignored. Paths and DTOs verified against api/openapi.json (v0.9.0.2).
 */

@Serializable
data class LibraryDto(
    val id: Int,
    val name: String? = null,
    val type: Int = 0,
    val lastScanned: String? = null,
)

@Serializable
data class SeriesDto(
    val id: Int,
    val libraryId: Int = 0,
    val name: String? = null,
    val sortName: String? = null,
    val localizedName: String? = null,
    val format: Int = 0,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val wordCount: Long = 0,
    val userRating: Float = 0f,
    val primaryColor: String? = null,
    val created: String? = null,
    val lastChapterAddedUtc: String? = null,
    val latestReadDate: String? = null,
)

@Serializable
data class VolumeDto(
    val id: Int,
    val seriesId: Int = 0,
    val name: String? = null,
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val lastModifiedUtc: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class MangaFileDto(
    val bytes: Long = 0,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val volumeId: Int = 0,
    val range: String? = null,
    val title: String? = null,
    val titleName: String? = null,
    val minNumber: Float = 0f,
    val sortOrder: Float = 0f,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val isSpecial: Boolean = false,
    val format: Int = 0,
    val wordCount: Long = 0,
    val files: List<MangaFileDto> = emptyList(),
    val releaseDate: String? = null,
    val lastModifiedUtc: String? = null,
    val volumeTitle: String? = null,
)

/** `GET api/Series/series-detail`. Volumes carry their chapters; specials/chapters are flattened views of the same. */
@Serializable
data class SeriesDetailDto(
    val volumes: List<VolumeDto> = emptyList(),
    val specials: List<ChapterDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

/** `POST api/Series/recently-updated-series` item: a series with [count] new chapters. */
@Serializable
data class GroupedSeriesDto(
    val seriesId: Int,
    val seriesName: String? = null,
    val libraryId: Int = 0,
    val format: Int = 0,
    val chapterId: Int = 0,
    val count: Int = 0,
)

/** `GET api/Stream/dashboard` item. */
@Serializable
data class DashboardStreamDto(
    val id: Int,
    val name: String? = null,
    val order: Int = 0,
    /** `DashboardStreamType`: 1 OnDeck, 2 RecentlyUpdated, 3 NewlyAdded, 4 SmartFilter. */
    val streamType: Int = 0,
    val visible: Boolean = true,
    val smartFilterEncoded: String? = null,
)

@Serializable
data class SeriesFilterStatementDto(
    /** Kavita `FilterComparison`. */
    val comparison: Int,
    /** Kavita `SeriesFilterField`. */
    val field: Int,
    val value: String,
)

@Serializable
data class SeriesSortOptionDto(
    /** Kavita `SeriesSortField`. */
    val sortField: Int,
    val isAscending: Boolean = true,
)

/** `POST api/Series/v2` body. */
@Serializable
data class SeriesFilterV2Dto(
    val statements: List<SeriesFilterStatementDto> = emptyList(),
    /** Kavita `FilterCombination`: 1 = And. */
    val combination: Int = 1,
    val sortOptions: SeriesSortOptionDto? = null,
    val limitTo: Int = 0,
) {
    companion object {
        private const val FIELD_LIBRARIES = 19
        private const val COMPARISON_EQUAL = 0
        private const val SORT_NAME = 1

        /** All series of one library, by sort name. */
        fun library(libraryId: Int) =
            SeriesFilterV2Dto(
                statements = listOf(SeriesFilterStatementDto(COMPARISON_EQUAL, FIELD_LIBRARIES, libraryId.toString())),
                sortOptions = SeriesSortOptionDto(SORT_NAME),
            )
    }
}

/**
 * Kavita times → epoch millis. `*Utc` fields are ISO instants; the others are server-local without an offset and are
 * read as UTC (only used for display/sorting). Unparseable or missing → 0.
 */
fun kavitaTime(value: String?): Long {
    if (value.isNullOrBlank()) return 0
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrDefault(0)
        .coerceAtLeast(0)
}
