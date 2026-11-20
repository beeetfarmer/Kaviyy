package com.nl.kaviyy.core.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import com.nl.kaviyy.core.database.KaviyyDatabase
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.ServerEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ChapterDto
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.LibraryDto
import com.nl.kaviyy.core.network.MangaFileDto
import com.nl.kaviyy.core.network.SeriesDetailDto
import com.nl.kaviyy.core.network.SeriesDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.VolumeDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KaviyyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val dao = db.seriesDao()

    private val serverId =
        kotlinx.coroutines.runBlocking {
            db.serverDao().upsert(ServerEntity(name = "k", url = "https://k.lan"))
        }
    private val server =
        Server(ServerId(serverId), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)

    private var libraryList: () -> List<LibraryDto> = { emptyList() }
    private val seriesPages = mutableListOf<Pair<Int, Int>>() // (page, libraryId-from-filter)
    private var seriesTotal = 0
    private var detail = SeriesDetailDto()
    private var failWith: Exception? = null

    private val api =
        object : FakeKavitaApi() {
            override suspend fun libraries(): List<LibraryDto> = failWith?.let { throw it } ?: libraryList()

            override suspend fun series(
                filter: SeriesFilterV2Dto,
                pageNumber: Int,
                pageSize: Int,
            ): List<SeriesDto> {
                failWith?.let { throw it }
                seriesPages += pageNumber to
                    filter.statements
                        .first()
                        .value
                        .toInt()
                val from = (pageNumber - 1) * pageSize
                return (from until minOf(from + pageSize, seriesTotal)).map {
                    SeriesDto(it + 1, libraryId = 3, name = "S%03d".format(it))
                }
            }

            override suspend fun series(seriesId: Int) =
                SeriesDto(seriesId, libraryId = 3, name = "One Piece", sortName = "one piece")

            override suspend fun seriesDetail(seriesId: Int) = detail
        }

    private val repo = OfflineFirstCatalogRepository(db.libraryDao(), dao, { api })

    @After
    fun tearDown() = db.close()

    @Test
    fun librariesAreReplacedOnRefreshAndKeptOnFailure() =
        runTest {
            libraryList = { listOf(LibraryDto(1, "Manga", 0), LibraryDto(2, "Books", 2, "2026-01-02T03:04:05")) }
            assertEquals(AppResult.Success(Unit), repo.refreshLibraries(server))
            assertEquals(listOf("Books", "Manga"), repo.libraries(server).first().map { it.name })
            libraryList = { listOf(LibraryDto(2, "Books", 2)) }
            repo.refreshLibraries(server)
            assertEquals(listOf(2), repo.libraries(server).first().map { it.id })
            failWith = IOException("offline")
            assertEquals(AppResult.Failure(AppError.Network), repo.refreshLibraries(server))
            assertEquals(listOf(2), repo.libraries(server).first().map { it.id })
            failWith = HttpException(Response.error<Any>(401, "".toResponseBody()))
            assertEquals(AppResult.Failure(AppError.Unauthorized), repo.refreshLibraries(server))
        }

    @Test
    fun mediatorAppendsPagesUntilAShortPage() =
        runTest {
            seriesTotal = SERIES_PAGE_SIZE + 5
            val mediator = LibrarySeriesMediator(server, LibraryQuery(3, SeriesSort.Title), api, dao)
            val state = PagingState<Int, SeriesEntity>(emptyList(), null, PagingConfig(SERIES_PAGE_SIZE), 0)
            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())
            val first = assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.REFRESH, state))
            assertTrue(!first.endOfPaginationReached)
            val second = assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.APPEND, state))
            assertTrue(second.endOfPaginationReached)
            assertEquals(listOf(1 to 3, 2 to 3), seriesPages)
            val page =
                dao
                    .seriesForQuery(
                        serverId,
                        LibraryQuery(3, SeriesSort.Title).key,
                    ).load(PagingSource.LoadParams.Refresh(null, 100, false))
            assertEquals(SERIES_PAGE_SIZE + 5, (page as PagingSource.LoadResult.Page).data.size)
            assertEquals("S000", page.data.first().name)
        }

    @Test
    fun eachQueryKeepsItsOwnRowsAndARefreshReplacesThem() =
        runTest {
            val state = PagingState<Int, SeriesEntity>(emptyList(), null, PagingConfig(SERIES_PAGE_SIZE), 0)
            val byTitle = LibraryQuery(3, SeriesSort.Title)
            val unread = byTitle.copy(unreadOnly = true)

            suspend fun rows(q: LibraryQuery) =
                (
                    dao.seriesForQuery(serverId, q.key).load(PagingSource.LoadParams.Refresh(null, 100, false))
                        as PagingSource.LoadResult.Page
                ).data.size
            seriesTotal = 3
            LibrarySeriesMediator(server, byTitle, api, dao).load(LoadType.REFRESH, state)
            seriesTotal = 2
            LibrarySeriesMediator(server, unread, api, dao).load(LoadType.REFRESH, state)
            assertEquals(3 to 2, rows(byTitle) to rows(unread))
            seriesTotal = 1
            LibrarySeriesMediator(server, byTitle, api, dao).load(LoadType.REFRESH, state)
            assertEquals(1 to 2, rows(byTitle) to rows(unread))
        }

    @Test
    fun mediatorFailureKeepsCacheAndReportsTheError() =
        runTest {
            failWith = IOException("offline")
            val mediator = LibrarySeriesMediator(server, LibraryQuery(3, SeriesSort.Title), api, dao)
            val state = PagingState<Int, SeriesEntity>(emptyList(), null, PagingConfig(SERIES_PAGE_SIZE), 0)
            val error = assertIs<RemoteMediator.MediatorResult.Error>(mediator.load(LoadType.REFRESH, state))
            assertEquals(AppError.Network, assertIs<AppErrorException>(error.throwable).error)
            assertTrue(
                mediator.load(LoadType.PREPEND, state).let {
                    (it as RemoteMediator.MediatorResult.Success).endOfPaginationReached
                },
            )
        }

    @Test
    fun seriesDetailIsStoredWholeAndStaleRowsDropped() =
        runTest {
            val ch1 =
                ChapterDto(
                    10,
                    volumeId = 5,
                    range = "1",
                    sortOrder = 1f,
                    files = listOf(MangaFileDto(100), MangaFileDto(50)),
                )
            val special = ChapterDto(12, volumeId = 6, isSpecial = true, sortOrder = 100f)
            detail =
                SeriesDetailDto(
                    volumes =
                        listOf(
                            VolumeDto(
                                5,
                                name = "Vol 1",
                                minNumber = 1f,
                                chapters = listOf(ch1, ChapterDto(11, volumeId = 5, sortOrder = 2f)),
                            ),
                        ),
                    specials = listOf(special),
                    chapters = listOf(ch1),
                )
            assertEquals(AppResult.Success(Unit), repo.refreshSeries(server, 7))
            var d = repo.seriesDetail(server, 7).first()
            assertEquals("One Piece", d.series?.name)
            assertEquals(listOf(5), d.volumes.map { it.id })
            assertEquals(7, d.volumes.single().seriesId)
            assertEquals(listOf(10, 11, 12), d.chapters.map { it.id })
            assertEquals(150, d.chapters.first().fileBytes)
            detail = SeriesDetailDto(volumes = listOf(VolumeDto(5, chapters = listOf(ch1))))
            repo.refreshSeries(server, 7)
            d = repo.seriesDetail(server, 7).first()
            assertEquals(listOf(10), d.chapters.map { it.id })
        }
}
