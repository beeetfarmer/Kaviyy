package com.nl.kaviyy.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    val description: String? = null,
)

@Serializable
data class CollectionDto(
    val id: Int,
    val title: String? = null,
    val summary: String? = null,
    val promoted: Boolean = false,
    val itemCount: Int = 0,
    val owner: String? = null,
    /** Kavita `ScrobbleProvider`; non-zero = synced from an external list. */
    val source: Int = 0,
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
    val pencillers: List<PersonDto> = emptyList(),
    val inkers: List<PersonDto> = emptyList(),
    val colorists: List<PersonDto> = emptyList(),
    val letterers: List<PersonDto> = emptyList(),
    val coverArtists: List<PersonDto> = emptyList(),
    val editors: List<PersonDto> = emptyList(),
    val publishers: List<PersonDto> = emptyList(),
    val characters: List<PersonDto> = emptyList(),
    val translators: List<PersonDto> = emptyList(),
    val imprints: List<PersonDto> = emptyList(),
    val teams: List<PersonDto> = emptyList(),
    val locations: List<PersonDto> = emptyList(),
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

/** `POST api/Rating/series` (0–5; 0 clears). */
@Serializable
data class UpdateRatingDto(
    val seriesId: Int,
    val userRating: Float,
)

/** `POST api/Review/series`. */
@Serializable
data class UpdateUserReviewDto(
    val seriesId: Int,
    val body: String,
)

/** A community score (Kavita+). [provider] = Kavita `ScrobbleProvider`. */
@Serializable
data class RatingDto(
    val averageScore: Int = 0,
    val favoriteCount: Int = 0,
    val provider: Int = 0,
    val providerUrl: String? = null,
)

@Serializable
data class UserReviewDto(
    val seriesId: Int = 0,
    val tagline: String? = null,
    /** HTML; prefer [bodyJustText]. */
    val body: String? = null,
    val bodyJustText: String? = null,
    val username: String? = null,
    val rating: Float = 0f,
    val score: Int = 0,
    val provider: Int = 0,
    val isExternal: Boolean = false,
    val siteUrl: String? = null,
)

/** `GET api/Metadata/series-detail-plus` (reviews + Kavita+ ratings; null/empty without data). */
@Serializable
data class SeriesDetailPlusDto(
    val reviews: List<UserReviewDto>? = null,
    val ratings: List<RatingDto>? = null,
)

/** `POST api/Series/refresh-metadata|scan` (admin). No defaults: always sent. */
@Serializable
data class RefreshSeriesDto(
    val libraryId: Int,
    val seriesId: Int,
    val forceUpdate: Boolean,
)

@Serializable
data class SearchSeriesDto(
    val seriesId: Int,
    val name: String? = null,
    val localizedName: String? = null,
    val libraryName: String? = null,
    val volumeCount: Int = 0,
)

/** A collection or reading list hit. */
@Serializable
data class SearchListDto(
    val id: Int,
    val title: String? = null,
    val itemCount: Int = 0,
)

@Serializable
data class SearchFileDto(
    val id: Int,
    /** Server path: show the file name only. */
    val filePath: String? = null,
)

@Serializable
data class SearchBookmarkDto(
    val seriesId: Int,
    val chapterId: Int = 0,
    val seriesName: String? = null,
    val localizedSeriesName: String? = null,
)

/** `GET api/Search/search`. */
@Serializable
data class SearchResultGroupDto(
    val series: List<SearchSeriesDto> = emptyList(),
    val collections: List<SearchListDto> = emptyList(),
    val readingLists: List<SearchListDto> = emptyList(),
    val persons: List<PersonDto> = emptyList(),
    val genres: List<GenreTagDto> = emptyList(),
    val tags: List<GenreTagDto> = emptyList(),
    val files: List<SearchFileDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
    val bookmarks: List<SearchBookmarkDto> = emptyList(),
)

/** `GET api/Reader/chapter-info`. Numbers are strings; Kavita uses "-100000" for "none" (loose leaf / default). */
@Serializable
data class ChapterInfoDto(
    val seriesId: Int = 0,
    val volumeId: Int = 0,
    val libraryId: Int = 0,
    val seriesName: String? = null,
    /** Kavita `MangaFormat`: 0 image, 1 archive, 2 unknown, 3 EPUB, 4 PDF. */
    val seriesFormat: Int = 0,
    val chapterNumber: String? = null,
    val volumeNumber: String? = null,
    val chapterTitle: String? = null,
    val isSpecial: Boolean = false,
    val pages: Int = 0,
    /** With `includeDimensions=true`. */
    val pageDimensions: List<FileDimensionDto>? = null,
)

@Serializable
data class FileDimensionDto(
    val pageNumber: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val isWide: Boolean = false,
)

/** Page bookmark (request and response). Request fields have no defaults so they are always sent. */
@Serializable
data class BookmarkDto(
    val id: Int,
    val page: Int,
    val volumeId: Int,
    val seriesId: Int,
    val chapterId: Int,
    val imageOffset: Int = 0,
)

/** Read position. `pageNum` = pages read; sending the page count marks the chapter read. */
@Serializable
data class ProgressDto(
    val volumeId: Int,
    val chapterId: Int,
    val pageNum: Int,
    val seriesId: Int,
    val libraryId: Int,
    val bookScrollId: String? = null,
    /** ISO-8601 UTC. */
    val lastModifiedUtc: String? = null,
)

/** EPUB highlight / note. Request fields without defaults are always sent. */
@Serializable
data class AnnotationDto(
    val id: Int,
    val xPath: String,
    val endingXPath: String?,
    val selectedText: String?,
    val comment: String?,
    val context: String?,
    val highlightCount: Int,
    val containsSpoiler: Boolean,
    val pageNumber: Int,
    val selectedSlotIndex: Int,
    val chapterId: Int,
    val volumeId: Int,
    val seriesId: Int,
    val libraryId: Int,
    // TODO(api): the spec requires it; Kavita takes the owner from the token, so 0 is sent. Verify on a server.
    val ownerUserId: Int,
    val chapterTitle: String? = null,
    val createdUtc: String? = null,
    val lastModifiedUtc: String? = null,
)

/** Kavita `CollectionTagBulkAddDto`. */
@Serializable
data class CollectionAddDto(
    val collectionTagId: Int,
    val collectionTagTitle: String,
    val seriesIds: List<Int>,
)

/** Kavita `UpdateSeriesForTagDto`. */
@Serializable
data class CollectionRemoveDto(
    val tag: CollectionDto,
    val seriesIdsToRemove: List<Int>,
)

/** Kavita `ReadingListDto` (the fields we show). */
@Serializable
data class ReadingListDto(
    val id: Int,
    val title: String? = null,
    val summary: String? = null,
    val promoted: Boolean = false,
    val itemCount: Int = 0,
    val startingYear: Int = 0,
    val startingMonth: Int = 0,
    val endingYear: Int = 0,
    val endingMonth: Int = 0,
    val ownerUserName: String? = null,
    val coverImageLocked: Boolean = false,
    /** Passed back untouched on update. */
    val tags: JsonElement? = null,
)

/** Kavita `ReadingListItemDto` (the fields we show). */
@Serializable
data class ReadingListItemDto(
    val id: Int,
    val order: Int = 0,
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int = 0,
    val seriesName: String? = null,
    val seriesFormat: Int = 0,
    val pagesRead: Int = 0,
    val pagesTotal: Int = 0,
    val title: String? = null,
)

/** Kavita `CreateReadingListDto`. */
@Serializable
data class CreateReadingListDto(
    val title: String,
)

/** Kavita `UpdateReadingListDto`; everything but title and summary is passed back as the server sent it. */
@Serializable
data class UpdateReadingListDto(
    val readingListId: Int,
    val title: String,
    val summary: String?,
    val promoted: Boolean,
    val coverImageLocked: Boolean,
    val startingMonth: Int,
    val startingYear: Int,
    val endingMonth: Int,
    val endingYear: Int,
    val tags: JsonElement? = null,
)

/** Kavita `UpdateReadingListPosition` (also the delete-item body). */
@Serializable
data class ReadingListPositionDto(
    val readingListId: Int,
    val readingListItemId: Int,
    val fromPosition: Int = 0,
    val toPosition: Int = 0,
)

/** Kavita `UpdateReadingListBySeriesDto`. */
@Serializable
data class ReadingListSeriesDto(
    val seriesId: Int,
    val readingListId: Int,
)

/** Kavita `CblSavedFileDto`. */
@Serializable
data class CblSavedFileDto(
    val name: String? = null,
    val fileName: String? = null,
    val provider: Int = 0,
    val repoPath: String? = null,
    val downloadUrl: String? = null,
    val sha: String? = null,
)

/** Kavita `CblReValidateRequestDto`. */
@Serializable
data class CblValidateDto(
    val fileName: String,
)

/** Kavita `CblBookResult` (the fields we show). `reason` 8 = matched. */
@Serializable
data class CblBookResultDto(
    val order: Int = 0,
    val series: String? = null,
    val volume: String? = null,
    val number: String? = null,
    val reason: Int = 0,
)

/** Kavita `CblImportSummaryDto`. `success`: 0 fail, 1 partial, 2 success. */
@Serializable
data class CblImportSummaryDto(
    val cblName: String? = null,
    val fileName: String? = null,
    val results: List<CblBookResultDto> = emptyList(),
    val success: Int = 0,
    val successfulInserts: List<CblBookResultDto> = emptyList(),
    val readingListId: Int = 0,
)

/** Kavita `CblImportDecisions`: no manual matches. */
@Serializable
data class CblDecisionsDto(
    val saveAsRemapRules: Boolean = false,
)

/** Kavita `CblFinalizeRequestDto`; the file fields come back from [CblSavedFileDto]. */
@Serializable
data class CblFinalizeDto(
    val fileName: String,
    val decisions: CblDecisionsDto,
    val provider: Int,
    val repoPath: String?,
    val downloadUrl: String?,
    val sha: String?,
    val promote: Boolean = false,
)

/** Kavita `UserDto`: the profile fields only. */
@Serializable
data class AccountDto(
    val id: Int,
    val username: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val kavitaVersion: String? = null,
)

/** Kavita `ReadingPaceDto`. */
@Serializable
data class ReadingPaceDto(
    val hoursRead: Int = 0,
    val pagesRead: Int = 0,
    val wordsRead: Long = 0,
    val booksRead: Int = 0,
    val comicsRead: Int = 0,
)

/** Kavita `DateTimeStatCountWithFormat`: [value] is an ISO date-time. */
@Serializable
data class DateStatCountDto(
    val value: String? = null,
    val count: Int = 0,
)

/** Kavita `UserReadStatistics`; [timeSpentReading] is in hours. */
@Serializable
data class UserReadStatsDto(
    val totalPagesRead: Long = 0,
    val totalWordsRead: Long = 0,
    val timeSpentReading: Long = 0,
    val avgHoursPerWeekSpentReading: Double = 0.0,
)

/** Kavita `Int32StatCount` / `DayOfWeekStatCount`. */
@Serializable
data class IntStatCountDto(
    val value: Int = 0,
    val count: Long = 0,
)

/** Kavita `StringStatCount`. */
@Serializable
data class StringStatCountDto(
    val value: String? = null,
    val count: Long = 0,
)

/** Kavita `StringBreakDownDto`. */
@Serializable
data class StringBreakdownDto(
    val data: List<StringStatCountDto> = emptyList(),
)

/** Kavita `EmailDeviceDto`; platform 0 Custom, 1 PocketBook, 2 Kindle, 3 Kobo. Also the update body. */
@Serializable
data class EmailDeviceDto(
    val id: Int,
    val name: String? = null,
    val emailAddress: String? = null,
    val platform: Int = 0,
)

@Serializable
data class CreateEmailDeviceDto(
    val name: String,
    val platform: Int,
    val emailAddress: String,
)

@Serializable
data class SendToEmailDeviceDto(
    val deviceId: Int,
    val chapterIds: List<Int>,
)

@Serializable
data class SendSeriesToEmailDeviceDto(
    val deviceId: Int,
    val seriesId: Int,
)

/** Kavita `MalUserInfoDto`, username only. */
@Serializable
data class MalAccountDto(
    val username: String? = null,
)

@Serializable
data class ScrobbleHoldDto(
    val seriesId: Int,
    val seriesName: String? = null,
)

@Serializable
data class ResetPasswordDto(
    val userName: String,
    val password: String,
    val oldPassword: String,
) {
    override fun toString() = "ResetPasswordDto(userName=$userName, <redacted>)"
}

@Serializable
data class UpdateEmailDto(
    val email: String,
    val password: String,
) {
    override fun toString() = "UpdateEmailDto(<redacted>)"
}

/** Kavita `InviteUserResponse`; `emailLink` (holds a token) is deliberately not decoded. */
@Serializable
data class EmailChangeDto(
    val emailSent: Boolean = false,
    val invalidEmail: Boolean = false,
)

/** Kavita `AuthKeyDto`; provider 0 = User, 1 = System (managed by the server). */
@Serializable
data class AuthKeyDto(
    val id: Int,
    val key: String? = null,
    val name: String? = null,
    val createdAtUtc: String? = null,
    val expiresAtUtc: String? = null,
    val lastAccessedAtUtc: String? = null,
    val provider: Int = 0,
) {
    override fun toString() = "AuthKeyDto(id=$id, name=$name, <redacted>)"
}

/** Kavita `RotateAuthKeyRequestDto`: keyLength 8–32; expiresUtc ISO instant or null (never). */
@Serializable
data class AuthKeyRequestDto(
    val keyLength: Int,
    val name: String,
    val expiresUtc: String? = null,
)
