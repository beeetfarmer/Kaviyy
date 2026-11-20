package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.appResult
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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

    @GET("api/Series/{seriesId}")
    suspend fun series(
        @Path("seriesId") seriesId: Int,
    ): SeriesDto

    /** Genres in the given libraries (comma-separated ids). */
    @GET("api/Metadata/genres")
    suspend fun genres(
        @Query("libraryIds") libraryIds: String,
    ): List<GenreTagDto>

    /** Publication statuses present in the given libraries. */
    @GET("api/Metadata/publication-status")
    suspend fun publicationStatuses(
        @Query("libraryIds") libraryIds: String,
    ): List<MetadataValueDto>

    @GET("api/Metadata/tags")
    suspend fun tags(
        @Query("libraryIds") libraryIds: String,
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

    /** Collections the user can see (own + promoted). */
    @GET("api/Collection")
    suspend fun collections(): List<CollectionDto>

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
