package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NewChapterCheckTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private var list = emptyList<SeriesDto>()
    private var offline = false
    private val api =
        object : FakeKavitaApi() {
            override suspend fun wantToRead(
                filter: SeriesFilterV2Dto,
                pageNumber: Int,
                pageSize: Int,
            ): List<SeriesDto> {
                if (offline) throw IOException("offline")
                return list
            }
        }
    private val check = NewChapterCheck({ api }, db.seriesDao())

    @After
    fun tearDown() = db.close()

    private fun series(
        id: Int,
        name: String,
        added: String,
    ) = SeriesDto(id = id, libraryId = 1, name = name, lastChapterAddedUtc = added)

    @Test
    fun onlySeriesAlreadySeenThatGainedAChapterAreReported() =
        runTest {
            list = listOf(series(1, "Ember Road", "2026-11-01T00:00:00Z"), series(2, "Salt", "2026-11-01T00:00:00Z"))
            assertEquals(AppResult.Success(emptyList()), check.check(server)) // first sight: recorded only
            list = listOf(series(1, "Ember Road", "2026-11-20T00:00:00Z"), series(2, "Salt", "2026-11-01T00:00:00Z"))
            assertEquals(AppResult.Success(listOf("Ember Road")), check.check(server))
            assertEquals(AppResult.Success(emptyList()), check.check(server)) // reported once
        }

    @Test
    fun offlineReportsTheError() =
        runTest {
            offline = true
            assertEquals(AppResult.Failure(AppError.Network), check.check(server))
        }
}
