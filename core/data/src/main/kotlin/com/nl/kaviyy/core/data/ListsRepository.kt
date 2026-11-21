package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.CollectionDao
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Want to Read across every library, narrowed by the user's [filter]. */
fun wantToReadQuery(filter: SeriesFilter = SeriesFilter()) = LibraryQuery(null, filter, wantToRead = true)

/**
 * The Want to Read card: [count] series ([more] when the first page was full, so there may be more), the first
 * covers, and how many got a new chapter in the last week. From the cached first page, so it works offline.
 */
data class ListSummary(
    val count: Int,
    val more: Boolean,
    val coverIds: List<Int>,
    val updatedThisWeek: Int,
)

/** The series of one collection, across every library. */
fun collectionQuery(
    collectionId: Int,
    filter: SeriesFilter = SeriesFilter(),
) = LibraryQuery(
    null,
    filter.copy(
        rules =
            filter.rules +
                FilterRule(FilterField.CollectionTags, Comparison.Contains, listOf(RuleValue("$collectionId"))),
    ),
)

interface ListsRepository {
    fun wantToRead(server: Server): Flow<ListSummary>

    suspend fun refreshWantToRead(server: Server): AppResult<Unit>

    /** Collections the user can see (own, promoted and synced), most recently changed first. */
    fun collections(server: Server): Flow<List<CollectionEntity>>

    fun collection(
        server: Server,
        id: Int,
    ): Flow<CollectionEntity?>

    suspend fun refreshCollections(server: Server): AppResult<Unit>
}

private const val CARD_COVERS = 3
private val WEEK_MS = TimeUnit.DAYS.toMillis(7)

class OfflineFirstListsRepository
    @Inject
    constructor(
        private val apis: ServerApis,
        private val series: SeriesDao,
        private val collectionDao: CollectionDao,
    ) : ListsRepository {
        private val query = wantToReadQuery()

        override fun wantToRead(server: Server): Flow<ListSummary> =
            series.observeQuery(server.id.value, query.key).map { rows ->
                val since = System.currentTimeMillis() - WEEK_MS
                ListSummary(
                    count = rows.size,
                    more = rows.size >= SERIES_PAGE_SIZE,
                    coverIds = rows.take(CARD_COVERS).map { it.id },
                    updatedThisWeek = rows.count { it.lastChapterAdded >= since },
                )
            }

        override fun collections(server: Server) = collectionDao.observe(server.id.value)

        override fun collection(
            server: Server,
            id: Int,
        ) = collectionDao.observe(server.id.value, id)

        override suspend fun refreshCollections(server: Server): AppResult<Unit> {
            val id = server.id.value
            return apiCall { apis.api(server).collections() }.map { list ->
                collectionDao.replaceAll(
                    id,
                    list.mapIndexed { i, c ->
                        CollectionEntity(
                            id,
                            c.id,
                            c.title.orEmpty(),
                            c.summary,
                            c.promoted,
                            c.itemCount,
                            c.owner,
                            c.source,
                            i,
                        )
                    },
                )
            }
        }

        override suspend fun refreshWantToRead(server: Server): AppResult<Unit> {
            val id = server.id.value
            return apiCall { apis.api(server).wantToRead(query.toFilter(), 1, SERIES_PAGE_SIZE) }.map { page ->
                series.saveQueryPage(id, query.key, 0, page.map { it.toEntity(id) })
            }
        }
    }
