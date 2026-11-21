package com.nl.kaviyy.core.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pause / Cancel from the download notification. Not exported: only our own PendingIntents reach it. */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var downloads: Downloads

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val serverId = intent.getLongExtra(EXTRA_SERVER, -1)
        val chapterId = intent.getIntExtra(EXTRA_CHAPTER, -1)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    PAUSE -> downloads.pause(serverId, chapterId)
                    CANCEL -> downloads.cancel(serverId, chapterId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val PAUSE = "com.nl.kaviyy.download.PAUSE"
        const val CANCEL = "com.nl.kaviyy.download.CANCEL"
        const val EXTRA_SERVER = "serverId"
        const val EXTRA_CHAPTER = "chapterId"
    }
}
