package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.appResult
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** ponytail: one page of reading lists; page further if someone has more. */
const val READING_LISTS_PAGE = 200

/** Kavita endpoints we use, verified against api/openapi.json. Add methods only when a feature needs them. */
@Suppress("TooManyFunctions") // one function per endpoint, like a DAO
interface KavitaApi {
    @GET("api/Library/libraries")
    suspend fun libraries(): List<LibraryDto>

    @POST("api/Series/v2")
    suspend fun series(
        @Body filter: SeriesFilterV2Dto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): List<SeriesDto>

    /** The user's Want to Read series, narrowed by [filter]. */
    @POST("api/want-to-read/v2")
    suspend fun wantToRead(
        @Body filter: SeriesFilterV2Dto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): List<SeriesDto>

    @GET("api/Series/{seriesId}")
    suspend fun series(
        @Path("seriesId") seriesId: Int,
    ): SeriesDto

    /** Genres in the given libraries (comma-separated ids). */
    @GET("api/Metadata/genres")
    suspend fun genres(
        @Query("libraryIds") libraryIds: String? = null,
    ): List<GenreTagDto>

    /** Publication statuses present in the given libraries. */
    @GET("api/Metadata/publication-status")
    suspend fun publicationStatuses(
        @Query("libraryIds") libraryIds: String,
    ): List<MetadataValueDto>

    @GET("api/Metadata/tags")
    suspend fun tags(
        @Query("libraryIds") libraryIds: String? = null,
    ): List<GenreTagDto>

    @GET("api/Metadata/languages")
    suspend fun languages(
        @Query("libraryIds") libraryIds: String,
    ): List<LanguageDto>

    @GET("api/Metadata/age-ratings")
    suspend fun ageRatings(
        @Query("libraryIds") libraryIds: String,
    ): List<MetadataValueDto>

    /** People with a Kavita `PersonRole`. */
    @GET("api/Metadata/people-by-role")
    suspend fun peopleByRole(
        @Query("role") role: Int,
    ): List<PersonDto>

    /** Changes the signed-in user's password (oldPassword required for non-admins). */
    @POST("api/Account/reset-password")
    suspend fun changePassword(
        @Body body: ResetPasswordDto,
    )

    /** Starts an e-mail change; the server mails a confirmation link. */
    @POST("api/Account/update/email")
    suspend fun changeEmail(
        @Body body: UpdateEmailDto,
    ): EmailChangeDto

    /** The user's Auth Keys (the response includes each full key: keep only a hint). */
    @GET("api/Account/auth-keys")
    suspend fun authKeys(): List<AuthKeyDto>

    @POST("api/Account/create-auth-key")
    suspend fun createAuthKey(
        @Body body: AuthKeyRequestDto,
    ): AuthKeyDto

    /** Replaces the key's secret; the old one stops working. */
    @POST("api/Account/rotate-auth-key")
    suspend fun rotateAuthKey(
        @Query("authKeyId") id: Int,
        @Body body: AuthKeyRequestDto,
    ): AuthKeyDto

    @DELETE("api/Account/auth-key")
    suspend fun deleteAuthKey(
        @Query("authKeyId") id: Int,
    )

    /** Kavita+ is active on the server (scrobbling needs it). */
    @GET("api/License/valid-license")
    suspend fun validLicense(): Boolean

    /** The user's AniList token; read only to see whether one is set, never kept. */
    @GET("api/Scrobbling/anilist-token")
    suspend fun aniListToken(): ResponseBody

    /** MAL account; only the username is decoded (the access token is ignored). */
    @GET("api/Scrobbling/mal-token")
    suspend fun malAccount(): MalAccountDto

    /** provider: 1 AniList, 2 MAL. */
    @GET("api/Scrobbling/token-expired")
    suspend fun scrobbleTokenExpired(
        @Query("provider") provider: Int,
    ): Boolean

    /** Series the user paused scrobbling for. */
    @GET("api/Scrobbling/holds")
    suspend fun scrobbleHolds(): List<ScrobbleHoldDto>

