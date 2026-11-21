package com.nl.kaviyy.core.sync

import androidx.room.Room
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SyncTest {
    private val app = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(app, KaviyyDatabase::class.java).allowMainThreadQueries().build()

    init {
        val background = Executors.newSingleThreadExecutor()
        runCatching {
            WorkManager.initialize(
                app,
                Configuration
                    .Builder()
                    .setExecutor(background)
                    .setTaskExecutor(background)
                    .build(),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun server(id: Long) =
        Server(ServerId(id), "s$id", (ServerUrl.parse("https://s$id.lan") as ServerUrlResult.Valid).url)

    @Test
    fun everyServerIsFlushedAndOneFailureMeansRetry() =
        runTest {
            val flushed = mutableListOf<Long>()
            val ok =
                syncAll(listOf(server(1), server(2))) {
                    flushed += it.id.value
                    AppResult.Success(Unit)
                }
            assertTrue(ok)
            flushed.clear()
            val retry =
                syncAll(listOf(server(1), server(2))) {
                    flushed += it.id.value
                    if (it.id.value == 1L) AppResult.Failure(AppError.Network) else AppResult.Success(Unit)
                }
            assertFalse(retry)
            assertEquals(listOf(1L, 2L), flushed) // the second server still synced
        }

    @Test
    fun startSchedulesThePeriodicJobAndRequestNowWaitsForANetwork() {
        val sync = Sync(app, db.outboxDao(), Dispatchers.Unconfined)
        sync.start()
        sync.requestNow()
        val work = WorkManager.getInstance(app)
        val periodic = work.getWorkInfosForUniqueWork(Sync.PERIODIC).get().single()
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
        val now = work.getWorkInfosForUniqueWork(Sync.NOW).get().single()
        assertEquals(NetworkType.CONNECTED, now.constraints.requiredNetworkType)
        assertEquals(WorkInfo.State.ENQUEUED, now.state)
    }
}
