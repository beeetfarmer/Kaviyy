package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.DashboardDao
import com.nl.kaviyy.core.database.DashboardItemEntity
import com.nl.kaviyy.core.database.DashboardStreamEntity
import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.DashboardStreamDto
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

/** Kavita `DashboardStreamType`. */
object StreamType {
    const val ON_DECK = 1
    const val RECENTLY_UPDATED = 2
    const val NEWLY_ADDED = 3
    const val SMART_FILTER = 4
}

data class HomeItem(
    val seriesId: Int,
    val libraryId: Int,
    val title: String,
    val pages: Int,
    val pagesRead: Int,
    /** Recently Updated: new chapters. */
    val newChapters: Int = 0,
    /** On Deck: the chapter to resume. */
    val chapter: ChapterEntity? = null,
) {
    val progress: Float get() = if (pages > 0) (pagesRead.toFloat() / pages).coerceIn(0f, 1f) else 0f
}

data class HomeRail(
    val streamId: Int,
    val type: Int,
    /** Server name; only meaningful for smart filters (built-ins are localised by [type]). */
    val name: String,
    val items: List<HomeItem>,
)

data class Home(
    val continueReading: HomeItem?,
    val rails: List<HomeRail>,
) {
    val isEmpty: Boolean get() = continueReading == null && rails.all { it.items.isEmpty() }
}

internal const val RAIL_SIZE = 20

/** Kavita `FilterEntityType.Series`. */
private const val ENTITY_SERIES = 0

/** Visible stream types this app renders (On Deck … Smart Filter); others (e.g. More In Genre) are skipped. */
private fun DashboardStreamDto.toEntity(serverId: Long): DashboardStreamEntity? =
    takeIf { visible && streamType in StreamType.ON_DECK..StreamType.SMART_FILTER }?.let {
        DashboardStreamEntity(serverId, id, name.orEmpty(), order, streamType, true, smartFilterEncoded)
    }

/** Continue-point lookups per refresh (one request each), for the On Deck subtitles and the Continue card. */
internal const val CONTINUE_LOOKUPS = 10

/** Home (screen 02): observe [home]; [refresh] fetches every stream. Interface = test seam for ViewModels. */
interface HomeRepository {
    fun home(server: Server): Flow<Home>

    /** Refreshes every stream; returns the first failure (the others still update). */
    suspend fun refresh(server: Server): AppResult<Unit>
}

