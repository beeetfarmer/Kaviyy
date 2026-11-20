package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.OutboxEntity
import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.MarkReadDto
import com.nl.kaviyy.core.network.MarkVolumeReadDto
import com.nl.kaviyy.core.network.MarkVolumesReadDto
import com.nl.kaviyy.core.network.UpdateRatingDto
import com.nl.kaviyy.core.network.UpdateWantToReadDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject

/** What a mark read/unread applies to. */
sealed interface ReadTarget {
    val seriesId: Int

    /** Outbox key: the latest change per target wins. */
    val key: String

    data class Series(
        override val seriesId: Int,
    ) : ReadTarget {
        override val key get() = "series:$seriesId"
    }

    data class Volume(
        override val seriesId: Int,
        val volumeId: Int,
    ) : ReadTarget {
        override val key get() = "volume:$volumeId"
    }

    data class Chapter(
        override val seriesId: Int,
        val chapterId: Int,
    ) : ReadTarget {
        override val key get() = "chapter:$chapterId"
    }
}

/**
 * Read status and Want to Read. Room is updated first (the UI changes at once, offline too), then the server; a failed
 * push is queued in the outbox (coalesced per target) for M17's SyncWorker. A success returns [AppResult.Success];
 * a queued change returns the push's failure so callers can say "will sync".
 */
interface ReadStateRepository {
    suspend fun setRead(
        server: Server,
        target: ReadTarget,
        read: Boolean,
    ): AppResult<Unit>

    suspend fun setWantToRead(
        server: Server,
        seriesId: Int,
        on: Boolean,
    ): AppResult<Unit>

    /** 0–5 stars; 0 clears. */
    suspend fun setRating(
        server: Server,
        seriesId: Int,
        rating: Float,
    ): AppResult<Unit>
}

internal const val OUTBOX_MARK_READ = "markRead"
internal const val OUTBOX_WANT_TO_READ = "wantToRead"
internal const val OUTBOX_RATING = "rating"
private const val MAX_RATING = 5f

class OfflineFirstReadStateRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val series: SeriesDao,
        private val outbox: OutboxDao,
    ) : ReadStateRepository {
        override suspend fun setRead(
            server: Server,
            target: ReadTarget,
            read: Boolean,
        ): AppResult<Unit> {
            val id = server.id.value
            val chapters =
                when (target) {
                    is ReadTarget.Series -> series.chapterIdsOfSeries(id, target.seriesId)
                    is ReadTarget.Volume -> series.chapterIdsOfVolume(id, target.volumeId)
                    is ReadTarget.Chapter -> listOf(target.chapterId)
                }
            series.applyRead(id, target.seriesId, chapters, read)
            val payload =
                buildJsonObject {
                    put("target", target.key)
                    put("seriesId", target.seriesId)
                    put("read", read)
                }.toString()
            return push(server, OUTBOX_MARK_READ, target.key, payload) { api -> api.markRead(target, read) }
        }

        override suspend fun setWantToRead(
            server: Server,
            seriesId: Int,
            on: Boolean,
        ): AppResult<Unit> {
            series.setWantToRead(server.id.value, seriesId, on)
            val payload =
                buildJsonObject {
                    put("seriesId", seriesId)
                    put("on", on)
                }.toString()
            return push(server, OUTBOX_WANT_TO_READ, "series:$seriesId", payload) { api ->
                val body = UpdateWantToReadDto(listOf(seriesId))
                if (on) api.addWantToRead(body) else api.removeWantToRead(body)
            }
        }

        override suspend fun setRating(
            server: Server,
            seriesId: Int,
            rating: Float,
        ): AppResult<Unit> {
            val stars = rating.coerceIn(0f, MAX_RATING)
            series.setUserRating(server.id.value, seriesId, stars)
            val payload =
                buildJsonObject {
                    put("seriesId", seriesId)
                    put("userRating", stars)
                }.toString()
            return push(
                server,
                OUTBOX_RATING,
                "series:$seriesId",
                payload,
            ) { api -> api.rateSeries(UpdateRatingDto(seriesId, stars)) }
        }

        private suspend fun push(
            server: Server,
            type: String,
            key: String,
            payload: String,
            call: suspend (KavitaApi) -> Unit,
        ): AppResult<Unit> {
            val id = server.id.value
            val result = apiCall { call(apis.api(server)) }
            if (result is AppResult.Success) {
                outbox.deleteFor(id, type, key) // an older queued change is now superseded
            } else {
                outbox.enqueue(
                    OutboxEntity(0, id, type, key, payload, UUID.randomUUID().toString(), System.currentTimeMillis()),
                )
            }
            return result
        }
    }

private suspend fun KavitaApi.markRead(
    target: ReadTarget,
    read: Boolean,
) {
    when (target) {
        is ReadTarget.Series -> {
            if (read) markSeriesRead(MarkReadDto(target.seriesId)) else markSeriesUnread(MarkReadDto(target.seriesId))
        }

        is ReadTarget.Volume -> {
            val body = MarkVolumeReadDto(target.seriesId, target.volumeId)
            if (read) markVolumeRead(body) else markVolumeUnread(body)
        }

        is ReadTarget.Chapter -> {
            val body = MarkVolumesReadDto(target.seriesId, emptyList(), listOf(target.chapterId))
            if (read) markMultipleRead(body) else markMultipleUnread(body)
        }
    }
}
