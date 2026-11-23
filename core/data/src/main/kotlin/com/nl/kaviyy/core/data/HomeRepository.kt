package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.DashboardDao
import com.nl.kaviyy.core.database.DashboardItemEntity
import com.nl.kaviyy.core.database.DashboardStreamEntity
import com.nl.kaviyy.core.database.OutboxDao
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.DashboardStreamDto
import com.nl.kaviyy.core.network.DecodeFilterDto
import com.nl.kaviyy.core.network.GenreTagDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterStatementDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.SeriesSortOptionDto
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

/** Kavita `DashboardStreamType`. */
object StreamType {
    const val ON_DECK = 1
    const val RECENTLY_UPDATED = 2
    const val NEWLY_ADDED = 3
    const val SMART_FILTER = 4

    /** Local only: the series of one collection / library the user put on Home. */
    const val COLLECTION = 101
    const val LIBRARY = 102

    /** Local only: Want to Read on Home, in the order chosen for it (the Want to Read page keeps its own). */
    const val WANT_TO_READ = 103

    /** Local only, never shown as rails: the series whose genres / tags match the blur / hide settings. */
    const val BLUR = 104
    const val HIDE = 105
}

/** Kavita `SeriesSortField.Random`. */
private const val SORT_RANDOM = 9

private const val COLLECTION_RAIL_BASE = 1_000_000
private const val LIBRARY_RAIL_BASE = 2_000_000

/** Local stream id of a collection or library rail (server stream ids are positive, built-ins -1…-3). */
fun itemRailId(
    section: HomeSection,
    id: Int,
): Int = -(if (section == HomeSection.Collection) COLLECTION_RAIL_BASE else LIBRARY_RAIL_BASE) - id

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
    /** Series with finished downloads: the first rail when offline. */
    val downloaded: List<HomeItem> = emptyList(),
    /** The cached first page of Want to Read. */
    val wantToRead: List<HomeItem> = emptyList(),
    /** Cached reading lists for their rail (server order; sorted in the UI). */
    val readingLists: List<ReadingListRow> = emptyList(),
    /** Series matching the blur tags. */
    val blurIds: Set<Int> = emptySet(),
    /** Series matching the hide tags (before the user's exceptions), A–Z. */
    val tagHidden: List<HomeItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            continueReading == null && rails.all { it.items.isEmpty() } && downloaded.isEmpty() &&
                wantToRead.isEmpty()

    fun rail(type: Int): HomeRail? = rails.firstOrNull { it.type == type }

    /** The rail of one collection / library on Home, once fetched. */
    fun itemRail(pref: HomeSectionPref): HomeRail? =
        rails.firstOrNull {
            it.streamId ==
                itemRailId(pref.section, pref.id)
        }

    val downloadedIds: Set<Int> by lazy { downloaded.mapTo(HashSet()) { it.seriesId } }

    /** Home without the series in [ids]; Continue Reading moves on to the next one. */
    fun without(ids: Set<Int>): Home {
        if (ids.isEmpty()) return this
        val keep = { item: HomeItem -> item.seriesId !in ids }
        val kept = rails.map { it.copy(items = it.items.filter(keep)) }
        return copy(
            continueReading = continuePick(kept),
            rails = kept,
            downloaded = downloaded.filter(keep),
            wantToRead = wantToRead.filter(keep),
        )
    }
}

/** The first On Deck series whose resume chapter isn't finished (a finished one waits for the next refresh). */
internal fun continuePick(rails: List<HomeRail>): HomeItem? =
    rails.firstOrNull { it.type == StreamType.ON_DECK }?.items?.firstOrNull { item ->
        item.chapter?.let { it.pages == 0 || it.pagesRead < it.pages } == true
    }

internal const val RAIL_SIZE = 20

/** Kavita `FilterEntityType.Series`. */
private const val ENTITY_SERIES = 0

/**
 * The server's visible smart-filter streams ("Show on Home"). The built-in rails are always kept locally instead:
 * which ones show, and where, is the user's Home layout in this app.
 */
private fun DashboardStreamDto.toEntity(serverId: Long): DashboardStreamEntity? =
    takeIf { visible && streamType == StreamType.SMART_FILTER }?.let {
        DashboardStreamEntity(serverId, id, name.orEmpty(), order, streamType, true, smartFilterEncoded)
    }

