package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.database.ReadingProfileDao
import com.nl.kaviyy.core.database.ReadingProfileEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ReadingProfileDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject

internal const val OUTBOX_READING_PROFILE = "readingProfile"
private const val KIND_IMPLICIT = 2

/**
 * Reader settings ⇄ Kavita reading profiles (M4.3). Reads come from Room (series profile, else the default profile,
 * else Kavita's defaults). Edits are written locally first as the series' implicit profile, then pushed; if the push
 * fails they wait in the outbox (latest per series) for the sync worker (M17).
 */
interface ReaderSettingsRepository {
    fun settings(
        server: Server,
        seriesId: Int,
    ): Flow<ReadingProfileDto>

    /** Fetches the profile the server applies to this series. */
    suspend fun refresh(
        server: Server,
        libraryId: Int,
        seriesId: Int,
    ): AppResult<Unit>

    /**
     * Saves [settings] for this series. Always succeeds locally; the result says whether the server has it yet
     * (failure = queued in the outbox).
     */
    suspend fun update(
        server: Server,
        libraryId: Int,
        seriesId: Int,
        settings: ReadingProfileDto,
    ): AppResult<Unit>
}

class OfflineFirstReaderSettingsRepository
    @Inject
    constructor(
        private val profiles: ReadingProfileDao,
        private val outbox: OutboxDao,
        private val apis: ServerApis,
    ) : ReaderSettingsRepository {
        override fun settings(
            server: Server,
            seriesId: Int,
        ): Flow<ReadingProfileDto> =
            profiles.observeForSeries(server.id.value, seriesId).map { row ->
                row?.let { ReadingProfileDto.parse(it.json) } ?: ReadingProfileDto()
            }

        override suspend fun refresh(
            server: Server,
            libraryId: Int,
            seriesId: Int,
        ): AppResult<Unit> =
            when (val result = apiCall { apis.api(server).readingProfile(libraryId, seriesId) }) {
                is AppResult.Failure -> {
                    result
                }

                is AppResult.Success -> {
                    store(server, seriesId, result.value)
                    AppResult.Success(Unit)
                }
            }

        override suspend fun update(
            server: Server,
            libraryId: Int,
            seriesId: Int,
            settings: ReadingProfileDto,
        ): AppResult<Unit> {
            val id = server.id.value
            val current = profiles.observeForSeries(id, seriesId).first()
            val base = current?.json?.let(ReadingProfileDto::rawObject) ?: JsonObject(emptyMap())
            val local =
                settings.copy(id = localId(seriesId), kind = KIND_IMPLICIT, name = settings.name).mergeInto(base)
            profiles.bind(ReadingProfileEntity(id, localId(seriesId), KIND_IMPLICIT, local.toString()), seriesId)
            // The server assigns the implicit profile's id; don't send our local one.
            val body =
                JsonObject(
                    local + (
                        "id" to
                            JsonPrimitive(
                                current?.id?.takeIf { it > 0 && current.kind == KIND_IMPLICIT } ?: 0,
                            )
                    ),
                )
            return when (
                val result =
                    apiCall {
                        apis
                            .api(
                                server,
                            ).saveSeriesReadingProfile(libraryId, seriesId, body)
                    }
            ) {
                is AppResult.Success -> {
                    store(server, seriesId, result.value)
                    profiles.deleteProfile(id, localId(seriesId))
                    outbox.deleteFor(id, OUTBOX_READING_PROFILE, seriesId.toString())
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> {
                    outbox.enqueue(
                        OutboxEntity(
                            serverId = id,
                            type = OUTBOX_READING_PROFILE,
                            entityKey = seriesId.toString(),
                            payload =
                                buildJsonObject {
                                    put("libraryId", libraryId)
                                    put("seriesId", seriesId)
                                    put("profile", body)
                                }.toString(),
                            clientUuid = UUID.randomUUID().toString(),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    result
                }
            }
        }

        private suspend fun store(
            server: Server,
            seriesId: Int,
            raw: JsonObject,
        ) {
            val dto = ReadingProfileDto.parse(raw.toString())
            profiles.bind(ReadingProfileEntity(server.id.value, dto.id, dto.kind, raw.toString()), seriesId)
        }

        /** Local implicit profiles get negative ids so they never collide with server ids. */
        private fun localId(seriesId: Int) = -seriesId
    }
