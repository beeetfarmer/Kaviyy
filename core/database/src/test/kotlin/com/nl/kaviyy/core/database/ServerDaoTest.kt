package com.nl.kaviyy.core.database

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ServerDaoTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                KaviyyDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private val dao = db.serverDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun upsertByIdOrdersByLastUsedAndMapsToModel() =
        runTest {
            val a = dao.upsert(ServerEntity(name = "A", url = "https://a.lan", lastUsedAt = 1))
            dao.upsert(ServerEntity(name = "B", url = "https://b.lan", lastUsedAt = 2))
            dao.upsert(checkNotNull(dao.byId(a)).copy(lastUsedAt = 3, version = "0.9.0.2"))
            val all = dao.observeAll().first()
            assertEquals(listOf("A", "B"), all.map { it.name })
            assertEquals("0.9.0.2", all.first().toModel()?.version)
            assertEquals(a, dao.byUrl("https://a.lan")?.id)
            dao.delete(a)
            assertNull(dao.byId(a))
        }

    @Test
    fun corruptUrlRowsAreSkippedNotCrashing() {
        assertNull(ServerEntity(name = "X", url = "ftp://nope").toModel())
    }
}
