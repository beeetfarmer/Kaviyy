package com.nl.kaviyy.core.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Periodic: queue the next unread On Deck chapters (the chapter jobs then download under their own constraints). */
class AutoDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun autoDownloader(): AutoDownloader
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).autoDownloader().runAll()
        return Result.success()
    }
}