/** On Deck: series started but not finished, most recently read first. */
internal val ON_DECK_FILTER =
    SeriesFilterV2Dto(
        statements =
            listOf(
                SeriesFilterStatementDto(Comparison.GreaterThan.api, FilterField.ReadProgress.api, "0"),
                SeriesFilterStatementDto(Comparison.LessThan.api, FilterField.ReadProgress.api, "100"),
            ),
        // Kavita sorts "ReadProgress" by last read date.
        sortOptions = SeriesSortOptionDto(SeriesSort.ReadProgress.field, SeriesSort.ReadProgress.ascending),
    )

/** Continue-point lookups per refresh (one request each), for the On Deck subtitles and the Continue card. */
internal const val CONTINUE_LOOKUPS = 10

/** Tag-matched series are fetched in pages of this size, up to [TAGGED_MAX_PAGES]. */
internal const val TAGGED_PAGE_SIZE = 500
private const val TAGGED_MAX_PAGES = 20
private const val COMBINE_OR = 0

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
        private val lists: ListsRepository,
        private val readingLists: ReadingListRepository,
        private val catalog: CatalogRepository,
        private val settings: AppSettingsStore,
        private val progress: ProgressRepository,
        private val outbox: OutboxDao,
    ) : HomeRepository {
        override fun home(server: Server): Flow<Home> {
            val id = server.id.value
            return combine(
                dashboard.observeStreams(id),
                dashboard.observeItems(id),
                dashboard.observeSeries(id),
                dashboard.observeChapters(id),
                dashboard.observeDownloadedSeries(id),
                ::assemble,
            ).let { streams ->
                combine(streams, readingLists.lists(server)) { home, lists -> home.copy(readingLists = lists) }
            }
        }

        override suspend fun refresh(server: Server): AppResult<Unit> {
            val id = server.id.value
            val api = apis.api(server)
            // Positions still queued go first, so On Deck and the continue points reflect the latest reading.
            outbox
                .pending(id)
                .filter { it.type == OUTBOX_PROGRESS }
                .mapNotNull { it.entityKey.toIntOrNull() }
                .distinct()
                .forEach { progress.push(server, it) }
            val layout = apiCall { api.dashboard() }
            val smartFilters =
                when (layout) {
                    is AppResult.Success -> layout.value.mapNotNull { it.toEntity(id) }

                    // Offline / failed: keep the cached smart filters.
                    is AppResult.Failure -> dashboard.streams(id).filter { it.streamType == StreamType.SMART_FILTER }
                }.sortedBy { it.position }
            // Lists first: the collection / library rails take their names from them.
            val shelves =
                coroutineScope {
                    listOf(
                        async { lists.refreshWantToRead(server) },
                        async { lists.refreshCollections(server) },
                        async { readingLists.refreshLists(server) },
                        async { catalog.refreshLibraries(server) },
                    ).awaitAll()
                }
            val streams =
                (builtInStreams(id) + smartFilters + itemStreams(server))
                    .mapIndexed { i, stream -> stream.copy(position = i) }
                    .also { dashboard.replaceStreams(id, it) }
            val app = settings.settings.first()
            val orders = homeOrders(app)
            val tagged =
                listOf(StreamType.BLUR to app.blurTags, StreamType.HIDE to app.hideTags).map { (type, names) ->
                    refreshTagged(id, api, -type, names)
                }
            val results =
                listOf(layout.map {}) + streams.map { refreshStream(id, api, it, orders) } + tagged + shelves
            return results.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
        }

        private suspend fun refreshStream(
            serverId: Long,
            api: KavitaApi,
            stream: DashboardStreamEntity,
            orders: Map<String, SectionOrder>,
        ): AppResult<Unit> {
            val order = stream.pref()?.let(orders::orderOf) ?: SectionOrder.Default

            fun item(
                position: Int,
                seriesId: Int,
            ) = DashboardItemEntity(serverId, stream.id, position, seriesId)
            return when (stream.streamType) {
                StreamType.ON_DECK -> {
                    apiCall { api.series(ON_DECK_FILTER, 1, RAIL_SIZE) }.map { list ->
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

                StreamType.COLLECTION, StreamType.LIBRARY, StreamType.WANT_TO_READ -> {
                    refreshSeriesList(serverId, api, stream, order)
                }

                StreamType.SMART_FILTER -> {
                    stream.smartFilterEncoded?.let { refreshSmartFilter(serverId, api, stream.id, it, order) }
                        ?: AppResult.Success(Unit)
                }

                else -> {
                    AppResult.Success(Unit)
                }
            }
        }

        /** A collection, library or Want to Read rail, in [order]. */
        private suspend fun refreshSeriesList(
            serverId: Long,
            api: KavitaApi,
            stream: DashboardStreamEntity,
            order: SectionOrder,
        ): AppResult<Unit> {
            val list =
                if (stream.streamType == StreamType.WANT_TO_READ) {
                    val filter = SeriesFilterV2Dto(sortOptions = order.sortOptions(null))
                    apiCall { api.wantToRead(filter, 1, RAIL_SIZE) }
                } else {
                    apiCall { api.series(itemFilter(stream, order), 1, RAIL_SIZE) }
                }
            return list.map { found ->
                series.upsertSeries(found.map { it.toEntity(serverId) })
                dashboard.replaceItems(
                    serverId,
                    stream.id,
                    found.mapIndexed { i, s -> DashboardItemEntity(serverId, stream.id, i, s.id) },
                )
            }
        }

        /** Every series with any of the genres / tags [names], kept under the local stream [streamId]. */
        private suspend fun refreshTagged(
            serverId: Long,
            api: KavitaApi,
            streamId: Int,
            names: List<String>,
        ): AppResult<Unit> {
            val found = if (names.isEmpty()) AppResult.Success(emptyList()) else apiCall { fetchTagged(api, names) }
            return found.map { list ->
                series.upsertSeries(list.map { it.toEntity(serverId) })
                dashboard.replaceItems(
                    serverId,
                    streamId,
                    list.mapIndexed { i, s -> DashboardItemEntity(serverId, streamId, i, s.id) },
                )
            }
        }

        private suspend fun fetchTagged(
            api: KavitaApi,
            names: List<String>,
        ): List<SeriesDto> {
            val wanted = names.mapTo(HashSet()) { it.lowercase() }
            val filter = taggedFilter(wanted, api.genres(), api.tags()) ?: return emptyList()
            val found = mutableListOf<SeriesDto>()
            var page = 1
            do {
                val list = api.series(filter, page++, TAGGED_PAGE_SIZE)
                found += list
            } while (list.size == TAGGED_PAGE_SIZE && page <= TAGGED_MAX_PAGES)
            return found
        }

        /** Decode the stream's filter on the server, then list its series. Non-series filters are skipped. */
        private suspend fun refreshSmartFilter(
            serverId: Long,
            api: KavitaApi,
            streamId: Int,
            encoded: String,
            order: SectionOrder,
        ): AppResult<Unit> {
            val decoded = apiCall { api.decodeFilter(DecodeFilterDto(encoded)) }
            val saved = decoded.getOrNull() ?: return decoded.map {}
            // A chosen order replaces the one saved with the filter.
            val filter =
                order.sortOptions(null)?.let { sort ->
                    val json = sortJson.encodeToJsonElement(SeriesSortOptionDto.serializer(), sort)
                    JsonObject(saved + ("sortOptions" to json))
                } ?: saved
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

        /** A stream per collection / library switched on in the Home layout that still exists. */
        private suspend fun itemStreams(server: Server): List<DashboardStreamEntity> {
            val id = server.id.value
            val collections = lists.collections(server).first().associate { it.id to it.title }
            val libraries = catalog.libraries(server).first().associate { it.id to it.name }
            return parseHomeLayout(settings.settings.first().homeLayout)
                .filter { it.visible && it.section.perItem }
                .mapNotNull { pref ->
                    val collection = pref.section == HomeSection.Collection
                    val name = (if (collection) collections else libraries)[pref.id] ?: return@mapNotNull null
                    val type = if (collection) StreamType.COLLECTION else StreamType.LIBRARY
                    DashboardStreamEntity(id, itemRailId(pref.section, pref.id), name, 0, type, visible = true)
                }
        }

        /**
         * On Deck, Recently Updated / Added and Want to Read, with fixed local ids (server ids are positive), plus the
         * hidden blur / hide streams (filled by [refreshTagged]).
         */
        private fun builtInStreams(serverId: Long) =
            listOf(StreamType.ON_DECK, StreamType.RECENTLY_UPDATED, StreamType.NEWLY_ADDED, StreamType.WANT_TO_READ)
                .mapIndexed { i, type -> DashboardStreamEntity(serverId, -type, "", i, type, visible = true) } +
                listOf(StreamType.BLUR, StreamType.HIDE).map {
                    DashboardStreamEntity(serverId, -it, "", 0, it, visible = false)
                }
    }

/** A collection's or library's series in [order]; by default newest first. */
internal fun itemFilter(
    stream: DashboardStreamEntity,
    order: SectionOrder = SectionOrder.Default,
): SeriesFilterV2Dto {
    val collection = stream.streamType == StreamType.COLLECTION
    val id = -stream.id - if (collection) COLLECTION_RAIL_BASE else LIBRARY_RAIL_BASE
    val field = if (collection) FilterField.CollectionTags.api else FilterField.LIBRARIES_API
    val newest = SeriesSortOptionDto(SeriesSort.RecentlyAdded.field, SeriesSort.RecentlyAdded.ascending)
    return SeriesFilterV2Dto(
        statements = listOf(SeriesFilterStatementDto(Comparison.Contains.api, field, "$id")),
        sortOptions = order.sortOptions(newest),
    )
}

/** Series with any genre or tag named in [wanted] (lowercase); null when none of them exist on the server. */
internal fun taggedFilter(
    wanted: Set<String>,
    genres: List<GenreTagDto>,
    tags: List<GenreTagDto>,
): SeriesFilterV2Dto? {
    fun ids(list: List<GenreTagDto>) =
        list.filter { it.title?.lowercase() in wanted }.joinToString(",") { "${it.id}" }.takeIf { it.isNotEmpty() }
    val statements =
        listOfNotNull(
            ids(genres)?.let { SeriesFilterStatementDto(Comparison.Contains.api, FilterField.Genres.api, it) },
            ids(tags)?.let { SeriesFilterStatementDto(Comparison.Contains.api, FilterField.Tags.api, it) },
        )
    return statements.takeIf { it.isNotEmpty() }?.let {
        SeriesFilterV2Dto(
            statements = it,
            combination = COMBINE_OR,
            sortOptions = SeriesSortOptionDto(SeriesSort.Title.field, isAscending = true),
        )
    }
}

/** The server sort for [this] order, or [default] when it has none of its own. */
internal fun SectionOrder.sortOptions(default: SeriesSortOptionDto?): SeriesSortOptionDto? =
    when (this) {
        SectionOrder.NameAsc -> SeriesSortOptionDto(SeriesSort.Title.field, isAscending = true)
        SectionOrder.NameDesc -> SeriesSortOptionDto(SeriesSort.Title.field, isAscending = false)
        SectionOrder.Random -> SeriesSortOptionDto(SORT_RANDOM)
        else -> default
    }

/** The Home layout row whose order applies to this stream. */
private fun DashboardStreamEntity.pref(): HomeSectionPref? =
    when (streamType) {
        StreamType.WANT_TO_READ -> HomeSectionPref(HomeSection.WantToRead, true)
        StreamType.SMART_FILTER -> HomeSectionPref(HomeSection.SmartFilters, true)
        StreamType.COLLECTION -> HomeSectionPref(HomeSection.Collection, true, -id - COLLECTION_RAIL_BASE)
        StreamType.LIBRARY -> HomeSectionPref(HomeSection.Library, true, -id - LIBRARY_RAIL_BASE)
        else -> null
    }

private val sortJson = kotlinx.serialization.json.Json

private fun assemble(
    streams: List<DashboardStreamEntity>,
    items: List<DashboardItemEntity>,
    series: List<SeriesEntity>,
    chapters: List<ChapterEntity>,
    downloaded: List<SeriesEntity>,
): Home {
    val seriesById = series.associateBy { it.id }
    val chapterById = chapters.associateBy { it.id }
    val byStream = items.groupBy { it.streamId }
    val rails =
        streams.map { stream ->
            val rows =
                byStream[stream.id].orEmpty().mapNotNull { item ->
                    seriesById[item.seriesId]?.toHomeItem(item.count, item.chapterId?.let(chapterById::get))
                }
            HomeRail(stream.id, stream.streamType, stream.name, rows)
        }
    val wantToRead = rails.firstOrNull { it.type == StreamType.WANT_TO_READ }?.items.orEmpty()

    fun tagged(type: Int) = byStream[-type].orEmpty().mapNotNull { seriesById[it.seriesId]?.toHomeItem() }
    return Home(
        continuePick(rails),
        rails,
        downloaded.map { it.toHomeItem() },
        wantToRead,
        blurIds = tagged(StreamType.BLUR).mapTo(HashSet()) { it.seriesId },
        tagHidden = tagged(StreamType.HIDE),
    )
}

private fun SeriesEntity.toHomeItem(
    newChapters: Int = 0,
    chapter: ChapterEntity? = null,
) = HomeItem(id, libraryId, localizedName ?: name, pages, pagesRead, newChapters, chapter)
