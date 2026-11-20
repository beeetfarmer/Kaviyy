package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.AnnotationDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AnnotationRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val serverId = runBlocking { db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan")) }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
    private val chapter = ReaderChapter(201, 7, 5, 3, "Book", "1", "1", null, false, pages = 12, format = 3)
    private val calls = mutableListOf<String>()
    private val bodies = mutableListOf<AnnotationDto>()
    private var offline = false
    private var remote = listOf<AnnotationDto>()
    private var nextId = 100

    private val api =
        object : FakeKavitaApi() {
            private fun log(s: String) {
                if (offline) throw IOException("offline")
                calls += s
            }

            override suspend fun annotations(chapterId: Int) = remote

            override suspend fun createAnnotation(body: AnnotationDto): AnnotationDto {
                log("create")
                bodies += body
                return body.copy(id = nextId++)
            }

            override suspend fun updateAnnotation(body: AnnotationDto): AnnotationDto {
                log("update ${body.id}")
                bodies += body
                return body
            }

            override suspend fun deleteAnnotation(annotationId: Int) = log("delete $annotationId")
        }
    private val outbox = db.outboxDao()
    private val repo = OfflineFirstAnnotationRepository({ api }, db.annotationDao(), outbox)
    private val draft =
        AnnotationDraft(page = 2, text = "the lantern", xPath = "//BODY/P[1]", color = 1)

    @After
    fun tearDown() = db.close()

    private fun dto(id: Int) =
        AnnotationDto(id, "//BODY/P[$id]", null, "text $id", null, null, 6, false, 1, 0, 201, 5, 7, 3, 9)

    @Test
    fun createUpdateDeleteWriteLocallyThenPush() =
        runTest {
            repo.create(server, chapter, draft)
            val row = repo.annotations(server, 201).first().single()
            assertEquals(Highlight(row.uuid, 2, "the lantern", 1, null), row)
            assertEquals(100, db.annotationDao().get(serverId, row.uuid)?.remoteId)
            val sent = bodies.single()
            assertEquals(Triple(3, 2, 1), Triple(sent.libraryId, sent.pageNumber, sent.selectedSlotIndex))
            repo.update(server, chapter, row.uuid, color = 3, note = "  ")
            val updated = repo.annotations(server, 201).first().single()
            assertEquals(3 to null, updated.color to updated.note)
            repo.delete(server, row.uuid)
            assertEquals(listOf("create", "update 100", "delete 100"), calls)
            assertTrue(repo.annotations(server, 201).first().isEmpty())
            assertTrue(outbox.pending(serverId).isEmpty())
        }

    @Test
    fun offlineChangesQueueAndSurviveARefresh() =
        runTest {
            remote = listOf(dto(1), dto(2))
            repo.refresh(server, 201)
            val synced = db.annotationDao().chapter(serverId, 201).sortedBy { it.remoteId }
            assertEquals(listOf(1, 2), synced.map { it.remoteId })
            offline = true
            repo.create(server, chapter, draft) // queued create
            repo.delete(server, synced.first().clientUuid) // queued delete of #1
            assertEquals(2, outbox.pending(serverId).size)
            offline = false
            repo.refresh(server, 201) // server still has #1 and #2
            val rows = db.annotationDao().chapter(serverId, 201)
            assertEquals(setOf(2, null), rows.map { it.remoteId }.toSet()) // #1 stays deleted, the new one stays
            // Stable ids across refreshes.
            assertEquals(synced[1].clientUuid, rows.first { it.remoteId == 2 }.clientUuid)
        }

    @Test
    fun deletingAnUnsyncedHighlightDropsItsQueuedCreate() =
        runTest {
            offline = true
            repo.create(server, chapter, draft)
            val uuid =
                repo
                    .annotations(server, 201)
                    .first()
                    .single()
                    .uuid
            repo.delete(server, uuid)
            assertTrue(outbox.pending(serverId).isEmpty())
            assertTrue(calls.isEmpty())
        }
}
