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
import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.LibraryDto
import com.nl.kaviyy.core.network.ReadingProfileDto
import com.nl.kaviyy.core.network.SeriesDetailDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReaderSettingsRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)

    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    private var effective = json("""{"id":1,"kind":0,"readingDirection":0,"futureField":"keep me"}""")
    private val saved = mutableListOf<JsonObject>()
    private var offline = false

    private val api =
        object : FakeKavitaApi() {
            override suspend fun libraries() = emptyList<LibraryDto>()

            override suspend fun readingProfile(
                libraryId: Int,
                seriesId: Int,
            ) = effective

            override suspend fun saveSeriesReadingProfile(
                libraryId: Int,
                seriesId: Int,
                profile: JsonObject,
            ): JsonObject {
                if (offline) throw IOException("offline")
                saved += profile
                return JsonObject(profile + ("id" to kotlinx.serialization.json.JsonPrimitive(40)))
            }
        }

    private val repo = OfflineFirstReaderSettingsRepository(db.readingProfileDao(), db.outboxDao(), { api })

    @After
    fun tearDown() = db.close()

    @Test
    fun defaultsUntilRefreshedThenServerProfile() =
        runTest {
            assertEquals(ReadingProfileDto(), repo.settings(server, 7).first())
            effective = json("""{"id":3,"kind":1,"readingDirection":1,"readerMode":2}""")
            repo.refresh(server, 1, 7)
            val s = repo.settings(server, 7).first()
            assertEquals(1, s.readingDirection)
            assertEquals(2, s.readerMode)
        }

    @Test
    fun defaultProfileIsTheFallbackForOtherSeries() =
        runTest {
            repo.refresh(server, 1, 7) // kind 0 = Default
            effective = json("""{"id":3,"kind":2,"readingDirection":1}""")
            repo.refresh(server, 1, 8)
            assertEquals(0, repo.settings(server, 9).first().readingDirection)
            assertEquals(1, repo.settings(server, 8).first().readingDirection)
        }

    @Test
    fun updatePushesOnlyKnownFieldsAndKeepsUnknownOnes() =
        runTest {
            repo.refresh(server, 1, 7)
            val result = repo.update(server, 1, 7, repo.settings(server, 7).first().copy(readingDirection = 1))
            assertEquals(AppResult.Success(Unit), result)
            val body = saved.single()
            assertEquals("keep me", body["futureField"]?.jsonPrimitive?.content)
            assertEquals(1, body["readingDirection"]?.jsonPrimitive?.int)
            assertEquals(0, body["id"]?.jsonPrimitive?.int) // base was the shared default, not an implicit profile
            assertEquals(1, repo.settings(server, 7).first().readingDirection)
            assertEquals(40, repo.settings(server, 7).first().id)
            assertTrue(db.outboxDao().pending(serverId).isEmpty())
        }

    @Test
    fun offlineEditIsLocalAndQueuedOncePerSeries() =
        runTest {
            offline = true
            repo.update(server, 1, 7, ReadingProfileDto(readingDirection = 1))
            val result = repo.update(server, 1, 7, ReadingProfileDto(readingDirection = 1, readerMode = 2))
            assertEquals(AppError.Network, assertIs<AppResult.Failure>(result).error)
            assertEquals(2, repo.settings(server, 7).first().readerMode)
            val pending = db.outboxDao().pending(serverId).single()
            assertEquals(OUTBOX_READING_PROFILE, pending.type)
            assertEquals("7", pending.entityKey)
            assertTrue(""""readerMode":2""" in pending.payload)
            offline = false
            repo.update(server, 1, 7, repo.settings(server, 7).first())
            assertTrue(db.outboxDao().pending(serverId).isEmpty())
        }

    @Test
    fun defaultProfileIsEditedInPlaceAndFollowersSeeIt() =
        runTest {
            api.profiles = listOf(json("""{"id":5,"kind":1}"""), json("""{"id":1,"kind":0,"futureField":"keep me"}"""))
            val raw = (repo.defaultProfile(server) as AppResult.Success).value
            assertEquals(1, raw["id"]?.jsonPrimitive?.int)
            repo.refresh(server, 1, 7) // series 7 follows the default (id 1)
            repo.saveDefault(server, raw, ReadingProfileDto.parse(raw.toString()).copy(readingDirection = 1))
            val body = api.savedProfiles.single()
            assertEquals("keep me", body["futureField"]?.jsonPrimitive?.content)
            assertEquals(1, body["id"]?.jsonPrimitive?.int)
            assertEquals(1, repo.settings(server, 7).first().readingDirection)
        }

    @Test
    fun noDefaultProfileIsNotFound() =
        runTest {
            api.profiles = listOf(json("""{"id":5,"kind":1}"""))
            assertEquals(
                AppResult.Failure(com.nl.kaviyy.core.model.result.AppError.NotFound),
                repo.defaultProfile(server),
            )
        }
}
