package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.AnnotationDao
import com.nl.kaviyy.core.database.AnnotationEntity
import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.AnnotationDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** A new highlight: [page] is the spine index, [text] anchors it, [xPath] is a best-effort Kavita path. */
data class AnnotationDraft(
    val page: Int,
    val text: String,
    val xPath: String,
    val color: Int,
    val note: String? = null,
)

/** A stored highlight: [uuid] is stable across syncs, [color] is the slot (0 yellow, 1 green, 2 blue, 3 pink). */
data class Highlight(
    val uuid: String,
    val page: Int,
    val text: String,
    val color: Int,
    val note: String?,
)

/**
 * EPUB highlights and notes (Kavita annotations). Room first; then the server; a failed push is queued in the outbox
 * by client UUID (`op` upsert/delete) for M17's SyncWorker. A refresh keeps queued local changes.
 */
interface AnnotationRepository {
    fun annotations(
        server: Server,
        chapterId: Int,
    ): Flow<List<Highlight>>

    suspend fun refresh(
        server: Server,
        chapterId: Int,
    ): AppResult<Unit>

    suspend fun create(
        server: Server,
        chapter: ReaderChapter,
        draft: AnnotationDraft,
    ): AppResult<Unit>

    suspend fun update(
        server: Server,
        chapter: ReaderChapter,
        uuid: String,
        color: Int,
        note: String?,
    ): AppResult<Unit>

    suspend fun delete(
        server: Server,
        uuid: String,
    ): AppResult<Unit>
}

private const val OP = "op"
private const val OP_UPSERT = "upsert"
private const val OP_DELETE = "delete"
private const val REMOTE_UUID = "remote-"

class OfflineFirstAnnotationRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val annotations: AnnotationDao,
        private val outbox: OutboxDao,
    ) : AnnotationRepository {
        override fun annotations(
            server: Server,
            chapterId: Int,
        ) = annotations.observeChapter(server.id.value, chapterId).map { rows ->
            rows.map { Highlight(it.clientUuid, it.pageNumber, it.selectedText, it.selectedSlotIndex, it.comment) }
        }

        override suspend fun refresh(
            server: Server,
            chapterId: Int,
        ): AppResult<Unit> {
            val id = server.id.value
            return when (val r = apiCall { apis.api(server).annotations(chapterId) }) {
                is AppResult.Failure -> {
                    r
                }

                is AppResult.Success -> {
                    val queued = outbox.pending(id).filter { it.type == OUTBOX_ANNOTATION }
                    val deletedRemote = queued.mapNotNull { it.deletedRemoteId() }.toSet()
                    val known =
                        annotations
                            .chapter(
                                id,
                                chapterId,
                            ).filter { it.remoteId != null }
                            .associateBy { it.remoteId }
                    val rows =
                        r.value
                            .filter { it.id !in deletedRemote }
                            .map { dto -> dto.toEntity(id, known[dto.id]?.clientUuid ?: "$REMOTE_UUID${dto.id}") }
                    annotations.replaceChapter(id, chapterId, rows, queued.map { it.entityKey })
                    AppResult.Success(Unit)
                }
            }
        }

        override suspend fun create(
            server: Server,
            chapter: ReaderChapter,
            draft: AnnotationDraft,
        ): AppResult<Unit> {
            val now = System.currentTimeMillis()
            val row =
                AnnotationEntity(
                    server.id.value,
                    UUID.randomUUID().toString(),
                    null,
                    chapter.id,
                    chapter.volumeId,
                    chapter.seriesId,
                    draft.page,
                    draft.xPath,
                    draft.xPath,
                    draft.text,
                    draft.note,
                    draft.color,
                    false,
                    now,
                    now,
                )
            annotations.upsert(listOf(row))
            return save(server, row, chapter.libraryId)
        }

        override suspend fun update(
            server: Server,
            chapter: ReaderChapter,
            uuid: String,
            color: Int,
            note: String?,
        ): AppResult<Unit> {
            val row =
                annotations.get(server.id.value, uuid)?.copy(
                    selectedSlotIndex = color,
                    comment = note?.takeIf { it.isNotBlank() },
                    lastModifiedUtc = System.currentTimeMillis(),
                ) ?: return AppResult.Success(Unit)
            annotations.upsert(listOf(row))
            return save(server, row, chapter.libraryId)
        }

        override suspend fun delete(
            server: Server,
            uuid: String,
        ): AppResult<Unit> {
            val id = server.id.value
            val remote = annotations.get(id, uuid)?.remoteId
            annotations.delete(id, uuid)
            if (remote == null) {
                // Never reached the server (or already gone): dropping a queued create is enough.
                outbox.deleteFor(id, OUTBOX_ANNOTATION, uuid)
                return AppResult.Success(Unit)
            }
            val payload =
                buildJsonObject {
                    put(OP, OP_DELETE)
                    put("id", remote)
                }.toString()
            return outbox.pushOrQueue(
                server,
                OUTBOX_ANNOTATION,
                uuid,
                payload,
            ) { apis.api(server).deleteAnnotation(remote) }
        }

        /** Creates or updates on the server; a new id is written back to the row. */
        private suspend fun save(
            server: Server,
            row: AnnotationEntity,
            libraryId: Int,
        ): AppResult<Unit> {
            val dto = row.toDto(libraryId)
            val payload =
                buildJsonObject {
                    put(OP, OP_UPSERT)
                    put("annotation", Json.encodeToJsonElement(AnnotationDto.serializer(), dto))
                }.toString()
            return outbox.pushOrQueue(server, OUTBOX_ANNOTATION, row.clientUuid, payload) {
                val api = apis.api(server)
                if (row.remoteId == null) {
                    val created = api.createAnnotation(dto)
                    annotations
                        .get(
                            row.serverId,
                            row.clientUuid,
                        )?.let { annotations.upsert(listOf(it.copy(remoteId = created.id))) }
                } else {
                    api.updateAnnotation(dto)
                }
            }
        }
    }

private fun OutboxEntity.deletedRemoteId(): Int? =
    runCatching {
        val json = Json.parseToJsonElement(payload).jsonObject
        if (json[OP]?.jsonPrimitive?.content == OP_DELETE) json["id"]?.jsonPrimitive?.int else null
    }.getOrNull()

private fun AnnotationEntity.toDto(libraryId: Int) =
    AnnotationDto(
        id = remoteId ?: 0,
        xPath = xPath,
        endingXPath = endingXPath,
        selectedText = selectedText,
        comment = comment,
        context = null,
        highlightCount = selectedText.length,
        containsSpoiler = containsSpoiler,
        pageNumber = pageNumber,
        selectedSlotIndex = selectedSlotIndex,
        chapterId = chapterId,
        volumeId = volumeId,
        seriesId = seriesId,
        libraryId = libraryId,
        ownerUserId = 0,
        createdUtc = Instant.ofEpochMilli(createdUtc).toString(),
        lastModifiedUtc = Instant.ofEpochMilli(lastModifiedUtc).toString(),
    )

private fun AnnotationDto.toEntity(
    serverId: Long,
    uuid: String,
) = AnnotationEntity(
    serverId,
    uuid,
    id,
    chapterId,
    volumeId,
    seriesId,
    pageNumber,
    xPath,
    endingXPath ?: xPath,
    selectedText.orEmpty(),
    comment?.takeIf { it.isNotBlank() },
    selectedSlotIndex,
    containsSpoiler,
    createdUtc.toMillis(),
    lastModifiedUtc.toMillis(),
)

private fun String?.toMillis(): Long = this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
