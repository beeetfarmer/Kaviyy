package com.nl.kaviyy.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.OutboxReplayer
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** Flushes every signed-in server's outbox; anything left over is retried with exponential backoff. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun session(): AuthSession

        fun replayer(): OutboxReplayer
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val done = syncAll(deps.session().servers.first()) { deps.replayer().flush(it) }
        return if (done) Result.success() else Result.retry()
    }
}

/** True when every server's queue was emptied; one server failing doesn't stop the others. */
internal suspend fun syncAll(
    servers: List<Server>,
    flush: suspend (Server) -> AppResult<Unit>,
): Boolean = servers.map { flush(it) }.all { it is AppResult.Success }
