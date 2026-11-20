package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.RefreshSeriesDto
import com.nl.kaviyy.core.network.apiCall
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
}

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
