package com.nl.kaviyy.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KaviyyDatabase::class.java)

    @Test
    fun migratesFromV1ToLatestKeepingServers() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO servers (id, name, url, allowInsecure, lastUsedAt) VALUES (7, 'k', 'https://k.lan', 0, 1)",
            )
        }
        // Runs every migration and validates against the latest exported schema.
        helper.runMigrationsAndValidate(DB, LATEST, true).use { db ->
            db.query("SELECT name FROM servers WHERE id = 7").use { c ->
                c.moveToFirst()
                assertEquals("k", c.getString(0))
            }
        }
    }

    @Test
    fun removingAServerCascadesToItsData() =
        runTest {
            val db =
                Room
                    .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            val a = db.serverDao().upsert(ServerEntity(name = "a", url = "https://a.lan"))
            val b = db.serverDao().upsert(ServerEntity(name = "b", url = "https://b.lan"))
            val raw = db.openHelper.writableDatabase
            listOf(a, b).forEach { raw.seed(it) }
            db.serverDao().delete(a)
            assertEquals(1, raw.count("series"))
            assertEquals(1, raw.count("reading_list_items")) // two levels down: servers → reading_lists → items
            assertEquals(
                listOf(b),
                db
                    .serverDao()
                    .observeAll()
                    .first()
                    .map { it.id },
            )
            db.close()
        }

    /** One series and one reading list with one item for [server]; unlisted NOT NULL columns get 0. */
    private fun SupportSQLiteDatabase.seed(server: Long) {
        insert("series", server, "libraryId", "format", "pages", "pagesRead", "wordCount", "userRating") {
            put("name", "s")
            put("sortName", "s")
            put("created", 0)
            put("lastChapterAdded", 0)
            put("latestReadDate", 0)
        }
        insert("reading_lists", server, "promoted", "itemCount", "startingYear", "startingMonth", "endingYear") {
            put("title", "r")
            put("endingMonth", 0)
        }
        insert("reading_list_items", server, "position", "chapterId", "volumeId", "seriesId") {
            put("readingListId", 1)
            put("pagesRead", 0)
            put("pagesTotal", 0)
        }
    }

    private fun SupportSQLiteDatabase.insert(
        table: String,
        server: Long,
        vararg zeroed: String,
        extra: ContentValues.() -> Unit,
    ) {
        val values =
            ContentValues().apply {
                put("serverId", server)
                put("id", 1)
                zeroed.forEach { put(it, 0) }
                extra()
            }
        insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private fun SupportSQLiteDatabase.count(table: String) =
        query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val DB = "migration-test"
        const val LATEST = 7
    }
}