    /** Send to Device: the user's e-mail devices (Kindle, Kobo, …). */
    @GET("api/Device")
    suspend fun devices(): List<EmailDeviceDto>

    @POST("api/Device/create")
    suspend fun createDevice(
        @Body device: CreateEmailDeviceDto,
    ): EmailDeviceDto

    @POST("api/Device/update")
    suspend fun updateDevice(
        @Body device: EmailDeviceDto,
    ): EmailDeviceDto

    @DELETE("api/Device")
    suspend fun deleteDevice(
        @Query("deviceId") deviceId: Int,
    )

    /** The server e-mails these chapters' files to the device. */
    @POST("api/Device/send-to")
    suspend fun sendToDevice(
        @Body body: SendToEmailDeviceDto,
    )

    @POST("api/Device/send-series-to")
    suspend fun sendSeriesToDevice(
        @Body body: SendSeriesToEmailDeviceDto,
    )

    /** The signed-in user (only the profile fields are decoded; tokens in the response are ignored). */
    @GET("api/Account")
    suspend fun account(): AccountDto

    /** Hours, pages and chapters read in a time range. */
    @GET("api/Stats/reading-pace")
    suspend fun readingPace(
        @Query("userId") userId: Int,
        @Query("StartDate") startDate: String,
        @Query("EndDate") endDate: String,
        @Query("TimeZoneId") timeZoneId: String,
    ): ReadingPaceDto

    /** Reading events per day (one entry per day and format). */
    @GET("api/Stats/reading-counts")
    suspend fun readingCounts(
        @Query("userId") userId: Int,
        @Query("StartDate") startDate: String,
        @Query("EndDate") endDate: String,
        @Query("TimeZoneId") timeZoneId: String,
    ): List<DateStatCountDto>

    @GET("api/Stats/user-read")
    suspend fun userReadStats(
        @Query("userId") userId: Int,
    ): UserReadStatsDto

    @GET("api/Stats/pages-per-year")
    suspend fun pagesPerYear(
        @Query("userId") userId: Int,
    ): List<IntStatCountDto>

    /** Kavita `DayOfWeek`: 0 = Sunday. */
    @GET("api/Stats/day-breakdown")
    suspend fun dayBreakdown(
        @Query("userId") userId: Int,
    ): List<IntStatCountDto>

    @GET("api/Stats/genre-breakdown")
    suspend fun genreBreakdown(
        @Query("userId") userId: Int,
    ): StringBreakdownDto

    @GET("api/Stats/tag-breakdown")
    suspend fun tagBreakdown(
        @Query("userId") userId: Int,
    ): StringBreakdownDto

    /** Books/comics read, authors, ratings and reviews. */
    @GET("api/Stats/user-stats")
    suspend fun userStats(
        @Query("userId") userId: Int,
    ): ProfileStatBarDto

    /** Reading per hour of day, in [timeZoneId]. */
    @GET("api/Stats/avg-time-by-hour")
    suspend fun avgTimeByHour(
        @Query("userId") userId: Int,
        @Query("TimeZoneId") timeZoneId: String,
    ): ReadTimeByHourDto

    @GET("api/Stats/reads-by-month")
    suspend fun readsByMonth(
        @Query("userId") userId: Int,
        @Query("StartDate") startDate: String,
        @Query("EndDate") endDate: String,
        @Query("TimeZoneId") timeZoneId: String,
    ): List<YearMonthStatCountDto>

    /** Reading lists the user can see (own + promoted), most recently changed first. */
    @POST("api/ReadingList/lists")
    suspend fun readingLists(
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = READING_LISTS_PAGE,
        @Query("includePromoted") includePromoted: Boolean = true,
        @Query("sortByLastModified") sortByLastModified: Boolean = true,
    ): List<ReadingListDto>

    @GET("api/ReadingList")
    suspend fun readingList(
        @Query("readingListId") readingListId: Int,
    ): ReadingListDto

