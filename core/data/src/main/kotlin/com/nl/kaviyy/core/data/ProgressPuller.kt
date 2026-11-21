package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.ProgressDao
import com.nl.kaviyy.core.database.ProgressEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ProgressDto
import com.nl.kaviyy.core.network.apiCall
import com.nl.kaviyy.core.network.parseUtc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings other devices' reading into Room for chapters that matter offline (in progress, downloaded): the server's
 * position replaces ours when it is newer, or when it carries no time (the server wins, as on open).
 */
@Singleton
class ProgressPuller
    @Inject
    constructor(
        private val apis: ServerApis,
        private val progress: ProgressDao,
        private val outbox: OutboxDao,
    ) {
        // ponytail: one request per chapter, capped; a bulk progress endpoint would replace this loop.
        suspend fun pull(server: Server): AppResult<Unit> {
            val id = server.id.value
            val api = apis.api(server)
            val queued = outbox.pending(id).filter { it.type == OUTBOX_PROGRESS }.mapTo(HashSet()) { it.entityKey }
            for (chapterId in progress.syncCandidates(id, MAX_CHAPTERS)) {
                if ("$chapterId" in queued) continue // ours is newer and on its way
                when (val remote = apiCall { api.progress(chapterId) }) {
                    is AppResult.Failure -> return remote
                    is AppResult.Success -> reconcile(id, remote.value)
                }
            }
            return AppResult.Success(Unit)
        }

        private suspend fun reconcile(
            serverId: Long,
            remote: ProgressDto,
        ) {
            val local = progress.get(serverId, remote.chapterId)
            val remoteTime = parseUtc(remote.lastModifiedUtc)
            val newer = local == null || remoteTime == 0L || remoteTime > local.lastModifiedUtc
            val changed = local?.pageNum != remote.pageNum
            if (newer && changed) {
                progress.save(
                    ProgressEntity(
                        serverId,
                        remote.chapterId,
                        remote.volumeId,
                        remote.seriesId,
                        remote.libraryId,
                        remote.pageNum,
                        remote.bookScrollId,
                        remoteTime,
                    ),
                )
            }
        }

        private companion object {
            const val MAX_CHAPTERS = 100
        }
    }
