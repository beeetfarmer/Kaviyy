package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ReadingListDao
import com.nl.kaviyy.core.database.ReadingListEntity
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.result.onSuccess
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.CblDecisionsDto
import com.nl.kaviyy.core.network.CblFinalizeDto
import com.nl.kaviyy.core.network.CblSavedFileDto
import com.nl.kaviyy.core.network.CblValidateDto
import com.nl.kaviyy.core.network.CreateReadingListDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.ReadingListDto
import com.nl.kaviyy.core.network.ReadingListItemDto
import com.nl.kaviyy.core.network.ReadingListPositionDto
import com.nl.kaviyy.core.network.ReadingListSeriesDto
import com.nl.kaviyy.core.network.UpdateReadingListDto
import com.nl.kaviyy.core.network.apiCall
import com.nl.kaviyy.core.network.saveCbl
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Lists shows this many reading lists; their items are fetched too so the rows show progress. */
const val READING_LISTS_PREVIEW = 5

/** Reading lists, cached in Room (offline-first); changes are online-only. */
@Suppress("TooManyFunctions") // one function per reading list operation
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

    /** Ids of the reading lists that already hold some of [seriesId] (network). */
    suspend fun listsWith(
        server: Server,
        seriesId: Int,
    ): AppResult<Set<Int>>

    /** The new list's id. */
    suspend fun create(
        server: Server,
        title: String,
    ): AppResult<Int>

    suspend fun update(
        server: Server,
        id: Int,
        title: String,
        summary: String?,
    ): AppResult<Unit>

    suspend fun delete(
        server: Server,
        id: Int,
    ): AppResult<Unit>

    suspend fun move(
        server: Server,
        item: ReadingListItemEntity,
        to: Int,
    ): AppResult<Unit>

    suspend fun remove(
        server: Server,
        item: ReadingListItemEntity,
    ): AppResult<Unit>

    suspend fun addSeries(
        server: Server,
        id: Int,
        seriesId: Int,
    ): AppResult<Unit>

    /** Uploads a CBL file and matches it against the library; nothing is created yet. */
    suspend fun previewCbl(
        server: Server,
        fileName: String,
        bytes: ByteArray,
    ): AppResult<CblPreview>

    /** Creates the reading list from a previewed file; the new list's id. Unmatched issues are skipped. */
    suspend fun importCbl(
        server: Server,
        preview: CblPreview,
    ): AppResult<Int>
}

/** A matched CBL file: [found] of [total] issues are in the library; [missing] are labels of the rest. */
data class CblPreview(
    val name: String,
    val found: Int,
    val total: Int,
    val missing: List<String>,
    /** Kavita found at least one issue. */
    val importable: Boolean,
    internal val saved: CblSavedFileDto,
)

private const val CBL_MATCHED = 8
private const val CBL_FAILED = 0

/** Only the owner changes a reading list (promoted lists from others are read-only). */
fun ReadingListEntity.editableBy(server: Server): Boolean =
    owner != null && owner.equals(server.username, ignoreCase = true)

/** The first item not fully read, else the first (read again). */
fun List<ReadingListItemEntity>.next(): ReadingListItemEntity? =
    firstOrNull { it.pagesTotal == 0 || it.pagesRead < it.pagesTotal } ?: firstOrNull()

@Suppress("TooManyFunctions") // one function per reading list operation
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

        override suspend fun listsWith(
            server: Server,
            seriesId: Int,
        ) = apiCall { apis.api(server).readingListsWithSeries(seriesId) }.map { l -> l.map { it.id }.toSet() }

        override suspend fun create(
            server: Server,
            title: String,
        ): AppResult<Int> =
            apiCall { apis.api(server).createReadingList(CreateReadingListDto(title.trim())).id }
                .onSuccess { refreshLists(server) }

        /** Fetches the list first so the fields we don't edit go back exactly as they are. */
        override suspend fun update(
            server: Server,
            id: Int,
            title: String,
            summary: String?,
        ) = changed(server, null) {
            val l = readingList(id)
            updateReadingList(
                UpdateReadingListDto(
                    id,
                    title.trim(),
                    summary?.trim()?.ifEmpty { null },
                    l.promoted,
                    l.coverImageLocked,
                    l.startingMonth,
                    l.startingYear,
                    l.endingMonth,
                    l.endingYear,
                    l.tags,
                ),
            )
        }

        override suspend fun delete(
            server: Server,
            id: Int,
        ) = changed(server, null) { deleteReadingList(id) }.onSuccess { dao.delete(server.id.value, id) }

        override suspend fun move(
            server: Server,
            item: ReadingListItemEntity,
            to: Int,
        ) = changed(server, item.readingListId) {
            moveReadingListItem(ReadingListPositionDto(item.readingListId, item.id, item.position, to))
        }

        override suspend fun remove(
            server: Server,
            item: ReadingListItemEntity,
        ) = changed(server, item.readingListId) {
            deleteReadingListItem(ReadingListPositionDto(item.readingListId, item.id))
        }

        override suspend fun addSeries(
            server: Server,
            id: Int,
            seriesId: Int,
        ) = changed(server, id) { addSeriesToReadingList(ReadingListSeriesDto(seriesId, id)) }

        override suspend fun previewCbl(
            server: Server,
            fileName: String,
            bytes: ByteArray,
        ): AppResult<CblPreview> =
            apiCall {
                val api = apis.api(server)
                val saved = api.saveCbl(fileName, bytes)
                val summary = api.validateCbl(CblValidateDto(saved.fileName ?: fileName))
                val missing = summary.results.filter { it.reason != CBL_MATCHED }
                val found = summary.successfulInserts.size.takeIf { it > 0 } ?: (summary.results.size - missing.size)
                CblPreview(
                    summary.cblName ?: saved.name ?: fileName,
                    found,
                    found + missing.size,
                    missing.map { m -> listOfNotNull(m.series, m.number?.let { "#$it" }).joinToString(" ") },
                    summary.success != CBL_FAILED && found > 0,
                    saved,
                )
            }

        override suspend fun importCbl(
            server: Server,
            preview: CblPreview,
        ): AppResult<Int> =
            apiCall {
                val s = preview.saved
                apis
                    .api(server)
                    .finalizeCbl(
                        CblFinalizeDto(
                            s.fileName.orEmpty(),
                            CblDecisionsDto(),
                            s.provider,
                            s.repoPath,
                            s.downloadUrl,
                            s.sha,
                        ),
                    ).readingListId
            }.onSuccess { refreshLists(server) }

        /**
         * Reading list changes are online-only (ponytail: no outbox; Kavita validates names and ownership). Afterwards
         * the lists, and [items] of that list, are refreshed from the server.
         */
        private suspend fun changed(
            server: Server,
            items: Int?,
            call: suspend KavitaApi.() -> Unit,
        ): AppResult<Unit> =
            apiCall { apis.api(server).call() }.onSuccess {
                refreshLists(server)
                items?.let { refreshItems(server, it) }
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
