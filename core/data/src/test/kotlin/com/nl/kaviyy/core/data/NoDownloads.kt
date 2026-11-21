package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.DownloadDao
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.database.OutdatedDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.robolectric.RuntimeEnvironment

/** A [DownloadedContent] with nothing downloaded (Robolectric tests). */
internal fun noDownloads() =
    DownloadedContent(
        RuntimeEnvironment.getApplication(),
        object : DownloadDao {
            override fun observe(serverId: Long): Flow<List<DownloadEntity>> = emptyFlow()

            override fun observeSeries(
                serverId: Long,
                seriesId: Int,
            ): Flow<List<DownloadEntity>> = emptyFlow()

            override suspend fun get(
                serverId: Long,
                chapterId: Int,
            ): DownloadEntity? = null

            override suspend fun inState(state: String): List<DownloadEntity> = emptyList()

            override suspend fun totalBytes(): Long = 0

            override fun observeOutdated(
                serverId: Long,
                seriesId: Int,
            ): Flow<List<OutdatedDownload>> = emptyFlow()

            override suspend fun upsert(download: DownloadEntity) = Unit

            override suspend fun delete(
                serverId: Long,
                chapterId: Int,
            ) = Unit
        },
        Dispatchers.Unconfined,
    )
