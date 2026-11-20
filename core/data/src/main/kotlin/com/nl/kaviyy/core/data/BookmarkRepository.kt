package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.BookmarkDao
import com.nl.kaviyy.core.database.BookmarkEntity
import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.BookmarkDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Page bookmarks: Room first, then the server; failed pushes wait in the outbox (M17). */
interface BookmarkRepository {
    /** Bookmarked pages (0-based) of a chapter. */
    fun pages(
        server: Server,
        chapterId: Int,
    ): Flow<Set<Int>>

    suspend fun refresh(
        server: Server,
        chapterId: Int,
    ): AppResult<Unit>

    suspend fun set(
        server: Server,
        chapter: ReaderChapter,
        page: Int,
        on: Boolean,
    ): AppResult<Unit>
}

class OfflineFirstBookmarkRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val bookmarks: BookmarkDao,
        private val outbox: OutboxDao,
    ) : BookmarkRepository {
        override fun pages(
            server: Server,
            chapterId: Int,
        ) = bookmarks.observeChapter(server.id.value, chapterId).map { rows -> rows.map { it.page }.toSet() }

        override suspend fun refresh(
            server: Server,
            chapterId: Int,
        ): AppResult<Unit> {
            val id = server.id.value
            return when (val r = apiCall { apis.api(server).chapterBookmarks(chapterId) }) {
                is AppResult.Success -> {
                    // Pages with a queued change keep their local state until the outbox flushes.
                    val queued =
                        outbox
                            .pending(
                                id,
                            ).filter { it.type == OUTBOX_BOOKMARK }
                            .mapNotNull { key(it.entityKey, chapterId) }
                    val rows =
                        r.value.map {
                            BookmarkEntity(
                                id,
                                chapterId,
                                it.page,
                                it.seriesId,
                                it.volumeId,
                                it.imageOffset,
                                it.id,
                            )
                        }
                    bookmarks.replaceChapter(id, chapterId, rows, queued)
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> {
                    r
                }
            }
        }

        override suspend fun set(
            server: Server,
            chapter: ReaderChapter,
            page: Int,
            on: Boolean,
        ): AppResult<Unit> {
            val id = server.id.value
            if (on) {
                bookmarks.upsert(listOf(BookmarkEntity(id, chapter.id, page, chapter.seriesId, chapter.volumeId)))
            } else {
                bookmarks.delete(id, chapter.id, page)
            }
            val body = BookmarkDto(0, page, chapter.volumeId, chapter.seriesId, chapter.id)
            val payload =
                buildJsonObject {
                    put("chapterId", chapter.id)
                    put("seriesId", chapter.seriesId)
                    put("volumeId", chapter.volumeId)
                    put("page", page)
                    put("on", on)
                }.toString()
            return outbox.pushOrQueue(server, OUTBOX_BOOKMARK, "${chapter.id}:$page", payload) {
                val api = apis.api(server)
                if (on) api.bookmark(body) else api.unbookmark(body)
            }
        }

        /** Page of an outbox key "chapterId:page" when it belongs to [chapterId]. */
        private fun key(
            entityKey: String,
            chapterId: Int,
        ): Int? =
            entityKey
                .split(':')
                .takeIf { it.size == 2 && it[0] == "$chapterId" }
                ?.get(1)
                ?.toIntOrNull()
    }
