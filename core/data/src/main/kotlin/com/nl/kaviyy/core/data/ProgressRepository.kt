package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.database.ProgressDao
import com.nl.kaviyy.core.database.ProgressEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.network.ServerClock
import com.nl.kaviyy.core.network.apiCall
import com.nl.kaviyy.core.network.parseUtc
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Where to reopen a chapter: [page] is Kavita's page index (for EPUB the reading-order index); [position] is this app's
 * finer position within it (an EPUB Readium locator as JSON), only when it is known to match the page.
 */
data class ResumePosition(
    val page: Int,
    val position: String? = null,
)

/**
 * Read position. [record] is local and instant (Room + a coalesced outbox entry, so nothing is lost if the reader
 * closes); [push] sends the latest position and clears the entry. Reaching the last page records the chapter as read.
 */
interface ProgressRepository {
    suspend fun resume(
        server: Server,
        chapter: ReaderChapter,
    ): ResumePosition

    /** [position]: finer local position (see [ResumePosition]); never sent to Kavita unless it is a Kavita XPath. */
    suspend fun record(
        server: Server,
        chapter: ReaderChapter,
        page: Int,
        position: String? = null,
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
        private val clock: ServerClock = ServerClock(),
    ) : ProgressRepository {
        override suspend fun resume(
            server: Server,
            chapter: ReaderChapter,
        ): ResumePosition {
            val id = server.id.value
            val queued = outbox.pending(id).any { it.type == OUTBOX_PROGRESS && it.entityKey == "${chapter.id}" }
            val local = progress.get(id, chapter.id)
            // A position not pushed yet is the newest one; otherwise the server knows best (other devices).
            // Newest wins: local times are on the server's clock (ServerClock), so they compare with the server's.
            val remote =
                if (queued && local != null) {
                    null
                } else {
                    apiCall { apis.api(server).progress(chapter.id) }
                        .getOrNull()
                        ?.takeIf {
                            local == null ||
                                parseUtc(it.lastModifiedUtc).let { t -> t == 0L || t >= local.lastModifiedUtc }
                        }
                }
            val pagesRead = remote?.pageNum ?: local?.pageNum ?: progress.chapterPagesRead(id, chapter.id) ?: 0
            // Our finer position still applies if the server agrees on the page.
            val position = local?.bookScrollId?.takeIf { local.pageNum == pagesRead && it.isAppPosition() }
            // A finished chapter starts over; otherwise continue where reading stopped.
            return if (pagesRead >=
                chapter.pages
            ) {
                ResumePosition(0)
            } else {
                ResumePosition(pagesRead.coerceAtLeast(0), position)
            }
        }

        override suspend fun record(
            server: Server,
            chapter: ReaderChapter,
            page: Int,
            position: String?,
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
                    position,
                    clock.now(id),
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

/** Kavita's own EPUB positions are XPaths; anything else (our Readium locators) stays on this device. */
private fun String.isAppPosition() = !startsWith(XPATH_PREFIX)

private const val XPATH_PREFIX = "//"

private fun ProgressEntity.toDto() =
    ProgressDto(
        volumeId,
        chapterId,
        pageNum,
        seriesId,
        libraryId,
        bookScrollId?.takeUnless { it.isAppPosition() },
        Instant.ofEpochMilli(lastModifiedUtc).toString(),
    )
