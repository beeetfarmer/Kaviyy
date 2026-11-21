package com.nl.kaviyy.core.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.NewChapterCheck
import com.nl.kaviyy.core.model.result.AppResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Periodic: notify about new chapters in Want to Read series (users mute it via the system channel settings). */
class NewChapterWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun session(): AuthSession

        fun check(): NewChapterCheck

        fun notifications(): DownloadNotifications
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        for (server in deps.session().servers.first()) {
            val names = (deps.check().check(server) as? AppResult.Success)?.value.orEmpty()
            if (names.isNotEmpty()) deps.notifications().newChapters(server.id.value, names)
        }
        return Result.success() // a missed check is caught up by the next period
    }

    companion object {
        private const val WORK_NAME = "new-chapters"
        private const val PERIOD_HOURS = 12L

        /** Call once from the Application. */
        fun schedule(context: Context) {
            // Not initialised in host-side tests of the Application; the app's startup provider always initialises it.
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            work.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NewChapterWorker>(
                    PERIOD_HOURS,
                    TimeUnit.HOURS,
                ).setConstraints(online).build(),
            )
        }
    }
}