/** Room-backed [HomeRepository]: the cache survives offline and failed refreshes. */
class OfflineFirstHomeRepository
    @Inject
    constructor(
        private val dashboard: DashboardDao,
        private val series: SeriesDao,
        private val apis: ServerApis,
    ) : HomeRepository {
        override fun home(server: Server): Flow<Home> {
            val id = server.id.value
            return combine(
                dashboard.observeStreams(id),
                dashboard.observeItems(id),
                dashboard.observeSeries(id),
                dashboard.observeChapters(id),
                ::assemble,
            )
        }

        override suspend fun refresh(server: Server): AppResult<Unit> {
            val id = server.id.value
            val api = apis.api(server)
            val layout = apiCall { api.dashboard() }
            val streams =
                when (layout) {
                    is AppResult.Success -> {
                        layout.value.mapNotNull { it.toEntity(id) }.also { dashboard.replaceStreams(id, it) }
                    }

                    // Offline / failed: keep the cached layout; first run falls back to the built-ins.
                    is AppResult.Failure -> {
                        dashboard.streams(id).ifEmpty { builtInStreams(id).also { dashboard.replaceStreams(id, it) } }
                    }
                }
            val results = listOf(layout.map {}) + streams.map { refreshStream(id, api, it) }
            return results.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
        }

        private suspend fun refreshStream(
            serverId: Long,
            api: KavitaApi,
            stream: DashboardStreamEntity,
        ): AppResult<Unit> {
            fun item(
                position: Int,
                seriesId: Int,
            ) = DashboardItemEntity(serverId, stream.id, position, seriesId)
            return when (stream.streamType) {
                StreamType.ON_DECK -> {
                    apiCall { api.onDeck(1, RAIL_SIZE) }.map { list ->
                        val chapters = continuePoints(serverId, api, list.take(CONTINUE_LOOKUPS).map { it.id })
                        series.upsertSeries(list.map { it.toEntity(serverId) })
                        series.upsertChapters(chapters.filterNotNull())
                        val rows = list.mapIndexed { i, s -> item(i, s.id).copy(chapterId = chapters.getOrNull(i)?.id) }
                        dashboard.replaceItems(serverId, stream.id, rows)
                    }
                }

                StreamType.RECENTLY_UPDATED -> {
                    apiCall { api.recentlyUpdated(1, RAIL_SIZE) }.map { list ->
                        dashboard.insertSeriesIfAbsent(
                            list.map {
                                SeriesEntity(
                                    serverId = serverId,
                                    id = it.seriesId,
                                    libraryId = it.libraryId,
                                    name = it.seriesName.orEmpty(),
                                    sortName = it.seriesName.orEmpty(),
                                    format = it.format,
                                    pages = 0,
                                    pagesRead = 0,
                                )
                            },
                        )
                        val rows = list.mapIndexed { i, g -> item(i, g.seriesId).copy(count = g.count) }
                        dashboard.replaceItems(serverId, stream.id, rows)
                    }
                }

                StreamType.NEWLY_ADDED -> {
                    apiCall { api.recentlyAdded(SeriesFilterV2Dto(), 1, RAIL_SIZE) }.map { list ->
                        series.upsertSeries(list.map { it.toEntity(serverId) })
                        dashboard.replaceItems(serverId, stream.id, list.mapIndexed { i, s -> item(i, s.id) })
                    }
                }

                StreamType.SMART_FILTER -> {
                    stream.smartFilterEncoded?.let { refreshSmartFilter(serverId, api, stream.id, it) }
                        ?: AppResult.Success(Unit)
                }

                else -> {
                    AppResult.Success(Unit)
                }
            }
        }

        /** Decode the stream's filter on the server, then list its series. Non-series filters are skipped. */
        private suspend fun refreshSmartFilter(
            serverId: Long,
            api: KavitaApi,
            streamId: Int,
            encoded: String,
        ): AppResult<Unit> {
            val decoded = apiCall { api.decodeFilter(DecodeFilterDto(encoded)) }
            val filter = decoded.getOrNull() ?: return decoded.map {}
            val isSeries = ((filter["entityType"] as? JsonPrimitive)?.intOrNull ?: ENTITY_SERIES) == ENTITY_SERIES
            val list =
                if (isSeries) {
                    apiCall {
                        api.seriesByFilter(
                            filter,
                            1,
                            RAIL_SIZE,
                        )
                    }
                } else {
                    AppResult.Success(emptyList())
                }
            return list.map { found ->
                series.upsertSeries(found.map { it.toEntity(serverId) })
                val rows = found.mapIndexed { i, s -> DashboardItemEntity(serverId, streamId, i, s.id) }
                dashboard.replaceItems(serverId, streamId, rows)
            }
        }

        private suspend fun continuePoints(
            serverId: Long,
            api: KavitaApi,
            seriesIds: List<Int>,
        ): List<ChapterEntity?> =
            coroutineScope {
                seriesIds
                    .map { seriesId ->
                        async { apiCall { api.continuePoint(seriesId) }.getOrNull()?.toEntity(serverId, seriesId) }
                    }.awaitAll()
            }

        /** Used only before the server's layout has ever loaded (first refresh while offline). */
        private fun builtInStreams(serverId: Long) =
            listOf(StreamType.ON_DECK, StreamType.RECENTLY_UPDATED, StreamType.NEWLY_ADDED).mapIndexed { i, type ->
                DashboardStreamEntity(serverId, id = -type, name = "", position = i, streamType = type, visible = true)
            }
    }

private fun assemble(
    streams: List<DashboardStreamEntity>,
    items: List<DashboardItemEntity>,
    series: List<SeriesEntity>,
    chapters: List<ChapterEntity>,
): Home {
    val seriesById = series.associateBy { it.id }
    val chapterById = chapters.associateBy { it.id }
    val byStream = items.groupBy { it.streamId }
    val rails =
        streams.map { stream ->
            val rows =
                byStream[stream.id].orEmpty().mapNotNull { item ->
                    val s = seriesById[item.seriesId] ?: return@mapNotNull null
                    HomeItem(
                        seriesId = s.id,
                        libraryId = s.libraryId,
                        title = s.localizedName ?: s.name,
                        pages = s.pages,
                        pagesRead = s.pagesRead,
                        newChapters = item.count,
                        chapter = item.chapterId?.let(chapterById::get),
                    )
                }
            HomeRail(stream.id, stream.streamType, stream.name, rows)
        }
    val continueReading = rails.firstOrNull { it.type == StreamType.ON_DECK }?.items?.firstOrNull { it.chapter != null }
    return Home(continueReading, rails)
}
