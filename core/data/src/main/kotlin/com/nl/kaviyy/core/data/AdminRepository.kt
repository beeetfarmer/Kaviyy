package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.RefreshSeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.apiCall
import com.nl.kaviyy.core.network.kavitaTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** Kavita's admin role name; the server enforces it too. */
const val ROLE_ADMIN = "Admin"

val Server.isAdmin: Boolean get() = ROLE_ADMIN in roles

/** Admin-only series actions (network only; the server checks the role). */
interface AdminRepository {
    /** Starts a background job on the server. */
    suspend fun refreshMetadata(
        server: Server,
        libraryId: Int,
        seriesId: Int,
    ): AppResult<Unit>

    /** Starts a background job on the server. */
    suspend fun scan(
        server: Server,
        libraryId: Int,
        seriesId: Int,
    ): AppResult<Unit>

    /** Replaces only the summary (and locks it so a scan keeps it). */
    suspend fun updateSummary(
        server: Server,
        seriesId: Int,
        summary: String,
    ): AppResult<Unit>

    suspend fun serverInfo(server: Server): AppResult<AdminServerInfo> = AppResult.Failure(AppError.NotFound)

    suspend fun jobs(server: Server): AppResult<List<ServerJob>> = AppResult.Failure(AppError.NotFound)

    /** Starts a scan of one library, or of all of them when [libraryId] is null. */
    suspend fun scanLibrary(
        server: Server,
        libraryId: Int?,
    ): AppResult<Unit> = AppResult.Failure(AppError.NotFound)

    /** Newest series first (name, added at). */
    suspend fun recentlyAdded(server: Server): AppResult<List<Pair<String, Long>>> =
        AppResult.Failure(AppError.NotFound)
}

data class AdminServerInfo(
    val version: String?,
    val docker: Boolean,
    /** Epoch millis, 0 when unknown. */
    val installedAt: Long,
)

data class ServerJob(
    val title: String,
    /** Epoch millis, 0 = never ran. */
    val lastRun: Long,
)

class KavitaAdminRepository
    @Inject
    constructor(
        private val apis: ServerApis,
    ) : AdminRepository {
        override suspend fun refreshMetadata(
            server: Server,
            libraryId: Int,
            seriesId: Int,
        ) = apiCall {
            apis
                .api(
                    server,
                ).refreshSeriesMetadata(RefreshSeriesDto(libraryId, seriesId, forceUpdate = true))
        }

        override suspend fun scan(
            server: Server,
            libraryId: Int,
            seriesId: Int,
        ) = apiCall { apis.api(server).scanSeries(RefreshSeriesDto(libraryId, seriesId, forceUpdate = true)) }

        override suspend fun serverInfo(server: Server) =
            apiCall {
                apis.api(server).serverInfo()
            }.map { AdminServerInfo(it.kavitaVersion, it.isDocker, kavitaTime(it.firstInstallDate)) }

        override suspend fun jobs(server: Server) =
            apiCall { apis.api(server).jobs() }.map { jobs ->
                jobs.map { ServerJob(it.title ?: it.id, kavitaTime(it.lastExecutionUtc)) }
            }

        override suspend fun scanLibrary(
            server: Server,
            libraryId: Int?,
        ) = apiCall { if (libraryId == null) apis.api(server).scanAll() else apis.api(server).scanLibrary(libraryId) }

        override suspend fun recentlyAdded(server: Server) =
            apiCall { apis.api(server).recentlyAdded(SeriesFilterV2Dto(), 1, RECENT_COUNT) }.map { list ->
                list.map {
                    (
                        it.localizedName?.takeIf(
                            String::isNotBlank,
                        ) ?: it.name.orEmpty()
                    ) to kavitaTime(it.created)
                }
            }

        override suspend fun updateSummary(
            server: Server,
            seriesId: Int,
            summary: String,
        ) = apiCall {
            val api = apis.api(server)
            // Round-trip the full object: the endpoint overwrites every field, so nothing else may change.
            val current = api.rawSeriesMetadata(seriesId)
            val edited =
                JsonObject(
                    current + mapOf("summary" to JsonPrimitive(summary.trim()), "summaryLocked" to JsonPrimitive(true)),
                )
            api.updateSeriesMetadata(buildJsonObject { put("seriesMetadata", edited) })
        }
    }

private const val RECENT_COUNT = 10
