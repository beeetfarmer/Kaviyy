package com.nl.kaviyy.feature.lists

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.FilterScope
import com.nl.kaviyy.core.data.LibraryFilterStore
import com.nl.kaviyy.core.data.LibraryQuery
import com.nl.kaviyy.core.data.ListSummary
import com.nl.kaviyy.core.data.ListsRepository
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.RelatedSeries
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.data.collectionQuery
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ListsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val session =
        object : AuthSession {
            override val servers = active.map { listOfNotNull(it) }
            override val activeServer = active

            override suspend fun switchTo(serverId: ServerId) = Unit

            override suspend fun remove(serverId: ServerId) = Unit

            override suspend fun deleteAllLocalData() = Unit

            override suspend fun knownServer(url: ServerUrl): Server? = null

            override suspend fun signIn(
                target: Server,
                method: LoginMethod,
                staySignedIn: Boolean,
            ): AppResult<Server> = error("unused")

            override suspend fun signOut(serverId: ServerId) = Unit

            override fun credentialSource(serverId: ServerId) = error("unused")
        }

    private val summary = MutableStateFlow(ListSummary(2, false, listOf(4, 5), 1))
    private val collections = MutableStateFlow(listOf(CollectionEntity(1, 7, "Comfort reads", itemCount = 3)))
    private val refreshed = mutableListOf<ServerId>()
    private val lists =
        object : ListsRepository {
            override fun wantToRead(server: Server) = summary

            override suspend fun refreshWantToRead(server: Server): AppResult<Unit> {
                refreshed += server.id
                return AppResult.Success(Unit)
            }

            override fun collections(server: Server) = collections

            override fun collection(
                server: Server,
                id: Int,
            ) = collections.map { list -> list.firstOrNull { it.id == id } }

            override suspend fun refreshCollections(server: Server): AppResult<Unit> {
                refreshed += server.id
                return AppResult.Success(Unit)
            }
        }

    private val queries = mutableListOf<LibraryQuery>()
    private val catalog =
        object : CatalogRepository {
            override fun libraries(server: Server): Flow<List<LibraryEntity>> = flowOf(emptyList())

            override suspend fun refreshLibraries(server: Server): AppResult<Unit> = error("unused")

            override fun librarySeries(
                server: Server,
                query: LibraryQuery,
            ): Flow<PagingData<SeriesEntity>> {
                queries += query
                return flowOf(PagingData.empty())
            }

            override suspend fun filterOptions(
                server: Server,
                source: OptionSource,
                libraryIds: List<Int>,
            ): AppResult<List<RuleValue>> = error("unused")

            override fun seriesDetail(
                server: Server,
                seriesId: Int,
            ): Flow<SeriesDetail> = error("unused")

            override suspend fun refreshSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<Unit> = error("unused")

            override suspend fun relatedSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<List<RelatedSeries>> = error("unused")
        }

    @Test
    fun listsShowTheWantToReadSummaryAndRefreshPerServer() =
        runTest {
            val vm = ListsViewModel(session, lists)
            vm.state.test {
                runCurrent()
                val first = expectMostRecentItem()
                assertEquals(ListSummary(2, false, listOf(4, 5), 1), first.wantToRead)
                assertEquals(listOf("Comfort reads"), first.collections.map { it.title })
                summary.value = ListSummary(3, false, listOf(4, 5, 6), 1)
                assertEquals(3, expectMostRecentItem().wantToRead.count)
                assertEquals(listOf(server.id, server.id), refreshed) // want to read + collections
                active.value = null
                assertEquals(ListsUiState(), expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aCollectionPagesItsSeriesByTheCollectionRule() =
        runTest {
            val vm = CollectionViewModel(session, catalog, lists, SavedStateHandle(mapOf(COLLECTION_ID_ARG to 7)))
            backgroundScope.launch { vm.series.collect {} }
            vm.state.test {
                runCurrent()
                assertEquals("Comfort reads", expectMostRecentItem().collection?.title)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(collectionQuery(7), queries.last())
        }

    @Test
    fun wantToReadPagesKavitasListWithItsOwnFilter() =
        runTest {
            val store = LibraryFilterStore()
            store.update(server.id) { it.copy(sort = SeriesSort.UserRating) } // Library's: not used here
            val vm = WantToReadViewModel(session, catalog, store)
            backgroundScope.launch { vm.series.collect {} }
            runCurrent()
            assertEquals(
                LibraryQuery(
                    null,
                    com.nl.kaviyy.core.data
                        .SeriesFilter(),
                    wantToRead = true,
                ),
                queries.last(),
            )
            store.update(server.id, FilterScope.WantToRead) { it.copy(sort = SeriesSort.RecentlyUpdated) }
            runCurrent()
            assertEquals(SeriesSort.RecentlyUpdated, queries.last().filter.sort)
            assertEquals(true, queries.last().wantToRead)
        }
}
