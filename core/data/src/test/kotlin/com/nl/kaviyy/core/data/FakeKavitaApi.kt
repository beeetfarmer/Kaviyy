package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.CollectionDto
import com.nl.kaviyy.core.network.DashboardStreamDto
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.GenreTagDto
import com.nl.kaviyy.core.network.GroupedSeriesDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.LanguageDto
import com.nl.kaviyy.core.network.LibraryDto
import com.nl.kaviyy.core.network.MetadataValueDto
import com.nl.kaviyy.core.network.PersonDto
import com.nl.kaviyy.core.network.SeriesDetailDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.SideNavStreamDto
import com.nl.kaviyy.core.network.SmartFilterDto
import kotlinx.serialization.json.JsonObject

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

    override suspend fun continuePoint(seriesId: Int): ChapterDto = error("unused")

    override suspend fun dashboard(visibleOnly: Boolean): List<DashboardStreamDto> = error("unused")

    override suspend fun genres(libraryIds: String): List<GenreTagDto> = error("unused")

    override suspend fun publicationStatuses(libraryIds: String): List<MetadataValueDto> = error("unused")

    override suspend fun tags(libraryIds: String): List<GenreTagDto> = error("unused")

    override suspend fun languages(libraryIds: String): List<LanguageDto> = error("unused")

    override suspend fun ageRatings(libraryIds: String): List<MetadataValueDto> = error("unused")

    override suspend fun peopleByRole(role: Int): List<PersonDto> = error("unused")

    override suspend fun collections(): List<CollectionDto> = error("unused")

    override suspend fun smartFilters(): List<SmartFilterDto> = error("unused")

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