    @POST("api/ReadingList/create")
    suspend fun createReadingList(
        @Body body: CreateReadingListDto,
    ): ReadingListDto

    @POST("api/ReadingList/update")
    suspend fun updateReadingList(
        @Body body: UpdateReadingListDto,
    )

    @DELETE("api/ReadingList")
    suspend fun deleteReadingList(
        @Query("readingListId") readingListId: Int,
    )

    @POST("api/ReadingList/update-position")
    suspend fun moveReadingListItem(
        @Body body: ReadingListPositionDto,
    )

    @POST("api/ReadingList/delete-item")
    suspend fun deleteReadingListItem(
        @Body body: ReadingListPositionDto,
    )

    /** Adds every chapter of a series to the end of a reading list. */
    @POST("api/ReadingList/update-by-series")
    suspend fun addSeriesToReadingList(
        @Body body: ReadingListSeriesDto,
    )

    @GET("api/ReadingList/lists-for-series")
    suspend fun readingListsWithSeries(
        @Query("seriesId") seriesId: Int,
    ): List<ReadingListDto>

    /** Saves a CBL file on the server without importing it. */
    @Multipart
    @POST("api/Cbl/file-import")
    suspend fun saveCblFile(
        @Part cblFile: MultipartBody.Part,
    ): CblSavedFileDto

    /** Matches a saved CBL file against the library (nothing is created). */
    @POST("api/Cbl/re-validate")
    suspend fun validateCbl(
        @Body body: CblValidateDto,
    ): CblImportSummaryDto

    /** Creates (or updates) the reading list from a saved CBL file. */
    @POST("api/Cbl/finalize-import")
    suspend fun finalizeCbl(
        @Body body: CblFinalizeDto,
    ): CblImportSummaryDto

    @GET("api/ReadingList/items")
    suspend fun readingListItems(
        @Query("readingListId") readingListId: Int,
    ): List<ReadingListItemDto>

    /** The user's own collections that contain [seriesId]. */
    @GET("api/Collection/all-series")
    suspend fun collectionsWithSeries(
        @Query("seriesId") seriesId: Int,
        @Query("ownedOnly") ownedOnly: Boolean = true,
    ): List<CollectionDto>

    /** Adds series to a collection; collectionTagId 0 creates one named collectionTagTitle. */
    @POST("api/Collection/update-for-series")
    suspend fun addToCollection(
        @Body body: CollectionAddDto,
    )

    @POST("api/Collection/update-series")
    suspend fun removeFromCollection(
        @Body body: CollectionRemoveDto,
    )

    @POST("api/Collection/update")
    suspend fun updateCollection(
        @Body body: CollectionDto,
    ): CollectionDto

    @DELETE("api/Collection")
    suspend fun deleteCollection(
        @Query("tagId") tagId: Int,
    )

    /** Collections the user can see (own + promoted). */
    @GET("api/Collection")
    suspend fun collections(
        @Query("sortByLastModified") sortByLastModified: Boolean = true,
    ): List<CollectionDto>

    /** The user's saved smart filters. */
    @GET("api/Filter")
    suspend fun smartFilters(): List<SmartFilterDto>

    /** Creates or updates (by [SeriesFilterV2Dto.id]/name) a series smart filter. */
    @POST("api/Filter/update/series")
    suspend fun saveSmartFilter(
        @Body filter: SeriesFilterV2Dto,
    )

    @DELETE("api/Filter")
    suspend fun deleteSmartFilter(
        @Query("filterId") filterId: Int,
    )

    @GET("api/Stream/sidenav")
    suspend fun sideNav(
        @Query("visibleOnly") visibleOnly: Boolean = true,
    ): List<SideNavStreamDto>

    @POST("api/Stream/add-dashboard-stream")
    suspend fun addDashboardStream(
        @Query("smartFilterId") smartFilterId: Int,
    ): DashboardStreamDto

    @DELETE("api/Stream/smart-filter-dashboard-stream")
    suspend fun removeDashboardStream(
        @Query("dashboardStreamId") dashboardStreamId: Int,
    )

