package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.AnnotationDao
import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.database.ProgressDao
import com.nl.kaviyy.core.database.ProgressEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.AnnotationDto
import com.nl.kaviyy.core.network.BookmarkDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.network.ServerClock
import com.nl.kaviyy.core.network.UpdateRatingDto
import com.nl.kaviyy.core.network.UpdateWantToReadDto
import com.nl.kaviyy.core.network.apiCall
import com.nl.kaviyy.core.network.parseUtc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends queued changes to the server in the order they were made. Success, or an answer that can never succeed
 * (4xx: gone, forbidden, rejected), removes the entry; a network, auth or server error stops the pass so order holds.
 */
@Singleton
class OutboxReplayer
    @Inject
    constructor(
        private val apis: ServerApis,
        private val outbox: OutboxDao,
        private val annotations: AnnotationDao,
        private val progress: ProgressDao,
        private val clock: ServerClock,
    ) {
        /** Success when the queue for [server] is empty; otherwise the error that stopped it. */
        suspend fun flush(server: Server): AppResult<Unit> {
            val api = apis.api(server)
            for (entry in outbox.pending(server.id.value)) {
                val result =
                    runCatching { Json.parseToJsonElement(entry.payload).jsonObject }.fold(
                        onSuccess = { apiCall { replay(api, entry, it) } },
                        onFailure = { AppResult.Failure(AppError.InvalidData) },
                    )
                val error = (result as? AppResult.Failure)?.error
                if (error == null || error.isPermanent() || error.gaveUp(entry.attempts + 1)) {
                    outbox.delete(entry.id)
                } else {
                    outbox.bumpAttempts(entry.id)
                    return result
                }
            }
            return AppResult.Success(Unit)
        }

        private suspend fun replay(
            api: KavitaApi,
            entry: OutboxEntity,
            json: JsonObject,
        ) {
            fun int(name: String) = json.field(name).jsonPrimitive.int

            fun bool(name: String) = json.field(name).jsonPrimitive.boolean
            when (entry.type) {
                OUTBOX_PROGRESS -> {
                    progress(api, entry, Json.decodeFromJsonElement(ProgressDto.serializer(), json))
                }

                OUTBOX_MARK_READ -> {
                    markRead(api, entry, json)
                }

                OUTBOX_WANT_TO_READ -> {
                    val body = UpdateWantToReadDto(listOf(int("seriesId")))
                    if (bool("on")) api.addWantToRead(body) else api.removeWantToRead(body)
                }

                OUTBOX_RATING -> {
                    api.rateSeries(UpdateRatingDto(int("seriesId"), json.field("userRating").jsonPrimitive.float))
                }

                OUTBOX_BOOKMARK -> {
                    val body = BookmarkDto(0, int("page"), int("volumeId"), int("seriesId"), int("chapterId"))
                    if (bool("on")) api.bookmark(body) else api.unbookmark(body)
                }

                OUTBOX_ANNOTATION -> {
                    annotation(api, entry, json)
                }

                OUTBOX_READING_PROFILE -> {
                    api.saveSeriesReadingProfile(int("libraryId"), int("seriesId"), json.field("profile").jsonObject)
                }

                else -> {
                    throw IllegalArgumentException(entry.type)
                }
            }
        }

        /** Newest wins: a position the server already has a newer one for is replaced locally instead of pushed. */
        private suspend fun progress(
            api: KavitaApi,
            entry: OutboxEntity,
            local: ProgressDto,
        ) {
            val remote = api.progress(local.chapterId)
            if (parseUtc(remote.lastModifiedUtc) <= parseUtc(local.lastModifiedUtc)) {
                api.saveProgress(local)
                return
            }
            progress.save(
                ProgressEntity(
                    entry.serverId,
                    local.chapterId,
                    local.volumeId,
                    local.seriesId,
                    local.libraryId,
                    remote.pageNum,
                    remote.bookScrollId,
                    parseUtc(remote.lastModifiedUtc),
                ),
            )
        }

        /** Unread moves progress back: only if it's newer than what the server last saw for that chapter. */
        private suspend fun markRead(
            api: KavitaApi,
            entry: OutboxEntity,
            json: JsonObject,
        ) {
            val target =
                ReadTarget.parse(
                    json.field("target").jsonPrimitive.content,
                    json.field("seriesId").jsonPrimitive.int,
                )
            requireNotNull(target) { entry.entityKey }
            val read = json.field("read").jsonPrimitive.boolean
            val queuedAt = entry.createdAt + clock.offset(entry.serverId)
            val stale = !read && target is ReadTarget.Chapter && remoteTime(api, target.chapterId) > queuedAt
            if (!stale) api.markRead(target, read)
        }

        private suspend fun remoteTime(
            api: KavitaApi,
            chapterId: Int,
        ) = parseUtc(api.progress(chapterId).lastModifiedUtc)

        /** Creates (writing the new id back to the row) or updates; deletes by remote id. */
        private suspend fun annotation(
            api: KavitaApi,
            entry: OutboxEntity,
            json: JsonObject,
        ) {
            if (json[OP]?.jsonPrimitive?.content == OP_DELETE) {
                api.deleteAnnotation(json.field("id").jsonPrimitive.int)
                return
            }
            val dto = Json.decodeFromJsonElement(AnnotationDto.serializer(), json.field("annotation"))
            val row = annotations.get(entry.serverId, entry.entityKey)
            val remoteId = row?.remoteId ?: dto.id.takeIf { it > 0 }
            if (remoteId != null) {
                api.updateAnnotation(dto.copy(id = remoteId))
            } else {
                val created = api.createAnnotation(dto)
                row?.let { annotations.upsert(listOf(it.copy(remoteId = created.id))) }
            }
        }
    }

/** Missing fields are bad data (InvalidData), never a reason to retry. */
private fun JsonObject.field(name: String) = requireNotNull(get(name)) { "missing $name" }

/** Server-side failures (5xx, unexpected) stop retrying after this many passes; offline and sign-in never do. */
private const val MAX_ATTEMPTS = 10

private fun AppError.gaveUp(attempts: Int) =
    this != AppError.Network && this != AppError.Tls && this != AppError.Unauthorized && attempts >= MAX_ATTEMPTS

/** 4xx (bar 401, handled by re-login) and unreadable entries never succeed on retry. */
@Suppress("MagicNumber")
internal fun AppError.isPermanent(): Boolean =
    when (this) {
        AppError.Forbidden, AppError.NotFound, AppError.InvalidData -> true
        is AppError.Server -> code in 400..499
        else -> false
    }
