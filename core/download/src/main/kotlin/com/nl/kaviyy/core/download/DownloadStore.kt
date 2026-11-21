package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.database.DownloadDao
import com.nl.kaviyy.core.database.DownloadEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Download rows: every state change goes through [DownloadEntity.on], one at a time. */
@Singleton
class DownloadStore
    @Inject
    constructor(
        private val dao: DownloadDao,
        private val clock: Clock,
    ) {
        // ponytail: one lock for all rows; per-chapter locks if download throughput ever contends here.
        private val lock = Mutex()

        /** Queues a chapter unless it's already there (any state). Returns whether a row was added. */
        suspend fun enqueue(download: DownloadEntity): Boolean =
            lock.withLock {
                if (dao.get(download.serverId, download.chapterId) != null) return@withLock false
                dao.upsert(download.copy(state = DownloadState.QUEUED.name, updatedAt = clock.millis()))
                true
            }

        /** Applies [event]; returns the new row, or null when the row is gone or the transition isn't allowed. */
        suspend fun apply(
            serverId: Long,
            chapterId: Int,
            event: DownloadEvent,
        ): DownloadEntity? =
            lock.withLock {
                val next = dao.get(serverId, chapterId)?.on(event, clock.millis()) ?: return@withLock null
                dao.upsert(next)
                next
            }

        suspend fun remove(
            serverId: Long,
            chapterId: Int,
        ) = lock.withLock { dao.delete(serverId, chapterId) }
    }