    @POST("api/Stream/add-sidenav-stream")
    suspend fun addSideNavStream(
        @Query("smartFilterId") smartFilterId: Int,
    ): SideNavStreamDto

    @DELETE("api/Stream/smart-filter-side-nav-stream")
    suspend fun removeSideNavStream(
        @Query("sideNavStreamId") sideNavStreamId: Int,
    )

    /** The user's Home layout. */
    @GET("api/Stream/dashboard")
    suspend fun dashboard(
        @Query("visibleOnly") visibleOnly: Boolean = true,
    ): List<DashboardStreamDto>

    /** Decodes a smart filter's encoded string into its filter object (kept raw, see [seriesByFilter]). */
    @POST("api/Filter/decode")
    suspend fun decodeFilter(
        @Body body: DecodeFilterDto,
    ): JsonObject

    /** `api/Series/v2` with a server-made filter (a decoded smart filter), sent back unchanged. */
    @POST("api/Series/v2")
    suspend fun seriesByFilter(
        @Body filter: JsonObject,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): List<SeriesDto>

    /** Series with reading progress, most recently read first. `libraryId` 0 = all libraries. */
    @POST("api/Series/on-deck")
    suspend fun onDeck(
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
        @Query("libraryId") libraryId: Int = 0,
    ): List<SeriesDto>

    @POST("api/Series/recently-updated-series")
    suspend fun recentlyUpdated(
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): List<GroupedSeriesDto>

    @GET("api/Server/server-info-slim")
    suspend fun serverInfo(): ServerInfoSlimDto

    /** Recurring jobs only; ad-hoc running tasks are pushed over SignalR (not used). */
    @GET("api/Server/jobs")
    suspend fun jobs(): List<JobDto>

    @POST("api/Library/scan")
    suspend fun scanLibrary(
        @Query("libraryId") libraryId: Int,
        @Query("force") force: Boolean = false,
    )

    @POST("api/Library/scan-all")
    suspend fun scanAll(
        @Query("force") force: Boolean = false,
    )

    @POST("api/Series/recently-added-v2")
    suspend fun recentlyAdded(
        @Body filter: SeriesFilterV2Dto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): List<SeriesDto>

    /** The chapter to resume in a series. */
    @POST("api/Reader/mark-read")
    suspend fun markSeriesRead(
        @Body body: MarkReadDto,
    )

    @POST("api/Reader/mark-unread")
    suspend fun markSeriesUnread(
        @Body body: MarkReadDto,
    )

    @POST("api/Reader/mark-volume-read")
    suspend fun markVolumeRead(
        @Body body: MarkVolumeReadDto,
    )

    @POST("api/Reader/mark-volume-unread")
    suspend fun markVolumeUnread(
        @Body body: MarkVolumeReadDto,
    )

    @POST("api/Reader/mark-multiple-read")
    suspend fun markMultipleRead(
        @Body body: MarkVolumesReadDto,
    )

    @POST("api/Reader/mark-multiple-unread")
    suspend fun markMultipleUnread(
        @Body body: MarkVolumesReadDto,
    )

    @POST("api/want-to-read/add-series")
    suspend fun addWantToRead(
        @Body body: UpdateWantToReadDto,
    )

    @POST("api/want-to-read/remove-series")
    suspend fun removeWantToRead(
        @Body body: UpdateWantToReadDto,
    )

    @POST("api/Rating/series")
    suspend fun rateSeries(
        @Body body: UpdateRatingDto,
    )

    @POST("api/Review/series")
    suspend fun saveSeriesReview(
        @Body body: UpdateUserReviewDto,
    ): UserReviewDto

    @DELETE("api/Review/series")
    suspend fun deleteSeriesReview(
        @Query("seriesId") seriesId: Int,
    )

    /** May answer 204 (no Kavita+ data), hence [Response]. */
    @GET("api/Metadata/series-detail-plus")
    suspend fun seriesDetailPlus(
        @Query("seriesId") seriesId: Int,
        @Query("libraryType") libraryType: Int,
    ): Response<SeriesDetailPlusDto>

