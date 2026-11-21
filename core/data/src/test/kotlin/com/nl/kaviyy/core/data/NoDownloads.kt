package com.nl.kaviyy.core.data

import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import kotlinx.coroutines.Dispatchers
import org.robolectric.RuntimeEnvironment

/** A [DownloadedContent] with nothing downloaded (Robolectric tests; an empty in-memory database). */
internal fun noDownloads(): DownloadedContent {
    val app = RuntimeEnvironment.getApplication()
    val db = Room.inMemoryDatabaseBuilder(app, KaviyyDatabase::class.java).allowMainThreadQueries().build()
    return DownloadedContent(app, db.downloadDao(), Dispatchers.Unconfined)
}
