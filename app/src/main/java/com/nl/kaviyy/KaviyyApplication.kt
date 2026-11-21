package com.nl.kaviyy

import android.app.Application
import com.nl.kaviyy.core.download.AutoDownloader
import com.nl.kaviyy.core.download.NewChapterWorker
import com.nl.kaviyy.core.download.ReadCleanup
import com.nl.kaviyy.core.model.log.Log
import com.nl.kaviyy.core.sync.Sync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KaviyyApplication : Application() {
    @Inject lateinit var autoDownloader: AutoDownloader

    @Inject lateinit var readCleanup: ReadCleanup

    @Inject lateinit var sync: Sync

    override fun onCreate() {
        super.onCreate()
        autoDownloader.start()
        NewChapterWorker.schedule(this)
        readCleanup.start()
        sync.start()
        // Release builds never set a sink, so logging is a no-op there (and R8 strips the calls).
        if (BuildConfig.DEBUG) {
            Log.sink = { level, tag, message, _ ->
                val priority =
                    when (level) {
                        Log.Level.Debug -> android.util.Log.DEBUG
                        Log.Level.Info -> android.util.Log.INFO
                        Log.Level.Warn -> android.util.Log.WARN
                        Log.Level.Error -> android.util.Log.ERROR
                    }
                android.util.Log.println(priority, tag, message)
            }
        }
    }
}
