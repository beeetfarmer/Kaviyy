package com.nl.kaviyy.core.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import com.nl.kaviyy.core.database.DownloadEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DownloadNotificationsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notifications = DownloadNotifications(app)
    private val row = DownloadEntity(1, 10, 7, 5, "COMPLETED", true)

    init {
        // A launcher activity, as the app has, so the tap intent can be built.
        val main = ComponentName(app.packageName, "test.Main")
        shadowOf(app.packageManager).addActivityIfNotPresent(main)
        shadowOf(app.packageManager).addIntentFilterForActivity(
            main,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @Test
    fun finishedAndFailedAreSeparateFromProgressAndUseImmutableIntents() {
        notifications.finished(row, "Ember Road", ok = true)
        notifications.finished(row.copy(chapterId = 11), "Ember Road", ok = false)
        val posted = shadowOf(manager).allNotifications
        assertEquals(
            setOf<String?>("Downloaded", "Download failed"),
            posted.mapTo(HashSet<String?>()) { it.extras.getString(NotificationCompat.EXTRA_TITLE) },
        )
        assertTrue(posted.all { it.extras.getString(NotificationCompat.EXTRA_TEXT) == "Ember Road" })
        val progressId = notifications.progress(row).first
        assertTrue(shadowOf(manager).getNotification(progressId) == null) // the done notice outlives the worker's
        posted.forEach { n ->
            val intent = n.contentIntent
            assertTrue(intent != null && shadowOf(intent).flags and PendingIntent.FLAG_IMMUTABLE != 0)
        }
    }

    @Test
    fun nothingIsPostedWhenNotificationsAreOff() {
        shadowOf(manager).setNotificationsEnabled(false)
        notifications.finished(row, "Ember Road", ok = true)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
