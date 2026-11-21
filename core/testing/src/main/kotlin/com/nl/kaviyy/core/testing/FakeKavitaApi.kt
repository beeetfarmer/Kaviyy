@file:Suppress("TooManyFunctions", "MagicNumber") // a fake of the whole API

package com.nl.kaviyy.core.testing

import com.nl.kaviyy.core.network.AccountDto
import com.nl.kaviyy.core.network.AnnotationDto
import com.nl.kaviyy.core.network.AuthKeyDto
import com.nl.kaviyy.core.network.AuthKeyRequestDto
import com.nl.kaviyy.core.network.BookmarkDto
import com.nl.kaviyy.core.network.CblFinalizeDto
import com.nl.kaviyy.core.network.CblImportSummaryDto
import com.nl.kaviyy.core.network.CblSavedFileDto
import com.nl.kaviyy.core.network.CblValidateDto
import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.ChapterInfoDto
import com.nl.kaviyy.core.network.CollectionAddDto
import com.nl.kaviyy.core.network.CollectionDto
import com.nl.kaviyy.core.network.CollectionRemoveDto
import com.nl.kaviyy.core.network.CreateEmailDeviceDto
import com.nl.kaviyy.core.network.CreateReadingListDto
import com.nl.kaviyy.core.network.DashboardStreamDto
import com.nl.kaviyy.core.network.DateStatCountDto
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.EmailChangeDto
import com.nl.kaviyy.core.network.EmailDeviceDto
import com.nl.kaviyy.core.network.GenreTagDto
import com.nl.kaviyy.core.network.GroupedSeriesDto
import com.nl.kaviyy.core.network.HourEstimateRangeDto
import com.nl.kaviyy.core.network.IntStatCountDto
import com.nl.kaviyy.core.network.JobDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.LanguageDto
import com.nl.kaviyy.core.network.LibraryDto
import com.nl.kaviyy.core.network.MalAccountDto
import com.nl.kaviyy.core.network.MarkReadDto
import com.nl.kaviyy.core.network.MarkVolumeReadDto
import com.nl.kaviyy.core.network.MarkVolumesReadDto
import com.nl.kaviyy.core.network.MetadataValueDto
import com.nl.kaviyy.core.network.PersonDto
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.network.ReadingListDto
import com.nl.kaviyy.core.network.ReadingListItemDto
import com.nl.kaviyy.core.network.ReadingListPositionDto
import com.nl.kaviyy.core.network.ReadingListSeriesDto
import com.nl.kaviyy.core.network.ReadingPaceDto
import com.nl.kaviyy.core.network.RefreshSeriesDto
import com.nl.kaviyy.core.network.RelatedSeriesDto
import com.nl.kaviyy.core.network.ResetPasswordDto
import com.nl.kaviyy.core.network.ScrobbleHoldDto
import com.nl.kaviyy.core.network.SearchResultGroupDto
import com.nl.kaviyy.core.network.SendSeriesToEmailDeviceDto
import com.nl.kaviyy.core.network.SendToEmailDeviceDto
import com.nl.kaviyy.core.network.SeriesDetailDto
import com.nl.kaviyy.core.network.SeriesDetailPlusDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.SeriesMetadataDto
import com.nl.kaviyy.core.network.ServerInfoSlimDto
import com.nl.kaviyy.core.network.SideNavStreamDto
import com.nl.kaviyy.core.network.SmartFilterDto
import com.nl.kaviyy.core.network.StringBreakdownDto
import com.nl.kaviyy.core.network.UpdateEmailDto
import com.nl.kaviyy.core.network.UpdateRatingDto
import com.nl.kaviyy.core.network.UpdateReadingListDto
import com.nl.kaviyy.core.network.UpdateUserReviewDto
import com.nl.kaviyy.core.network.UpdateWantToReadDto
import com.nl.kaviyy.core.network.UserReadStatsDto
import com.nl.kaviyy.core.network.UserReviewDto
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
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

    override suspend fun serverInfo(): ServerInfoSlimDto = error("unused")

    override suspend fun jobs(): List<JobDto> = error("unused")

    override suspend fun scanLibrary(
        libraryId: Int,
        force: Boolean,
    ): Unit = error("unused")

    override suspend fun scanAll(force: Boolean): Unit = error("unused")

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

    override suspend fun downloadChapterFrom(
        chapterId: Int,
        range: String?,
    ): Response<okhttp3.ResponseBody> = error("unused")

    override suspend fun pageImage(
        chapterId: Int,
        page: Int,
        extractPdf: Boolean,
    ): okhttp3.ResponseBody = error("unused")

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

    override suspend fun readingLists(
        pageNumber: Int,
        pageSize: Int,
        includePromoted: Boolean,
        sortByLastModified: Boolean,
    ): List<ReadingListDto> = error("unused")

    override suspend fun readingListItems(readingListId: Int): List<ReadingListItemDto> = error("unused")

    override suspend fun readingList(readingListId: Int): ReadingListDto = error("unused")

    override suspend fun account(): AccountDto = error("unused")

    val accountCalls = mutableListOf<String>()
    var emailResult = EmailChangeDto(emailSent = true)

    override suspend fun changePassword(body: ResetPasswordDto) {
        accountCalls += "password ${body.userName} ${body.oldPassword}->${body.password}"
    }

    override suspend fun changeEmail(body: UpdateEmailDto) = emailResult.also { accountCalls += "email ${body.email}" }

    var keys = listOf<AuthKeyDto>()
    val keyCalls = mutableListOf<String>()

    override suspend fun authKeys() = keys

    override suspend fun createAuthKey(body: AuthKeyRequestDto) =
        AuthKeyDto(5, "N".repeat(body.keyLength), body.name, expiresAtUtc = body.expiresUtc).also {
            keyCalls += "create ${body.name} ${body.keyLength} ${body.expiresUtc}"
        }

    override suspend fun rotateAuthKey(
        id: Int,
        body: AuthKeyRequestDto,
    ) = AuthKeyDto(id, "R".repeat(body.keyLength), body.name).also { keyCalls += "rotate $id" }

    override suspend fun deleteAuthKey(id: Int) {
        keyCalls += "delete $id"
    }

    var licensed = true
    var aniList = ""
    var malUser: String? = null
    var expired = setOf<Int>()
    var holds = listOf<ScrobbleHoldDto>()
    var scrobbleCalls = 0

    override suspend fun validLicense() = licensed.also { scrobbleCalls++ }

    override suspend fun aniListToken() = "\"$aniList\"".toResponseBody().also { scrobbleCalls++ }

    override suspend fun malAccount() = MalAccountDto(malUser).also { scrobbleCalls++ }

    override suspend fun scrobbleTokenExpired(provider: Int) = (provider in expired).also { scrobbleCalls++ }

    override suspend fun scrobbleHolds() = holds.also { scrobbleCalls++ }

    var deviceList = listOf<EmailDeviceDto>()
    val deviceCalls = mutableListOf<String>()

    override suspend fun devices() = deviceList

    override suspend fun createDevice(device: CreateEmailDeviceDto) =
        EmailDeviceDto(9, device.name, device.emailAddress, device.platform).also {
            deviceCalls +=
                "create ${device.name}"
        }

    override suspend fun updateDevice(device: EmailDeviceDto) = device.also { deviceCalls += "update ${device.id}" }

    override suspend fun deleteDevice(deviceId: Int) {
        deviceCalls += "delete $deviceId"
    }

    override suspend fun sendToDevice(body: SendToEmailDeviceDto) {
        deviceCalls += "send ${body.chapterIds} to ${body.deviceId}"
    }

    override suspend fun sendSeriesToDevice(body: SendSeriesToEmailDeviceDto) {
        deviceCalls += "send series ${body.seriesId} to ${body.deviceId}"
    }

    override suspend fun readingPace(
        userId: Int,
        startDate: String,
        endDate: String,
        timeZoneId: String,
    ): ReadingPaceDto = error("unused")

    override suspend fun readingCounts(
        userId: Int,
        startDate: String,
        endDate: String,
        timeZoneId: String,
    ): List<DateStatCountDto> = error("unused")

    override suspend fun userReadStats(userId: Int): UserReadStatsDto = error("unused")

    override suspend fun pagesPerYear(userId: Int): List<IntStatCountDto> = error("unused")

    override suspend fun dayBreakdown(userId: Int): List<IntStatCountDto> = error("unused")

    override suspend fun genreBreakdown(userId: Int): StringBreakdownDto = error("unused")

    override suspend fun saveCblFile(cblFile: MultipartBody.Part): CblSavedFileDto = error("unused")

    override suspend fun validateCbl(body: CblValidateDto): CblImportSummaryDto = error("unused")

    override suspend fun finalizeCbl(body: CblFinalizeDto): CblImportSummaryDto = error("unused")

    override suspend fun createReadingList(body: CreateReadingListDto): ReadingListDto = error("unused")

    override suspend fun updateReadingList(body: UpdateReadingListDto): Unit = error("unused")

    override suspend fun deleteReadingList(readingListId: Int): Unit = error("unused")

    override suspend fun moveReadingListItem(body: ReadingListPositionDto): Unit = error("unused")

    override suspend fun deleteReadingListItem(body: ReadingListPositionDto): Unit = error("unused")

    override suspend fun addSeriesToReadingList(body: ReadingListSeriesDto): Unit = error("unused")

    override suspend fun readingListsWithSeries(seriesId: Int): List<ReadingListDto> = error("unused")

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

    var profiles = listOf<JsonObject>()
    val savedProfiles = mutableListOf<JsonObject>()

    override suspend fun readingProfiles() = profiles

    override suspend fun updateReadingProfile(profile: JsonObject) = profile.also { savedProfiles += it }

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
