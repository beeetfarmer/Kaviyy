package com.nl.kaviyy.feature.series

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.LibraryQuery
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesMetadata
import com.nl.kaviyy.core.database.ChapterEntity
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

class SeriesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val detail = MutableStateFlow(SeriesDetail(null, emptyList(), emptyList()))
    private var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    private val refreshed = mutableListOf<Int>()

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
            override fun libraries(server: Server) = flowOf(listOf(LibraryEntity(1, 3, "Manga", 0)))

            override suspend fun refreshLibraries(server: Server): AppResult<Unit> = error("unused")

            override fun librarySeries(
                server: Server,
                query: LibraryQuery,
            ): Flow<PagingData<SeriesEntity>> = error("unused")

            override suspend fun filterOptions(
                server: Server,
                source: OptionSource,
                libraryIds: List<Int>,
            ): AppResult<List<RuleValue>> = error("unused")

            override fun seriesDetail(
                server: Server,
                seriesId: Int,
            ): Flow<SeriesDetail> = detail

            override suspend fun refreshSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<Unit> {
                refreshed += seriesId
                return refreshResult
            }
        }

    @Test
    fun showsCachedDetailWithLibraryAndContinuePointAndRefreshes() =
        runTest {
            val vm = SeriesViewModel(session, repo, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                assertEquals(listOf(7), refreshed)
                val ch = ChapterEntity(1, 49, 5, 7, pages = 30, pagesRead = 4, format = 0)
                detail.value =
                    SeriesDetail(
                        SeriesEntity(1, 7, 3, "Ember Road", "ember road", format = 0, pages = 10, pagesRead = 2),
                        emptyList(),
                        listOf(ch),
                        SeriesMetadata(continueChapterId = 49),
                    )
                runCurrent()
                val s = expectMostRecentItem()
                assertEquals("Manga", s.libraryName)
                assertEquals(ch, s.continueChapter)
                assertEquals(false, s.refreshing)
            }
        }

    @Test
    fun refreshFailureIsReportedAndRetryCallsAgain() =
        runTest {
            refreshResult = AppResult.Failure(AppError.Network)
            val vm = SeriesViewModel(session, repo, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                assertEquals(AppError.Network, expectMostRecentItem().error)
                refreshResult = AppResult.Success(Unit)
                vm.refresh()
                runCurrent()
                assertEquals(null, expectMostRecentItem().error)
                assertEquals(listOf(7, 7), refreshed)
            }
        }
}
