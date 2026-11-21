package com.nl.kaviyy.feature.filter

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.paging.PagingData
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterPlacement
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.FilterScope
import com.nl.kaviyy.core.data.LibraryFilterStore
import com.nl.kaviyy.core.data.LibraryQuery
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.RelatedSeries
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SavedFilter
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesFilter
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.data.SmartFilterRepository
import com.nl.kaviyy.core.database.LibraryEntity
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class FilterViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val store = LibraryFilterStore()
    private var options: AppResult<List<RuleValue>> = AppResult.Success(listOf(DRAMA))
    private val scopes = mutableListOf<List<Int>>()

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

    private val repo =
        object : CatalogRepository {
            override fun libraries(server: Server) =
                flowOf(listOf(LibraryEntity(1, 10, "Manga", 0), LibraryEntity(1, 20, "Comics", 1)))

            override suspend fun refreshLibraries(server: Server): AppResult<Unit> = error("unused")

            override fun librarySeries(
                server: Server,
                query: LibraryQuery,
            ): Flow<PagingData<SeriesEntity>> = error("unused")

            override suspend fun filterOptions(
                server: Server,
                source: OptionSource,
                libraryIds: List<Int>,
            ): AppResult<List<RuleValue>> {
                scopes += libraryIds
                return options
            }

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

    private var saveResult: AppResult<SavedFilter> = AppResult.Success(SavedFilter(5, "Fantasy", "e"))
    private val saves = mutableListOf<Pair<String, FilterPlacement>>()
    private val deleted = mutableListOf<Int>()
    private val loadedFilter =
        SeriesFilter(rules = listOf(FilterRule(FilterField.Genres, Comparison.Contains, listOf(DRAMA))))

    private val smart =
        object : SmartFilterRepository {
            override suspend fun list(server: Server) = AppResult.Success(listOf(SavedFilter(5, "Fantasy", "e")))

            override suspend fun pinned(server: Server): AppResult<List<SavedFilter>> = error("unused")

            override suspend fun load(
                server: Server,
                saved: SavedFilter,
            ) = AppResult.Success(loadedFilter)

            override suspend fun placement(
                server: Server,
                filterId: Int,
            ) = AppResult.Success(FilterPlacement(onHome = true))

            override suspend fun save(
                server: Server,
                name: String,
                filter: SeriesFilter,
                scope: List<Int>,
                placement: FilterPlacement,
            ): AppResult<SavedFilter> {
                saves += name to placement
                return saveResult
            }

            override suspend fun delete(
                server: Server,
                filterId: Int,
            ): AppResult<Unit> {
                deleted += filterId
                return AppResult.Success(Unit)
            }
        }

    @Test
    fun namedApplySavesWithPlacementAndFailureKeepsTheSheetOpen() =
        runTest {
            val vm = FilterViewModel(session, store, repo, smart)
            vm.open(10)
            runCurrent()
            vm.setMatchAll(false)
            vm.nameText.setTextAndPlaceCursorAtEnd("Fantasy")
            vm.setPinInLibrary(true)
            saveResult = AppResult.Failure(AppError.Forbidden)
            vm.apply()
            runCurrent()
            assertEquals(AppError.Forbidden, vm.state.value.error)
            assertEquals(false, vm.state.value.done)
            assertEquals(true, store.current(server.id).matchAll) // nothing applied
            saveResult = AppResult.Success(SavedFilter(5, "Fantasy", "e"))
            vm.apply()
            runCurrent()
            assertEquals("Fantasy" to FilterPlacement(inLibrary = true), saves.last())
            assertEquals(true, vm.state.value.done)
            assertEquals(false, store.current(server.id).matchAll)
        }

    @Test
    fun eachListKeepsItsOwnFilter() =
        runTest {
            store.update(server.id) { it.copy(matchAll = false) } // the Library's filter
            val vm = FilterViewModel(session, store, repo, smart)
            vm.open(null, FilterScope.WantToRead)
            runCurrent()
            assertEquals(true, vm.state.value.draft.matchAll) // Want to Read starts from its own (default) filter
            vm.setSort(SeriesSort.RecentlyUpdated)
            vm.apply()
            runCurrent()
            assertEquals(SeriesSort.RecentlyUpdated, store.current(server.id, FilterScope.WantToRead).sort)
            assertEquals(false, store.current(server.id).matchAll)
            assertEquals(SeriesFilter().sort, store.current(server.id).sort)
        }

    @Test
    fun savedFiltersOpenForEditingAndDelete() =
        runTest {
            val vm = FilterViewModel(session, store, repo, smart)
            vm.open(10)
            runCurrent()
            vm.show(FilterPage.Saved)
            runCurrent()
            val saved =
                vm.state.value.saved
                    .orEmpty()
                    .single()
            vm.openSaved(saved)
            runCurrent()
            assertEquals(FilterPage.Main, vm.state.value.page)
            assertEquals(loadedFilter, vm.state.value.draft)
            assertEquals(FilterPlacement(onHome = true), vm.state.value.placement)
            assertEquals("Fantasy", vm.nameText.text.toString())
            vm.deleteSaved(saved)
            runCurrent()
            assertEquals(listOf(5), deleted)
            assertEquals(emptyList(), vm.state.value.saved)
        }

    @Test
    fun editsADraftAndOnlyApplyChangesTheGrid() =
        runTest {
            val unread = FilterRule(FilterField.ReadProgress, Comparison.LessThan, listOf(RuleValue("100")))
            store.update(server.id) { SeriesFilter(rules = listOf(unread)) }
            val vm = FilterViewModel(session, store, repo, smart)
            vm.open(currentLibrary = 10)
            runCurrent()
            assertEquals(listOf(unread), vm.state.value.draft.rules)

            vm.addRule()
            vm.pickField(FilterField.Genres)
            runCurrent()
            assertEquals(Options.Loaded(listOf(DRAMA)), vm.state.value.options)
            assertEquals(listOf(10), scopes.last()) // options come from the grid's library
            vm.toggleValue(DRAMA)
            vm.commitRule()
            vm.setMatchAll(false)
            vm.setSort(SeriesSort.ReleaseYear)
            assertEquals(FilterPage.Main, vm.state.value.page)
            assertEquals(1, store.current(server.id).rules.size) // not applied yet

            vm.apply()
            runCurrent()
            val applied = store.current(server.id)
            assertEquals(
                listOf(unread, FilterRule(FilterField.Genres, Comparison.Contains, listOf(DRAMA))),
                applied.rules,
            )
            assertEquals(false, applied.matchAll)
            assertEquals(SeriesSort.ReleaseYear, applied.sort)
        }

    @Test
    fun editReplacesRemoveDeletesAndBadValuesAreRejected() =
        runTest {
            val vm = FilterViewModel(session, store, repo, smart)
            vm.open(10)
            runCurrent()
            vm.pickField(FilterField.ReleaseYear)
            vm.valueText.setTextAndPlaceCursorAtEnd("soon")
            vm.commitRule()
            assertEquals(DraftError.NotANumber, vm.state.value.ruleError)
            vm.valueText.setTextAndPlaceCursorAtEnd("2015")
            vm.setComparison(Comparison.GreaterThan)
            vm.commitRule()
            vm.editRule(0)
            assertEquals("2015", vm.valueText.text.toString())
            vm.valueText.setTextAndPlaceCursorAtEnd("2018")
            vm.commitRule()
            assertEquals(
                listOf("2018"),
                vm.state.value.draft.rules
                    .map { it.values.single().code },
            )
            vm.removeRule(0)
            assertEquals(emptyList(), vm.state.value.draft.rules)
        }

    @Test
    fun libraryScopeAndOptionFailures() =
        runTest {
            options = AppResult.Failure(AppError.Network)
            val vm = FilterViewModel(session, store, repo, smart)
            vm.open(10)
            runCurrent()
            vm.toggleLibrary(20)
            assertEquals(listOf(10, 20), vm.state.value.draft.libraryIds)
            vm.toggleLibrary(10)
            vm.toggleLibrary(20) // never empty: falls back to the grid's library
            assertEquals(listOf(10), vm.state.value.draft.libraryIds)
            vm.pickField(FilterField.Tags)
            runCurrent()
            assertEquals(Options.Failed(AppError.Network), vm.state.value.options)
        }
}

private val DRAMA = RuleValue("7", "Drama")
