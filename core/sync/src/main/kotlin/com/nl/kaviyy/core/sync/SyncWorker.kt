package com.nl.kaviyy.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.OutboxReplayer
import com.nl.kaviyy.core.data.ProgressPuller
import com.nl.kaviyy.core.data.SmartCollections
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Flushes every signed-in server's outbox, then pulls their progress (failures retry with exponential backoff),
 * then brings smart collections up to date.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun session(): AuthSession

        fun replayer(): OutboxReplayer

        fun puller(): ProgressPuller

        fun smartCollections(): SmartCollections
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        // Push first, so the pull never overwrites a change that hasn't reached the server.
        val done =
            syncAll(deps.session().servers.first()) { server ->
                deps.replayer().flush(server).let { if (it is AppResult.Success) deps.puller().pull(server) else it }
            }
        // Best effort: smart collections pick up newly tagged series; a failure waits for the next run.
        deps
            .session()
            .servers
            .first()
            .forEach { deps.smartCollections().syncAll(it) }
        return if (done) Result.success() else Result.retry()
    }
}

/** True when every server's queue was emptied; one server failing doesn't stop the others. */
internal suspend fun syncAll(
    servers: List<Server>,
    flush: suspend (Server) -> AppResult<Unit>,
): Boolean = servers.map { flush(it) }.all { it is AppResult.Success }