    @GET("api/Person")
    suspend fun person(
        @Query("name") name: String,
    ): PersonDto

    @POST("api/Series/refresh-metadata")
    suspend fun refreshSeriesMetadata(
        @Body body: RefreshSeriesDto,
    )

    @POST("api/Series/scan")
    suspend fun scanSeries(
        @Body body: RefreshSeriesDto,
    )

    /** The full SeriesMetadataDto as raw JSON, so an edit can send every field back unchanged. */
    @GET("api/Series/metadata")
    suspend fun rawSeriesMetadata(
        @Query("seriesId") seriesId: Int,
    ): JsonObject

    /** Body: `UpdateSeriesMetadataDto` (`{"seriesMetadata": {...}}`). */
    @POST("api/Series/metadata")
    suspend fun updateSeriesMetadata(
        @Body body: JsonObject,
    )

    @GET("api/Search/search")
    suspend fun search(
        @Query("queryString") query: String,
        @Query("includeChapterAndFiles") includeChapterAndFiles: Boolean,
    ): SearchResultGroupDto

    @GET("api/Search/series-for-chapter")
    suspend fun seriesForChapter(
        @Query("chapterId") chapterId: Int,
    ): SeriesDto

    @GET("api/Search/series-for-mangafile")
    suspend fun seriesForFile(
        @Query("mangaFileId") fileId: Int,
    ): SeriesDto

    @GET("api/Person/roles")
    suspend fun personRoles(
        @Query("personId") personId: Int,
    ): List<Int>

    @GET("api/Reader/chapter-info")
    suspend fun chapterInfo(
        @Query("chapterId") chapterId: Int,
        @Query("includeDimensions") includeDimensions: Boolean = true,
        // PDFs: page count and dimensions of the server-rendered pages; ignored for other formats.
        @Query("extractPdf") extractPdf: Boolean = true,
    ): ChapterInfoDto

