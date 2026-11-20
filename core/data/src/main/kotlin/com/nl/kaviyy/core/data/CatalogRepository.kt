package com.nl.kaviyy.core.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.LibraryDao
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.VolumeEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** The [KavitaApi] for a server, already wired to its credentials. Seam for tests. */
fun interface ServerApis {
    fun api(server: Server): KavitaApi
}

/** Carries an [AppError] through Paging's Throwable-based error channel. */
class AppErrorException(
    val error: AppError,
) : Exception(error.toString())

data class SeriesDetail(
    val series: SeriesEntity?,
    val volumes: List<VolumeEntity>,
    val chapters: List<ChapterEntity>,
)

internal const val SERIES_PAGE_SIZE = 50

/**
 * Offline-first catalogue: screens observe Room; `refresh…` calls fetch from the server and write to Room. A failed
 * refresh leaves the cached data in place and returns the error for the UI to show.
 */
interface CatalogRepository {
    fun libraries(server: Server): Flow<List<LibraryEntity>>

    suspend fun refreshLibraries(server: Server): AppResult<Unit>

    /** Library grid: Room pages in the server's order for [query], filled from the server as it scrolls. */
    fun librarySeries(
        server: Server,
        query: LibraryQuery,
    ): Flow<PagingData<SeriesEntity>>

    /** Values for an options rule (network only). [OptionSource.Formats] is local. */
    suspend fun filterOptions(
        server: Server,
        source: OptionSource,
        libraryIds: List<Int>,
    ): AppResult<List<RuleValue>>

    fun seriesDetail(
        server: Server,
        seriesId: Int,
    ): Flow<SeriesDetail>

    suspend fun refreshSeries(
        server: Server,
        seriesId: Int,
    ): AppResult<Unit>
}

/** Room-backed [CatalogRepository]. */
class OfflineFirstCatalogRepository
    @Inject
    constructor(
        private val libraryDao: LibraryDao,
        private val dao: SeriesDao,
        private val apis: ServerApis,
    ) : CatalogRepository {
        override fun libraries(server: Server): Flow<List<LibraryEntity>> = libraryDao.observeLibraries(server.id.value)

        override suspend fun refreshLibraries(server: Server): AppResult<Unit> =
            apiCall { apis.api(server).libraries() }.map { list ->
                libraryDao.replaceLibraries(server.id.value, list.map { it.toEntity(server.id.value) })
            }

        @OptIn(ExperimentalPagingApi::class)
        override fun librarySeries(
            server: Server,
            query: LibraryQuery,
        ): Flow<PagingData<SeriesEntity>> =
            Pager(
                config = PagingConfig(pageSize = SERIES_PAGE_SIZE, enablePlaceholders = false),
                remoteMediator = LibrarySeriesMediator(server, query, apis.api(server), dao),
                pagingSourceFactory = { dao.seriesForQuery(server.id.value, query.key) },
            ).flow

        override suspend fun filterOptions(
            server: Server,
            source: OptionSource,
            libraryIds: List<Int>,
        ): AppResult<List<RuleValue>> {
            val api = apis.api(server)
            val libs = libraryIds.joinToString(",")

            fun named(
                id: Any,
                title: String?,
            ) = title?.takeIf { it.isNotBlank() }?.let { RuleValue(id.toString(), it) }
            return apiCall {
                when (source) {
                    OptionSource.Genres -> {
                        api.genres(libs).mapNotNull { named(it.id, it.title) }
                    }

                    OptionSource.Tags -> {
                        api.tags(libs).mapNotNull { named(it.id, it.title) }
                    }

                    OptionSource.Languages -> {
                        api.languages(libs).mapNotNull { l ->
                            l.isoCode?.let { named(it, l.title) }
                        }
                    }

                    OptionSource.AgeRatings -> {
                        api.ageRatings(libs).mapNotNull { named(it.value, it.title) }
                    }

                    OptionSource.PublicationStatus -> {
                        api
                            .publicationStatuses(
                                libs,
                            ).mapNotNull { named(it.value, it.title) }
                    }

                    OptionSource.Collections -> {
                        api.collections().mapNotNull { named(it.id, it.title) }
                    }

                    OptionSource.Formats -> {
                        emptyList()
                    }

                    else -> {
                        api.peopleByRole(source.role).mapNotNull { named(it.id, it.name) }
                    }
                }
            }.map { list ->
                // Age ratings and statuses keep the server's order (it is meaningful); the rest are alphabetical.
                if (source == OptionSource.AgeRatings || source == OptionSource.PublicationStatus) {
                    list
                } else {
                    list.sortedBy { it.label.lowercase() }
                }
            }
        }

        override fun seriesDetail(
            server: Server,
            seriesId: Int,
        ): Flow<SeriesDetail> =
            combine(
                dao.observeSeries(server.id.value, seriesId),
                dao.observeVolumes(server.id.value, seriesId),
                dao.observeChapters(server.id.value, seriesId),
                ::SeriesDetail,
            )

        override suspend fun refreshSeries(
            server: Server,
            seriesId: Int,
        ): AppResult<Unit> {
            val api = apis.api(server)
            val id = server.id.value
            val series = apiCall { api.series(seriesId) }
            if (series is AppResult.Failure) return series
            return apiCall { api.seriesDetail(seriesId) }.map { detail ->
                // Volumes carry their chapters; specials/loose chapters are also listed separately. Dedupe by id.
                val chapters =
                    (detail.volumes.flatMap { it.chapters } + detail.specials + detail.chapters)
                        .distinctBy { it.id }
                        .map { it.toEntity(id, seriesId) }
                dao.replaceSeriesDetail(
                    (series as AppResult.Success).value.toEntity(id),
                    detail.volumes.map { it.copy(seriesId = seriesId).toEntity(id) },
                    chapters,
                )
            }
        }
    }

/**
 * Appends server pages into Room as the grid scrolls. Pages are numbered from 1 (Kavita `PageNumber`); the end is a
 * short page (the `Pagination` header isn't in the API spec). Each query keeps its own ordered rows (`query_items`):
 * a refresh replaces them, appends add after, and the cache stays readable offline.
 */
@OptIn(ExperimentalPagingApi::class)
internal class LibrarySeriesMediator(
    private val server: Server,
    private val query: LibraryQuery,
    private val api: KavitaApi,
    private val dao: SeriesDao,
) : RemoteMediator<Int, SeriesEntity>() {
    private var nextPage = 1

    override suspend fun initialize() = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, SeriesEntity>,
    ): MediatorResult {
        val page =
            when (loadType) {
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> nextPage
            }
        return when (
            val result = apiCall { api.series(query.toFilter(), page, SERIES_PAGE_SIZE) }
        ) {
            is AppResult.Failure -> {
                MediatorResult.Error(AppErrorException(result.error))
            }

            is AppResult.Success -> {
                val id = server.id.value
                dao.saveQueryPage(id, query.key, (page - 1) * SERIES_PAGE_SIZE, result.value.map { it.toEntity(id) })
                nextPage = page + 1
                MediatorResult.Success(endOfPaginationReached = result.value.size < SERIES_PAGE_SIZE)
            }
        }
    }
}
