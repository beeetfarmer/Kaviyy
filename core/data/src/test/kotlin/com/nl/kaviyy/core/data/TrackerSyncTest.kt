package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.SeriesMetadataEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.datastore.TrackerAccount
import com.nl.kaviyy.core.datastore.TrackerFile
import com.nl.kaviyy.core.datastore.TrackerStore
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.tracker.AniListApi
import com.nl.kaviyy.core.network.tracker.KitsuApi
import com.nl.kaviyy.core.network.tracker.MalApi
import com.nl.kaviyy.core.network.tracker.TrackerApi
import com.nl.kaviyy.core.network.tracker.TrackerLogin
import com.nl.kaviyy.core.network.tracker.TrackerMedia
import com.nl.kaviyy.core.testing.InMemoryDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class TrackerSyncTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val store = TrackerStore(InMemoryDataStore(TrackerFile()))

    /** A fake tracker: [catalog] to search, [list] the user's progress per media id. */
    private class FakeTracker(
        val catalog: List<TrackerMedia>,
        val list: MutableMap<Int, Int> = mutableMapOf(),
    ) : TrackerApi {
        val searches = mutableListOf<String>()
        val saves = mutableListOf<Pair<Int, Int>>()

        override suspend fun search(
            login: TrackerLogin,
            title: String,
        ) = catalog.also { searches += title }

        override suspend fun progress(
            login: TrackerLogin,
            mediaId: Int,
        ) = list[mediaId]

        override suspend fun save(
            login: TrackerLogin,
            mediaId: Int,
            chapters: Int,
        ) {
            saves += mediaId to chapters
            list[mediaId] = chapters
        }
    }

    private val fakeAniList =
        FakeTracker(listOf(TrackerMedia(1, listOf("Other Road")), TrackerMedia(2, listOf("EMBER ROAD!"))))
    private val fakeMal = FakeTracker(listOf(TrackerMedia(9, listOf("Ember Road: Ashes"))))
    private val http = OkHttpClient()
    private val apis =
        object : TrackerApis(
            AniListApi(http, Dispatchers.Unconfined),
            MalApi(http, Dispatchers.Unconfined),
            KitsuApi(http, Dispatchers.Unconfined),
        ) {
            override fun of(tracker: Tracker): TrackerApi = if (tracker == Tracker.AniList) fakeAniList else fakeMal
        }
    private val sync = TrackerSync(store, apis, db.seriesDao())

    @After
    fun tearDown() = db.close()

    private suspend fun seed(readThrough: Int) {
        db.seriesDao().replaceSeriesDetail(
            SeriesEntity(serverId, 7, 1, "Ember Road", "ember road", format = 0, pages = 60, pagesRead = 0),
            listOf(VolumeEntity(serverId, 5, 7, "1", 1f, 1f, pages = 60, pagesRead = 0)),
            (1..3).map { n ->
                ChapterEntity(
                    serverId,
                    10 + n,
                    5,
                    7,
                    range = "$n",
                    pages = 20,
                    pagesRead =
                        if (n <=
                            readThrough
                        ) {
                            20
                        } else {
                            4
                        },
                    format = 0,
                )
            } + ChapterEntity(serverId, 99, 5, 7, range = "50", pages = 5, pagesRead = 5, isSpecial = true, format = 0),
        )
        store.update {
            it.copy(
                accounts =
                    mapOf(
                        "AniList" to TrackerAccount("neo", accessToken = "a"),
                        "Mal" to TrackerAccount("neo", accessToken = "m"),
                    ),
            )
        }
    }

    @Test
    fun finishedSeriesSendTheHighestFinishedChapterToConfidentMatchesOnly() =
        runTest {
            seed(readThrough = 2)
            sync.finished(serverId, 7)
            sync.syncAll(listOf(server))
            assertEquals(listOf(2 to 2), fakeAniList.saves) // exact title (case/punctuation ignored); special ignored
            assertEquals(emptyList(), fakeMal.saves) // "Ember Road: Ashes" isn't the same series: no guess
            assertEquals(emptyList(), store.snapshot().pending)
            assertEquals(0, store.snapshot().matches["Mal|$serverId|7"]) // remembered: no search next time
        }

    @Test
    fun neverLowersProgressAndSkipsWhatWasAlreadySent() =
        runTest {
            seed(readThrough = 2)
            fakeAniList.list[2] = 5 // further on AniList already
            sync.finished(serverId, 7)
            sync.syncAll(listOf(server))
            assertEquals(emptyList(), fakeAniList.saves)
            sync.finished(serverId, 7)
            sync.syncAll(listOf(server))
            assertEquals(1, fakeAniList.searches.size) // match cached
        }

    @Test
    fun aKavitaLinkBeatsSearching() =
        runTest {
            seed(readThrough = 3)
            db.seriesDao().upsertMetadata(
                SeriesMetadataEntity(serverId, 7, webLinks = "https://myanimelist.net/manga/4242/ember_road"),
            )
            sync.finished(serverId, 7)
            sync.syncAll(listOf(server))
            assertEquals(listOf(4242 to 3), fakeMal.saves)
            assertEquals(0, fakeMal.searches.size)
        }

    @Test
    fun nothingIsQueuedWithoutAConnectedTracker() =
        runTest {
            sync.finished(serverId, 7)
            assertEquals(emptyList(), store.snapshot().pending)
        }

    @Test
    fun linksAndTitlesAreParsedStrictly() {
        assertEquals(30013, linkedId(Tracker.AniList, "https://anilist.co/manga/30013/One-Piece"))
        assertEquals(12, linkedId(Tracker.Kitsu, "https://kitsu.app/manga/12, https://x"))
        assertNull(linkedId(Tracker.Kitsu, "https://kitsu.app/manga/one-piece"))
        assertNull(linkedId(Tracker.Mal, "https://anilist.co/manga/1"))
        assertEquals("emberroad", normalize("Ember  Road!"))
        assertEquals(
            2,
            listOf(
                TrackerMedia(1, listOf("Ember")),
                TrackerMedia(2, listOf("ember road")),
            ).exactMatch(listOf("Ember Road"))?.id,
        )
    }
}
