package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.appResult
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
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
