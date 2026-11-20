package com.nl.kaviyy.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.SeriesDetail
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server =
        Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val libs = MutableStateFlow(listOf(LibraryEntity(1, 10, "Manga", 0), LibraryEntity(1, 20, "Books", 2)))
    private var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    private val pagedFor = mutableListOf<Int>()

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
            override fun libraries(server: Server) = libs

            override suspend fun refreshLibraries(server: Server) = refreshResult

            override fun librarySeries(
                server: Server,
                libraryId: Int,
            ): Flow<PagingData<SeriesEntity>> {
                pagedFor += libraryId
                return flowOf(PagingData.empty())
            }

            override fun seriesDetail(
                server: Server,
                seriesId: Int,
            ): Flow<SeriesDetail> = error("unused")

            override suspend fun refreshSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<Unit> = error("unused")
        }

    @Test
    fun selectsFirstLibraryThenTheChosenOneAndFallsBackWhenItDisappears() =
        runTest {
            val vm = LibraryViewModel(session, repo, SavedStateHandle())
            backgroundScope.launch { vm.series.collect {} }
            vm.state.test {
                runCurrent()
                assertEquals(10, expectMostRecentItem().selectedId)
                vm.select(20)
                runCurrent()
                assertEquals(1, expectMostRecentItem().selectedIndex)
                assertEquals(listOf(10, 20), pagedFor)
                libs.value = libs.value.take(1)
                runCurrent()
                assertEquals(10, expectMostRecentItem().selectedId)
            }
        }

    @Test
    fun layoutToggleAndRefreshError() =
        runTest {
            refreshResult = AppResult.Failure(AppError.Network)
            val vm = LibraryViewModel(session, repo, SavedStateHandle())
            vm.state.test {
                runCurrent()
                val s = expectMostRecentItem()
                assertEquals(AppError.Network, s.error)
                assertEquals(true, s.grid)
                vm.toggleLayout()
                refreshResult = AppResult.Success(Unit)
                vm.retry()
                runCurrent()
                val after = expectMostRecentItem()
                assertEquals(false, after.grid)
                assertNull(after.error)
            }
        }
}
