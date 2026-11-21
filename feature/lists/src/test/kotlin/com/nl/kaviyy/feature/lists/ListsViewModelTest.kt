package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.data.RelatedSeries
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.data.collectionQuery
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.ReadingListEntity
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.result.AppError
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

    private val server =
        Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url, username = "Neo")
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
    private val collections =
        MutableStateFlow(listOf(CollectionEntity(1, 7, "Comfort reads", itemCount = 3, owner = "neo")))
    private val changes = mutableListOf<String>()
    private var changeResult: AppResult<Unit> = AppResult.Success(Unit)
    private val refreshed = mutableListOf<ServerId>()
    private val readingRows =
        MutableStateFlow(
            listOf(
                ReadingListRow(ReadingListEntity(1, 3, "Crisis order", itemCount = 4, owner = "neo"), readCount = 1),
            ),
        )
    private val readingCalls = mutableListOf<String>()
    private val readingItems =
        MutableStateFlow(
            listOf(
                ReadingListItemEntity(1, 1, 3, 0, 100, 0, 5, "Ch. 1", pagesRead = 20, pagesTotal = 20),
                ReadingListItemEntity(1, 2, 3, 1, 101, 0, 6, "Ch. 2", pagesTotal = 20),
            ),
        )
    private var itemsResult: AppResult<Unit> = AppResult.Success(Unit)
    private val readingLists =
        object : ReadingListRepository {
            override fun lists(server: Server) = readingRows

            override fun list(
                server: Server,
                id: Int,
            ) = readingRows.map { r -> r.firstOrNull { it.list.id == id } }

            override fun items(
                server: Server,
                id: Int,
            ) = readingItems

            override suspend fun refreshLists(server: Server): AppResult<Unit> {
                refreshed += server.id
                return AppResult.Success(Unit)
            }

            override suspend fun refreshItems(
                server: Server,
                id: Int,
            ) = itemsResult

            override suspend fun listsWith(
                server: Server,
                seriesId: Int,
            ): AppResult<Set<Int>> = error("unused")

            override suspend fun create(
                server: Server,
                title: String,
            ): AppResult<Int> {
                readingCalls += "create $title"
                return if (title == "fail") AppResult.Failure(AppError.Network) else AppResult.Success(9)
            }

            override suspend fun update(
                server: Server,
                id: Int,
                title: String,
                summary: String?,
            ): AppResult<Unit> {
                readingCalls += "update $id $title"
                return AppResult.Success(Unit)
            }

            override suspend fun delete(
                server: Server,
                id: Int,
            ): AppResult<Unit> {
                readingCalls += "delete $id"
                return AppResult.Success(Unit)
            }

            override suspend fun move(
                server: Server,
                item: ReadingListItemEntity,
                to: Int,
            ): AppResult<Unit> {
                readingCalls += "move ${item.id} to $to"
                return AppResult.Success(Unit)
            }

            override suspend fun remove(
                server: Server,
                item: ReadingListItemEntity,
            ): AppResult<Unit> {
                readingCalls += "remove ${item.id}"
                return AppResult.Success(Unit)
            }

            override suspend fun addSeries(
                server: Server,
                id: Int,
                seriesId: Int,
            ): AppResult<Unit> = error("unused")
        }

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

            override suspend fun collectionsWith(
                server: Server,
                seriesId: Int,
            ): AppResult<Set<Int>> = error("unused")

            override suspend fun setInCollection(
                server: Server,
                collection: CollectionEntity,
                seriesId: Int,
                inIt: Boolean,
            ): AppResult<Unit> = error("unused")

            override suspend fun createCollection(
                server: Server,
                title: String,
                seriesId: Int,
            ): AppResult<Unit> = error("unused")

            override suspend fun updateCollection(
                server: Server,
                collection: CollectionEntity,
                title: String,
                summary: String?,
            ): AppResult<Unit> {
                changes += "update ${collection.id} $title $summary"
                return changeResult
            }

            override suspend fun deleteCollection(
                server: Server,
                id: Int,
            ): AppResult<Unit> {
                changes += "delete $id"
                return changeResult
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
            val vm = ListsViewModel(session, lists, readingLists)
            vm.state.test {
                runCurrent()
                val first = expectMostRecentItem()
                assertEquals(ListSummary(2, false, listOf(4, 5), 1), first.wantToRead)
                assertEquals(listOf("Comfort reads"), first.collections.map { it.title })
                assertEquals(listOf("Crisis order"), first.readingLists.map { it.list.title })
                summary.value = ListSummary(3, false, listOf(4, 5, 6), 1)
                assertEquals(3, expectMostRecentItem().wantToRead.count)
                assertEquals(List(3) { server.id }, refreshed) // want to read + collections + reading lists
                active.value = null
                assertEquals(ListsUiState(), expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aReadingListShowsItsItemsAndKeepsThemWhenRefreshFails() =
        runTest {
            itemsResult = AppResult.Failure(AppError.Network)
            val vm = ReadingListViewModel(session, readingLists, SavedStateHandle(mapOf(READING_LIST_ID_ARG to 3)))
            vm.state.test {
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals("Crisis order", st.list?.list?.title)
                assertEquals(listOf(100, 101), st.items.map { it.chapterId })
                assertEquals(AppError.Network, st.error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theOwnerArrangesRenamesAndDeletesAReadingList() =
        runTest {
            val vm = ReadingListViewModel(session, readingLists, SavedStateHandle(mapOf(READING_LIST_ID_ARG to 3)))
            vm.state.test {
                runCurrent()
                assertEquals(true, expectMostRecentItem().editable)
                vm.toggleArranging()
                vm.move(0, -1) // already first: nothing
                vm.move(0, 1)
                runCurrent()
                vm.remove(readingItems.value[1])
                runCurrent()
                vm.startEdit()
                assertEquals("Crisis order", vm.title.text.toString())
                vm.title.setTextAndPlaceCursorAtEnd("Crisis")
                vm.save()
                runCurrent()
                vm.delete()
                vm.delete()
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals(true to true, st.arranging to st.deleted)
                assertEquals(listOf("move 1 to 1", "remove 2", "update 3 Crisis", "delete 3"), readingCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun plusCreatesAReadingListAndOpensItOnce() =
        runTest {
            val vm = ListsViewModel(session, lists, readingLists)
            vm.state.test {
                runCurrent()
                vm.openNew()
                vm.newName.setTextAndPlaceCursorAtEnd("fail")
                vm.createReadingList()
                runCurrent()
                assertEquals(true to true, expectMostRecentItem().newList.let { it.open to it.failed })
                vm.newName.setTextAndPlaceCursorAtEnd(" Crisis ")
                vm.createReadingList()
                runCurrent()
                assertEquals(9, expectMostRecentItem().newList.created)
                vm.consumeCreated()
                runCurrent()
                assertEquals(NewListState(), expectMostRecentItem().newList)
                assertEquals(listOf("create fail", "create Crisis"), readingCalls)
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
    fun theOwnerEditsAndDeletesAfterConfirming() =
        runTest {
            val vm = CollectionViewModel(session, catalog, lists, SavedStateHandle(mapOf(COLLECTION_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                assertEquals(true, expectMostRecentItem().editable)
                vm.startEdit()
                assertEquals("Comfort reads", vm.title.text.toString())
                vm.title.setTextAndPlaceCursorAtEnd("Comfort")
                changeResult = AppResult.Failure(AppError.Network)
                vm.save()
                runCurrent()
                var st = expectMostRecentItem()
                assertEquals(true to AppError.Network, st.editing to st.error) // stays open on failure
                changeResult = AppResult.Success(Unit)
                vm.save()
                runCurrent()
                assertEquals(false, expectMostRecentItem().editing)
                vm.startEdit()
                vm.delete() // asks first
                runCurrent()
                st = expectMostRecentItem()
                assertEquals(true to false, st.confirmDelete to st.deleted)
                vm.delete()
                runCurrent()
                assertEquals(true, expectMostRecentItem().deleted)
                assertEquals(listOf("update 7 Comfort ", "update 7 Comfort ", "delete 7"), changes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun othersCannotEdit() =
        runTest {
            collections.value = listOf(CollectionEntity(1, 7, "Theirs", owner = "trinity"))
            val vm = CollectionViewModel(session, catalog, lists, SavedStateHandle(mapOf(COLLECTION_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                assertEquals(false, expectMostRecentItem().editable)
                cancelAndIgnoreRemainingEvents()
            }
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
