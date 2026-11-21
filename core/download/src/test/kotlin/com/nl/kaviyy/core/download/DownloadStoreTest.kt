package com.nl.kaviyy.core.download

import androidx.room.Room
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DownloadStoreTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val store = DownloadStore(db.downloadDao(), Clock.fixed(Instant.ofEpochMilli(42), ZoneOffset.UTC))

    @After
    fun tearDown() = db.close()

    @Test
    fun enqueueOnceThenTransitionAndRemove() =
        runTest {
            val row = DownloadEntity(serverId, 7, 1, 2, "ignored", pageImages = true)
            assertTrue(store.enqueue(row))
            assertFalse(store.enqueue(row))
            assertEquals(
                DownloadState.QUEUED to 42L,
                db.downloadDao().get(serverId, 7)?.let {
                    it.downloadState to
                        it.updatedAt
                },
            )
            assertEquals(DownloadState.RUNNING, store.apply(serverId, 7, DownloadEvent.Start)?.downloadState)
            assertNull(store.apply(serverId, 7, DownloadEvent.Resume)) // not paused: ignored
            assertEquals(DownloadState.RUNNING, db.downloadDao().get(serverId, 7)?.downloadState)
            store.remove(serverId, 7)
            assertNull(store.apply(serverId, 7, DownloadEvent.Pause))
        }
}
