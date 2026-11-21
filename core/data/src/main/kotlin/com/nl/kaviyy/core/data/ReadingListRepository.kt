package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ReadingListDao
import com.nl.kaviyy.core.database.ReadingListEntity
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ReadingListDto
import com.nl.kaviyy.core.network.ReadingListItemDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Lists shows this many reading lists; their items are fetched too so the rows show progress. */
const val READING_LISTS_PREVIEW = 5

/** Reading lists, cached in Room (offline-first); changes are online-only. */
interface ReadingListRepository {
    fun lists(server: Server): Flow<List<ReadingListRow>>

    fun list(
        server: Server,
        id: Int,
    ): Flow<ReadingListRow?>

    fun items(
        server: Server,
        id: Int,
    ): Flow<List<ReadingListItemEntity>>

    /** Lists, plus the items of the first [READING_LISTS_PREVIEW] for their progress. */
    suspend fun refreshLists(server: Server): AppResult<Unit>

    suspend fun refreshItems(
        server: Server,
        id: Int,
    ): AppResult<Unit>
}

/** The first item not fully read, else the first (read again). */
fun List<ReadingListItemEntity>.next(): ReadingListItemEntity? =
    firstOrNull { it.pagesTotal == 0 || it.pagesRead < it.pagesTotal } ?: firstOrNull()

class OfflineFirstReadingListRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val dao: ReadingListDao,
    ) : ReadingListRepository {
        override fun lists(server: Server) = dao.observe(server.id.value)

        override fun list(
            server: Server,
            id: Int,
        ) = dao.observe(server.id.value, id)

        override fun items(
            server: Server,
            id: Int,
        ) = dao.items(server.id.value, id)

        override suspend fun refreshLists(server: Server): AppResult<Unit> =
            apiCall { apis.api(server).readingLists() }.map { lists ->
                dao.replaceAll(server.id.value, lists.mapIndexed { i, l -> l.toEntity(server.id.value, i) })
                lists.take(READING_LISTS_PREVIEW).forEach { refreshItems(server, it.id) }
            }

        override suspend fun refreshItems(
            server: Server,
            id: Int,
        ): AppResult<Unit> =
            apiCall { apis.api(server).readingListItems(id) }.map { items ->
                dao.replaceItems(server.id.value, id, items.map { it.toEntity(server.id.value, id) })
            }
    }

private fun ReadingListDto.toEntity(
    serverId: Long,
    position: Int,
) = ReadingListEntity(
    serverId,
    id,
    title.orEmpty(),
    summary,
    promoted,
    itemCount,
    startingYear,
    startingMonth,
    endingYear,
    endingMonth,
    ownerUserName,
    position,
)

private fun ReadingListItemDto.toEntity(
    serverId: Long,
    listId: Int,
) = ReadingListItemEntity(
    serverId,
    id,
    listId,
    order,
    chapterId,
    volumeId,
    seriesId,
    title,
    pagesRead,
    pagesTotal,
    seriesName,
    seriesFormat,
)