    /** -1 when there is none. */
    @GET("api/Reader/next-chapter")
    suspend fun nextChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") chapterId: Int,
    ): Int

    /** -1 when there is none. */
    @GET("api/Reader/prev-chapter")
    suspend fun prevChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") chapterId: Int,
    ): Int

    /** The chapter's original file (a single EPUB/PDF is returned as is). Needs the Download role. */
    @Streaming
    @GET("api/Download/chapter")
    suspend fun downloadChapter(
        @Query("chapterId") chapterId: Int,
    ): ResponseBody

    /** The original file from [range]'s offset (`bytes=N-`); 206 when the server honours it. Download role. */
    @Streaming
    @GET("api/Download/chapter")
    suspend fun downloadChapterFrom(
        @Query("chapterId") chapterId: Int,
        @Header("Range") range: String?,
    ): Response<ResponseBody>

    /** A series' cover (kept with its downloads for offline). `apiKey` is added on the wire by AuthInterceptor. */
    @Streaming
    @GET("api/Image/series-cover")
    suspend fun seriesCover(
        @Query("seriesId") seriesId: Int,
    ): ResponseBody

    /** One page image (0-based); PDFs are rendered server-side. `apiKey` is added on the wire by AuthInterceptor. */
    @Streaming
    @GET("api/Reader/image")
    suspend fun pageImage(
        @Query("chapterId") chapterId: Int,
        @Query("page") page: Int,
        @Query("extractPdf") extractPdf: Boolean = true,
    ): ResponseBody

    @GET("api/Annotation/all")
    suspend fun annotations(
        @Query("chapterId") chapterId: Int,
    ): List<AnnotationDto>

    @POST("api/Annotation/create")
    suspend fun createAnnotation(
        @Body body: AnnotationDto,
    ): AnnotationDto

    @POST("api/Annotation/update")
    suspend fun updateAnnotation(
        @Body body: AnnotationDto,
    ): AnnotationDto

    @DELETE("api/Annotation")
    suspend fun deleteAnnotation(
        @Query("annotationId") annotationId: Int,
    )

    @GET("api/Reader/get-progress")
    suspend fun progress(
        @Query("chapterId") chapterId: Int,
    ): ProgressDto

    @POST("api/Reader/progress")
    suspend fun saveProgress(
        @Body body: ProgressDto,
    )

    @GET("api/Reader/chapter-bookmarks")
    suspend fun chapterBookmarks(
        @Query("chapterId") chapterId: Int,
    ): List<BookmarkDto>

    @POST("api/Reader/bookmark")
    suspend fun bookmark(
        @Body body: BookmarkDto,
    )

    @POST("api/Reader/unbookmark")
    suspend fun unbookmark(
        @Body body: BookmarkDto,
    )

    @GET("api/Series/all-related")
    suspend fun allRelated(
        @Query("seriesId") seriesId: Int,
    ): RelatedSeriesDto

    @GET("api/Series/metadata")
    suspend fun seriesMetadata(
        @Query("seriesId") seriesId: Int,
    ): SeriesMetadataDto

    @GET("api/Reader/time-left")
    suspend fun timeLeft(
        @Query("seriesId") seriesId: Int,
    ): HourEstimateRangeDto

    @GET("api/want-to-read")
    suspend fun isWantToRead(
        @Query("seriesId") seriesId: Int,
    ): Boolean

    @GET("api/Reader/continue-point")
    suspend fun continuePoint(
        @Query("seriesId") seriesId: Int,
    ): ChapterDto

    /** The profile that applies to a series (series → library → default), as raw JSON: see ReadingProfileDto. */
    @GET("api/reading-profile/{libraryId}/{seriesId}")
    suspend fun readingProfile(
        @Path("libraryId") libraryId: Int,
        @Path("seriesId") seriesId: Int,
    ): JsonObject

    /** The user's non-implicit profiles (kind 0 = Default, 1 = User), as raw JSON. */
    @GET("api/reading-profile/all")
    suspend fun readingProfiles(): List<JsonObject>

    /** Updates one of the user's profiles; returns the saved profile. */
    @POST("api/reading-profile")
    suspend fun updateReadingProfile(
        @Body profile: JsonObject,
    ): JsonObject

    /** Creates/updates the series' implicit profile. */
    @POST("api/reading-profile/series")
    suspend fun saveSeriesReadingProfile(
        @Query("libraryId") libraryId: Int,
        @Query("seriesId") seriesId: Int,
        @Body profile: JsonObject,
    ): JsonObject

    @GET("api/Series/series-detail")
    suspend fun seriesDetail(
        @Query("seriesId") seriesId: Int,
    ): SeriesDetailDto
}

/** One [KavitaApi] per server, on that server's authenticated client (pinning, cleartext policy, auth). */
@Singleton
class KavitaApis
    @Inject
    constructor(
        private val clients: ServerHttpClients,
    ) {
        // Keyed by the whole Server: a changed pin / HTTP opt-in builds a new client.
        private val cache = ConcurrentHashMap<Server, KavitaApi>()

        fun forServer(
            server: Server,
            credentials: CredentialSource,
        ): KavitaApi =
            cache.getOrPut(server) {
                Retrofit
                    .Builder()
                    .baseUrl(server.url.value.trimEnd('/') + "/")
                    .client(clients.create(server, credentials))
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(KavitaApi::class.java)
            }

        private companion object {
            val json =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    coerceInputValues = true
                }
        }
    }

/** Runs a Retrofit call, mapping HTTP errors to their [AppError] and everything else like [appResult]. */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        AppResult.Failure(AppError.fromHttpCode(e.code()))
    } catch (e: Exception) {
        AppResult.Failure(AppError.from(e))
    }

/** Uploads a CBL file as the `cblFile` form part. */
suspend fun KavitaApi.saveCbl(
    fileName: String,
    bytes: ByteArray,
): CblSavedFileDto =
    saveCblFile(
        MultipartBody.Part.createFormData("cblFile", fileName, bytes.toRequestBody("application/xml".toMediaType())),
    )
