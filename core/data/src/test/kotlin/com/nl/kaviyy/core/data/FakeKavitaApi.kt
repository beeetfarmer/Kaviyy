package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.network.AnnotationDto
import com.nl.kaviyy.core.network.BookmarkDto
import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.ChapterInfoDto
import com.nl.kaviyy.core.network.CollectionAddDto
import com.nl.kaviyy.core.network.CollectionDto
import com.nl.kaviyy.core.network.CollectionRemoveDto
import com.nl.kaviyy.core.network.DashboardStreamDto
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.GenreTagDto
import com.nl.kaviyy.core.network.GroupedSeriesDto
import com.nl.kaviyy.core.network.HourEstimateRangeDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.LanguageDto
import com.nl.kaviyy.core.network.LibraryDto
import com.nl.kaviyy.core.network.MarkReadDto
import com.nl.kaviyy.core.network.MarkVolumeReadDto
import com.nl.kaviyy.core.network.MarkVolumesReadDto
import com.nl.kaviyy.core.network.MetadataValueDto
import com.nl.kaviyy.core.network.PersonDto
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.network.RefreshSeriesDto
import com.nl.kaviyy.core.network.RelatedSeriesDto
import com.nl.kaviyy.core.network.SearchResultGroupDto
import com.nl.kaviyy.core.network.SeriesDetailDto
import com.nl.kaviyy.core.network.SeriesDetailPlusDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.SeriesMetadataDto
import com.nl.kaviyy.core.network.SideNavStreamDto
import com.nl.kaviyy.core.network.SmartFilterDto
import com.nl.kaviyy.core.network.UpdateRatingDto
import com.nl.kaviyy.core.network.UpdateUserReviewDto
import com.nl.kaviyy.core.network.UpdateWantToReadDto
import com.nl.kaviyy.core.network.UserReviewDto
import kotlinx.serialization.json.JsonObject
import retrofit2.Response

/** Base fake: every call fails loudly unless a test overrides it. */
open class FakeKavitaApi : KavitaApi {
    override suspend fun libraries(): List<LibraryDto> = error("unused")

    override suspend fun series(
        filter: SeriesFilterV2Dto,
        pageNumber: Int,
        pageSize: Int,
    ): List<SeriesDto> = error("unused")

    override suspend fun series(seriesId: Int): SeriesDto = error("unused")

    override suspend fun onDeck(
        pageNumber: Int,
        pageSize: Int,
        libraryId: Int,
    ): List<SeriesDto> = error("unused")

    override suspend fun recentlyUpdated(
        pageNumber: Int,
        pageSize: Int,
    ): List<GroupedSeriesDto> = error("unused")

    override suspend fun recentlyAdded(
        filter: SeriesFilterV2Dto,
        pageNumber: Int,
        pageSize: Int,
    ): List<SeriesDto> = error("unused")

    override suspend fun continuePoint(seriesId: Int): ChapterDto = ChapterDto(0, volumeId = 0)

    override suspend fun dashboard(visibleOnly: Boolean): List<DashboardStreamDto> = error("unused")

    override suspend fun genres(libraryIds: String): List<GenreTagDto> = error("unused")

    override suspend fun publicationStatuses(libraryIds: String): List<MetadataValueDto> = error("unused")

    override suspend fun tags(libraryIds: String): List<GenreTagDto> = error("unused")

    override suspend fun languages(libraryIds: String): List<LanguageDto> = error("unused")

    override suspend fun ageRatings(libraryIds: String): List<MetadataValueDto> = error("unused")

    override suspend fun peopleByRole(role: Int): List<PersonDto> = error("unused")

    override suspend fun collections(sortByLastModified: Boolean): List<CollectionDto> = error("unused")

    override suspend fun smartFilters(): List<SmartFilterDto> = error("unused")

    override suspend fun seriesMetadata(seriesId: Int): SeriesMetadataDto = SeriesMetadataDto()

    override suspend fun allRelated(seriesId: Int): RelatedSeriesDto = error("unused")

    override suspend fun person(name: String): PersonDto = error("unused")

    override suspend fun chapterInfo(
        chapterId: Int,
        includeDimensions: Boolean,
        extractPdf: Boolean,
    ): ChapterInfoDto = error("unused")

    override suspend fun nextChapter(
        seriesId: Int,
        volumeId: Int,
        chapterId: Int,
    ): Int = error("unused")

    override suspend fun prevChapter(
        seriesId: Int,
        volumeId: Int,
        chapterId: Int,
    ): Int = error("unused")

    override suspend fun downloadChapter(chapterId: Int): okhttp3.ResponseBody = error("unused")

    override suspend fun annotations(chapterId: Int): List<AnnotationDto> = error("unused")

    override suspend fun createAnnotation(body: AnnotationDto): AnnotationDto = error("unused")

