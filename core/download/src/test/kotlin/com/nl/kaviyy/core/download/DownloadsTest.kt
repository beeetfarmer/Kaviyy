package com.nl.kaviyy.core.download

import androidx.room.Room
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.nl.kaviyy.core.data.DownloadedContent
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.OutdatedDownload
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.testing.FakeKavitaApi
import com.nl.kaviyy.core.testing.InMemoryDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Clock
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class DownloadsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val db =
        Room
            .inMemoryDatabaseBuilder(app, KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val url = (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url
    private val reader = Server(ServerId(serverId), "k", url, "0.9.0.2")
    private val downloader = reader.copy(roles = setOf("Download"))
    private val prefs = InMemoryDataStore(AppSettings())
    private val store = DownloadStore(db.downloadDao(), Clock.systemUTC())
    private val files =
        ChapterDownloader(
            DownloadedContent(app, db.downloadDao(), Dispatchers.Unconfined),
            ServerApis {
                FakeKavitaApi()
            },
            Dispatchers.Unconfined,
        )
    private val downloads = Downloads(app, store, files, db.downloadDao(), AppSettingsStore(prefs))

    init {
        val background = Executors.newSingleThreadExecutor()
        runCatching {
            // the singleton survives between tests in one JVM
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

    private fun chapter(
        id: Int,
        bytes: Long = 0,
    ) = DownloadRequest(id, 2, 3, format = 1, bytes = bytes)

    private fun job(chapterId: Int) =
        WorkManager
            .getInstance(app)
            .getWorkInfosForUniqueWork(Downloads.name(serverId, chapterId))
            .get()
            .singleOrNull()

    @Test
    fun withoutTheDownloadRoleNothingIsQueued() =
        runTest {
            assertEquals(EnqueueResult.NoPermission, downloads.enqueue(reader, listOf(chapter(1))))
            assertNull(store.get(serverId, 1))
        }

    @Test
    fun queuesNewChaptersOnceWithTheNetworkPolicy() =
        runTest {
            assertEquals(EnqueueResult.Queued, downloads.enqueue(downloader, listOf(chapter(1), chapter(2))))
            assertEquals(EnqueueResult.NothingNew, downloads.enqueue(downloader, listOf(chapter(1))))
            val constraints = job(1)?.constraints
            assertEquals(NetworkType.UNMETERED, constraints?.requiredNetworkType)
            assertEquals(true, constraints?.requiresStorageNotLow())
            prefs.updateData { it.copy(downloadWifiOnly = false) }
            downloads.enqueue(downloader, listOf(chapter(3)))
            assertEquals(NetworkType.CONNECTED, job(3)?.constraints?.requiredNetworkType)
        }

    @Test
    fun theStorageLimitCountsWhatIsAlreadyDownloaded() =
        runTest {
            prefs.updateData { it.copy(storageLimitMb = 10) }
            val mb = 1024L * 1024
            assertEquals(EnqueueResult.Queued, downloads.enqueue(downloader, listOf(chapter(1, 6 * mb))))
            db.downloadDao().upsert(checkNotNull(store.get(serverId, 1)).copy(bytesDone = 6 * mb))
            assertEquals(EnqueueResult.OverLimit, downloads.enqueue(downloader, listOf(chapter(2, 5 * mb))))
            assertEquals(EnqueueResult.Queued, downloads.enqueue(downloader, listOf(chapter(2, 4 * mb))))
        }

    @Test
    fun cancelRemovesTheRowAndFiles() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(1)))
            val dir = files.dir(serverId, 2, 1).apply { mkdirs() }
            File(dir, "0").writeText("x")
            downloads.cancel(serverId, 1)
            assertNull(store.get(serverId, 1))
            assertFalse(dir.exists())
        }

    @Test
    fun pauseAndResumeGoThroughTheStateMachine() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(1)))
            downloads.pause(serverId, 1)
            assertEquals(DownloadState.PAUSED, store.get(serverId, 1)?.downloadState)
            downloads.resume(serverId, 1)
            assertEquals(DownloadState.QUEUED, store.get(serverId, 1)?.downloadState)
        }

    @Test
    fun aChapterChangedOnTheServerIsOfferedAsAnUpdateAndRequeued() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(1).copy(lastModified = 1_000)))
            store.apply(serverId, 1, DownloadEvent.Start)
            store.apply(serverId, 1, DownloadEvent.Complete(1_000))
            val cached = ChapterEntity(serverId, 1, 3, 2, pages = 1, pagesRead = 0, format = 1, lastModified = 1_000)
            db.seriesDao().upsertChapters(listOf(cached))
            assertEquals(emptyList(), downloads.outdated(downloader, 2).first())
            db.seriesDao().upsertChapters(listOf(cached.copy(lastModified = 2_000)))
            val outdated = downloads.outdated(downloader, 2).first()
            assertEquals(listOf(OutdatedDownload(1, 2_000)), outdated)
            downloads.update(downloader, outdated)
            assertEquals(
                DownloadState.QUEUED to 2_000L,
                store.get(serverId, 1)?.let {
                    it.downloadState to
                        it.serverLastModified
                },
            )
            assertEquals(emptyList(), downloads.outdated(downloader, 2).first())
        }

    @Test
    fun theDownloadsListCarriesSeriesNamesAndChapters() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(1), chapter(5)))
            db.seriesDao().upsertSeries(
                listOf(SeriesEntity(serverId, 2, 1, "Ember Road", "ember road", format = 1, pages = 1, pagesRead = 0)),
            )
            db.seriesDao().upsertChapters(
                listOf(ChapterEntity(serverId, 1, 3, 2, range = "49", pages = 1, pagesRead = 0, format = 1)),
            )
            val items = downloads.downloads(downloader).first().associateBy { it.row.chapterId }
            assertEquals("Ember Road" to "49", items[1]?.let { it.seriesName to it.chapter?.range })
            assertNull(items[5]?.chapter) // not cached yet
        }

    @Test
    fun turningWifiOnlyOffRequeuesWaitingDownloadsOnAnyNetwork() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(1)))
            assertEquals(NetworkType.UNMETERED, job(1)?.constraints?.requiredNetworkType)
            downloads.setWifiOnly(false)
            assertFalse(prefs.data.first().downloadWifiOnly)
            val jobs = WorkManager.getInstance(app).getWorkInfosForUniqueWork(Downloads.name(serverId, 1)).get()
            assertEquals(NetworkType.CONNECTED, jobs.last().constraints.requiredNetworkType)
        }

    @Test
    fun clearAllRemovesEveryRowAndFile() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(1), chapter(2)))
            val dir = files.dir(serverId, 2, 1).apply { mkdirs() }
            File(dir, "0").writeText("x")
            downloads.clearAll()
            assertEquals(0L, db.downloadDao().totalBytes())
            assertNull(store.get(serverId, 2))
            assertFalse(dir.exists())
        }

    @Test
    fun removeReadDeletesOnlyFinishedChaptersThatAreFullyRead() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(21), chapter(22), chapter(23)))
            listOf(21, 22).forEach {
                store.apply(serverId, it, DownloadEvent.Start)
                store.apply(serverId, it, DownloadEvent.Complete(0))
            }
            db.seriesDao().upsertChapters(
                listOf(
                    ChapterEntity(serverId, 21, 3, 2, pages = 10, pagesRead = 10, format = 1), // read
                    ChapterEntity(serverId, 22, 3, 2, pages = 10, pagesRead = 4, format = 1), // half read
                    ChapterEntity(serverId, 23, 3, 2, pages = 10, pagesRead = 10, format = 1), // read, still queued
                ),
            )
            downloads.removeRead(serverId)
            assertNull(store.get(serverId, 21))
            assertEquals(DownloadState.COMPLETED, store.get(serverId, 22)?.downloadState)
            assertEquals(DownloadState.QUEUED, store.get(serverId, 23)?.downloadState)
        }

    @Test
    fun askingAgainForAFailedChapterRetriesIt() =
        runTest {
            downloads.enqueue(downloader, listOf(chapter(31)))
            store.apply(serverId, 31, DownloadEvent.Start)
            store.apply(serverId, 31, DownloadEvent.Fail("network"))
            assertEquals(EnqueueResult.Queued, downloads.enqueue(downloader, listOf(chapter(31))))
            assertEquals(DownloadState.QUEUED, store.get(serverId, 31)?.downloadState)
        }
}
