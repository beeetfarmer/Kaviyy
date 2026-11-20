package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.database.ProgressDao
import com.nl.kaviyy.core.database.ProgressEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Read position. [record] is local and instant (Room + a coalesced outbox entry, so nothing is lost if the reader
 * closes); [push] sends the latest position and clears the entry. Reaching the last page records the chapter as read.
 */
interface ProgressRepository {
    /** Page (0-based) to open [chapter] at. */
    suspend fun resume(
        server: Server,
        chapter: ReaderChapter,
    ): Int

    suspend fun record(
        server: Server,
        chapter: ReaderChapter,
        page: Int,
    )

    suspend fun push(
        server: Server,
        chapterId: Int,
    ): AppResult<Unit>
}

class OfflineFirstProgressRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val progress: ProgressDao,
        private val outbox: OutboxDao,
    ) : ProgressRepository {
        override suspend fun resume(
            server: Server,
            chapter: ReaderChapter,
        ): Int {
            val id = server.id.value
            val queued = outbox.pending(id).any { it.type == OUTBOX_PROGRESS && it.entityKey == "${chapter.id}" }
            val local = progress.get(id, chapter.id)
            // A position not pushed yet is the newest one; otherwise the server knows best (other devices).
            val pagesRead =
                if (queued && local != null) {
                    local.pageNum
                } else {
                    apiCall { apis.api(server).progress(chapter.id) }.let { r ->
                        if (r is AppResult.Success) {
                            r.value.pageNum
                        } else {
                            local?.pageNum
                                ?: progress.chapterPagesRead(id, chapter.id)
                        }
                    } ?: 0
                }
            // A finished chapter starts over; otherwise continue where reading stopped.
            return if (pagesRead >= chapter.pages) 0 else pagesRead.coerceAtLeast(0)
        }

        override suspend fun record(
            server: Server,
            chapter: ReaderChapter,
            page: Int,
        ) {
            val id = server.id.value
            val pagesRead = if (page >= chapter.pages - 1) chapter.pages else page
            val row =
                ProgressEntity(
                    id,
                    chapter.id,
                    chapter.volumeId,
                    chapter.seriesId,
                    chapter.libraryId,
                    pagesRead,
                    null,
                    System.currentTimeMillis(),
                )
            progress.save(row)
            val payload = Json.encodeToString(ProgressDto.serializer(), row.toDto())
            outbox.enqueue(
                OutboxEntity(
                    0,
                    id,
                    OUTBOX_PROGRESS,
                    "${chapter.id}",
                    payload,
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                ),
            )
        }

        override suspend fun push(
            server: Server,
            chapterId: Int,
        ): AppResult<Unit> {
            val id = server.id.value
            val row = progress.get(id, chapterId) ?: return AppResult.Success(Unit)
            val result = apiCall { apis.api(server).saveProgress(row.toDto()) }
            // Only clear the queue if nothing newer was recorded while the request was in flight.
            if (result is AppResult.Success &&
                progress.get(id, chapterId) == row
            ) {
                outbox.deleteFor(id, OUTBOX_PROGRESS, "$chapterId")
            }
            return result
        }
    }

private fun ProgressEntity.toDto() =
    ProgressDto(
        volumeId,
        chapterId,
        pageNum,
        seriesId,
        libraryId,
        bookScrollId,
        Instant.ofEpochMilli(lastModifiedUtc).toString(),
    )