    override suspend fun updateAnnotation(body: AnnotationDto): AnnotationDto = error("unused")

    override suspend fun deleteAnnotation(annotationId: Int): Unit = error("unused")

    override suspend fun wantToRead(
        filter: SeriesFilterV2Dto,
        pageNumber: Int,
        pageSize: Int,
    ): List<SeriesDto> = error("unused")

    override suspend fun collectionsWithSeries(
        seriesId: Int,
        ownedOnly: Boolean,
    ): List<CollectionDto> = error("unused")

    override suspend fun addToCollection(body: CollectionAddDto): Unit = error("unused")

    override suspend fun removeFromCollection(body: CollectionRemoveDto): Unit = error("unused")

    override suspend fun updateCollection(body: CollectionDto): CollectionDto = error("unused")

    override suspend fun deleteCollection(tagId: Int): Unit = error("unused")

    override suspend fun progress(chapterId: Int): ProgressDto = error("unused")

    override suspend fun saveProgress(body: ProgressDto): Unit = error("unused")

    override suspend fun chapterBookmarks(chapterId: Int): List<BookmarkDto> = error("unused")

    override suspend fun bookmark(body: BookmarkDto): Unit = error("unused")

    override suspend fun unbookmark(body: BookmarkDto): Unit = error("unused")

    override suspend fun search(
        query: String,
        includeChapterAndFiles: Boolean,
    ): SearchResultGroupDto = error("unused")

    override suspend fun seriesForChapter(chapterId: Int): SeriesDto = error("unused")

    override suspend fun seriesForFile(fileId: Int): SeriesDto = error("unused")

    override suspend fun personRoles(personId: Int): List<Int> = error("unused")

    override suspend fun refreshSeriesMetadata(body: RefreshSeriesDto): Unit = error("unused")

    override suspend fun scanSeries(body: RefreshSeriesDto): Unit = error("unused")

    override suspend fun rawSeriesMetadata(seriesId: Int): JsonObject = error("unused")

    override suspend fun updateSeriesMetadata(body: JsonObject): Unit = error("unused")

    override suspend fun rateSeries(body: UpdateRatingDto): Unit = error("unused")

    override suspend fun saveSeriesReview(body: UpdateUserReviewDto): UserReviewDto = error("unused")

    override suspend fun deleteSeriesReview(seriesId: Int): Unit = error("unused")

    override suspend fun seriesDetailPlus(
        seriesId: Int,
        libraryType: Int,
    ): Response<SeriesDetailPlusDto> = error("unused")

    override suspend fun markSeriesRead(body: MarkReadDto): Unit = error("unused")

    override suspend fun markSeriesUnread(body: MarkReadDto): Unit = error("unused")

    override suspend fun markVolumeRead(body: MarkVolumeReadDto): Unit = error("unused")

    override suspend fun markVolumeUnread(body: MarkVolumeReadDto): Unit = error("unused")

    override suspend fun markMultipleRead(body: MarkVolumesReadDto): Unit = error("unused")

    override suspend fun markMultipleUnread(body: MarkVolumesReadDto): Unit = error("unused")

    override suspend fun addWantToRead(body: UpdateWantToReadDto): Unit = error("unused")

    override suspend fun removeWantToRead(body: UpdateWantToReadDto): Unit = error("unused")

    override suspend fun timeLeft(seriesId: Int): HourEstimateRangeDto = HourEstimateRangeDto()

    override suspend fun isWantToRead(seriesId: Int): Boolean = false

    override suspend fun saveSmartFilter(filter: SeriesFilterV2Dto): Unit = error("unused")

    override suspend fun deleteSmartFilter(filterId: Int): Unit = error("unused")

    override suspend fun sideNav(visibleOnly: Boolean): List<SideNavStreamDto> = error("unused")

    override suspend fun addDashboardStream(smartFilterId: Int): DashboardStreamDto = error("unused")

    override suspend fun removeDashboardStream(dashboardStreamId: Int): Unit = error("unused")

    override suspend fun addSideNavStream(smartFilterId: Int): SideNavStreamDto = error("unused")

    override suspend fun removeSideNavStream(sideNavStreamId: Int): Unit = error("unused")

    override suspend fun decodeFilter(body: DecodeFilterDto): JsonObject = error("unused")

    override suspend fun seriesByFilter(
        filter: JsonObject,
        pageNumber: Int,
        pageSize: Int,
    ): List<SeriesDto> = error("unused")

    override suspend fun readingProfile(
        libraryId: Int,
        seriesId: Int,
    ): JsonObject = error("unused")

    override suspend fun saveSeriesReadingProfile(
        libraryId: Int,
        seriesId: Int,
        profile: JsonObject,
    ): JsonObject = error("unused")

    override suspend fun seriesDetail(seriesId: Int): SeriesDetailDto = error("unused")
}
