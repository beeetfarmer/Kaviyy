package com.nl.kaviyy.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nl.kaviyy.core.database.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The ongoing download notification with Pause / Cancel (explicit, immutable PendingIntents). */
@Singleton
class DownloadNotifications
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun progress(row: DownloadEntity): Pair<Int, Notification> {
            ensureChannel()
            val id = notificationId(row.serverId, row.chapterId)
            val (done, total) =
                if (row.pageImages) {
                    row.pagesDone.toLong() to row.pagesTotal.toLong()
                } else {
                    row.bytesDone to
                        row.bytesTotal
                }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_download_notification)
                    .setContentTitle(context.getString(R.string.download_notification_title))
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setSilent(true)
                    .setProgress(PROGRESS_MAX, if (total > 0) (done * PROGRESS_MAX / total).toInt() else 0, total <= 0)
                    .addAction(
                        0,
                        context.getString(R.string.download_pause),
                        action(row, DownloadActionReceiver.PAUSE, id),
                    ).addAction(
                        0,
                        context.getString(R.string.download_cancel),
                        action(
                            row,
                            DownloadActionReceiver.CANCEL,
                            id + 1,
                        ),
                    ).build()
            return id to notification
        }

        /** [derived] One notification per server listing the series; tapping opens the app. */
        fun newChapters(
            serverId: Long,
            names: List<String>,
        ) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    NEW_CHAPTERS,
                    context.getString(R.string.new_chapters_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val text = names.joinToString(", ")
            val notification =
                NotificationCompat
                    .Builder(context, NEW_CHAPTERS)
                    .setSmallIcon(R.drawable.ic_download_notification)
                    .setContentTitle(context.getString(R.string.new_chapters_title))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .build()
            // One per server, replacing itself; a clash with a chapter id only swaps which notice shows.
            runCatching {
                NotificationManagerCompat
                    .from(
                        context,
                    ).notify("$NEW_CHAPTERS:$serverId".hashCode(), notification)
            }
        }

        private fun openApp(): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }

        /** Completed or failed; tapping opens the app. Skipped when the user hasn't allowed notifications. */
        fun finished(
            row: DownloadEntity,
            seriesName: String?,
            ok: Boolean,
        ) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            ensureChannel()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_download_notification)
                    .setContentTitle(
                        context.getString(if (ok) R.string.download_done else R.string.download_failed),
                    ).setContentText(seriesName)
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .setGroup(CHANNEL)
                    .build()
            // Odd id: never the (even) progress id, which WorkManager removes when the worker ends.
            runCatching {
                NotificationManagerCompat
                    .from(
                        context,
                    ).notify(notificationId(row.serverId, row.chapterId) + 1, notification)
            }
        }

        private fun action(
            row: DownloadEntity,
            action: String,
            requestCode: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, DownloadActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(DownloadActionReceiver.EXTRA_SERVER, row.serverId)
                    .putExtra(DownloadActionReceiver.EXTRA_CHAPTER, row.chapterId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun ensureChannel() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.download_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        private companion object {
            const val CHANNEL = "downloads"
            const val NEW_CHAPTERS = "new_chapters"
            const val PROGRESS_MAX = 100
            const val ID_PRIME = 1_000_003L
            const val EVEN_POSITIVE = 0x7FFFFFFE

            // Two ids per chapter (notification + cancel request code); collisions only mix up a progress bar.
            fun notificationId(
                serverId: Long,
                chapterId: Int,
            ) = (serverId * ID_PRIME + chapterId).hashCode() and EVEN_POSITIVE
        }
    }
