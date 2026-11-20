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
    val smartFilterId: Int? = null,
)

/** `GET api/Stream/sidenav` item (only what pinning needs). */
@Serializable
data class SideNavStreamDto(
    val id: Int,
    val name: String? = null,
    val visible: Boolean = true,
    val smartFilterId: Int? = null,
)

/** `GET api/Filter` item. [filter] is Kavita's encoded filter string. */
@Serializable
data class SmartFilterDto(
    val id: Int,
    val name: String? = null,
    val filter: String? = null,
    /** Kavita `FilterEntityType`: 0 = Series. */
    val entityType: Int = 0,
)

/** `GET api/Metadata/genres` / `tags` item (GenreTagDto / TagDto: id + title). */
@Serializable
data class GenreTagDto(
    val id: Int,
    val title: String? = null,
)

/** `GET api/Metadata/publication-status` item (the API reuses `AgeRatingDto`: value + title). */
@Serializable
data class MetadataValueDto(
    val value: Int,
    val title: String? = null,
)

@Serializable
data class LanguageDto(
    val isoCode: String? = null,
    val title: String? = null,
)

@Serializable
data class PersonDto(
    val id: Int,
    val name: String? = null,
)

@Serializable
data class CollectionDto(
    val id: Int,
    val title: String? = null,
)

@Serializable
data class DecodeFilterDto(
    val encodedFilter: String,
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
    /** Set (with [name]) only when saving a smart filter. */
    val id: Int = 0,
    val name: String? = null,
    val statements: List<SeriesFilterStatementDto> = emptyList(),
    /** Kavita `FilterCombination`: 1 = And. */
    val combination: Int = 1,
    val sortOptions: SeriesSortOptionDto? = null,
    val limitTo: Int = 0,
)

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

/** `GET api/Series/metadata` (only what screen 04 shows). */
@Serializable
data class SeriesMetadataDto(
    val summary: String? = null,
    val releaseYear: Int = 0,
    val ageRating: Int = 0,
    val publicationStatus: Int = 0,
    val genres: List<GenreTagDto> = emptyList(),
    val tags: List<GenreTagDto> = emptyList(),
    val writers: List<PersonDto> = emptyList(),
    /** Comma-separated URLs. */
    val webLinks: String? = null,
)

/** `GET api/Reader/time-left`. */
@Serializable
data class HourEstimateRangeDto(
    val minHours: Int = 0,
    val maxHours: Int = 0,
    val avgHours: Float = 0f,
)

/** `GET api/Series/all-related`: one list per Kavita `RelationKind`. */
@Serializable
data class RelatedSeriesDto(
    val sequels: List<SeriesDto> = emptyList(),
    val prequels: List<SeriesDto> = emptyList(),
    val spinOffs: List<SeriesDto> = emptyList(),
    val adaptations: List<SeriesDto> = emptyList(),
    val sideStories: List<SeriesDto> = emptyList(),
    val characters: List<SeriesDto> = emptyList(),
    val contains: List<SeriesDto> = emptyList(),
    val others: List<SeriesDto> = emptyList(),
    val alternativeSettings: List<SeriesDto> = emptyList(),
    val alternativeVersions: List<SeriesDto> = emptyList(),
    val doujinshis: List<SeriesDto> = emptyList(),
    val parent: List<SeriesDto> = emptyList(),
    val editions: List<SeriesDto> = emptyList(),
    val annuals: List<SeriesDto> = emptyList(),
)

// Read-state bodies: no defaults, so every field is always sent (the API JSON omits defaults).

/** `api/Reader/mark-read|mark-unread`. */
@Serializable
data class MarkReadDto(
    val seriesId: Int,
)

/** `api/Reader/mark-volume-read|mark-volume-unread`. */
@Serializable
data class MarkVolumeReadDto(
    val seriesId: Int,
    val volumeId: Int,
)

/** `api/Reader/mark-multiple-read|mark-multiple-unread` (used for single chapters: there is no chapter-unread call). */
@Serializable
data class MarkVolumesReadDto(
    val seriesId: Int,
    val volumeIds: List<Int>,
    val chapterIds: List<Int>,
)

/** `api/want-to-read/add-series|remove-series`. */
@Serializable
data class UpdateWantToReadDto(
    val seriesIds: List<Int>,
)
