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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
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
