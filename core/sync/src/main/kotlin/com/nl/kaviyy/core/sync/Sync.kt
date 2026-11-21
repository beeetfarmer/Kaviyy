package com.nl.kaviyy.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [SyncWorker]: once now (waits for a network, so it also covers "back online"), whenever changes are
 * queued, on app foreground, and periodically as a safety net.
 */
@Singleton
class Sync
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val outbox: OutboxDao,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        // Not initialised in host-side tests of the Application; the app's startup provider always initialises it.
        private val work get() = runCatching { WorkManager.getInstance(context) }.getOrNull()

        /** Call once from the Application. */
        fun start() {
            work?.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS).setConstraints(online).build(),
            )
            outbox
                .observeCount()
                .map { it > 0 }
                .distinctUntilChanged()
                .filter { it }
                .onEach { requestNow() }
                .launchIn(CoroutineScope(SupervisorJob() + io))
        }

        /** Flush as soon as there is a network; a pending request is kept (its backoff too). */
        fun requestNow() {
            work?.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(online)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )
        }

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        internal companion object {
            const val NOW = "sync-now"
            const val PERIODIC = "sync-periodic"
            private const val PERIOD_HOURS = 3L
            private const val BACKOFF_SECONDS = 30L
        }
    }
