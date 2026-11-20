package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.GroupedSeriesDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.LibraryDto
import com.nl.kaviyy.core.network.SeriesDetailDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
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
