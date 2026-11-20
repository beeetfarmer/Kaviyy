package com.nl.kaviyy.feature.search

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.SearchRepository
import com.nl.kaviyy.core.data.SearchResults
import com.nl.kaviyy.core.data.SearchSeries
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val searched = mutableListOf<String>()
    private val recentList = MutableStateFlow(listOf("old"))
    private var chapterLookup: AppResult<Int> = AppResult.Success(7)

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
        object : SearchRepository {
            override suspend fun search(
                server: Server,
                query: String,
            ): AppResult<SearchResults> {
                searched += query
                return AppResult.Success(SearchResults(series = listOf(SearchSeries(7, "Ember Road", null, 1))))
            }

            override suspend fun seriesForChapter(
                server: Server,
                chapterId: Int,
            ) = chapterLookup

            override suspend fun seriesForFile(
                server: Server,
                fileId: Int,
            ): AppResult<Int> = AppResult.Success(9)

            override suspend fun personField(
                server: Server,
                personId: Int,
            ): AppResult<FilterField> = AppResult.Success(FilterField.Colorist)

            override fun recents(server: Server) = recentList

            override suspend fun addRecent(
                server: Server,
                query: String,
            ) {
                recentList.value = listOf(query) + recentList.value
            }

            override suspend fun clearRecents(server: Server) {
                recentList.value = emptyList()
            }
        }

    private fun type(
        vm: SearchViewModel,
        text: String,
    ) {
        vm.query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun typingIsDebouncedAndEmptyShowsRecents() =
        runTest {
            val vm = SearchViewModel(session, repo)
            vm.state.test {
                runCurrent()
                assertEquals(listOf("old"), expectMostRecentItem().recents)
                type(vm, "e")
                runCurrent()
                advanceTimeBy(100)
                type(vm, "ember ")
                runCurrent()
                advanceTimeBy(299)
                runCurrent()
                assertEquals(emptyList(), searched)
                advanceTimeBy(2)
                runCurrent()
                assertEquals(listOf("ember"), searched)
                assertEquals(true, expectMostRecentItem().results is Results.Loaded)
                vm.clear()
                Snapshot.sendApplyNotifications()
                advanceTimeBy(301)
                runCurrent()
                assertEquals(Results.Idle, expectMostRecentItem().results)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun openingResolvesLookupsRemembersTheQueryAndReportsFailures() =
        runTest {
            val vm = SearchViewModel(session, repo)
            type(vm, "ember")
            vm.destinations.test {
                vm.open(SearchHit.Chapter(87))
                runCurrent()
                assertEquals(SearchDestination.Series(7), awaitItem())
                vm.open(SearchHit.Person(5, "Emberly Moss"))
                runCurrent()
                assertEquals(SearchDestination.Browse(FilterField.Colorist, 5, "Emberly Moss"), awaitItem())
                vm.open(SearchHit.Tag(3, "Post-apocalyptic"))
                runCurrent()
                assertEquals(SearchDestination.Browse(FilterField.Tags, 3, "Post-apocalyptic"), awaitItem())
                chapterLookup = AppResult.Failure(AppError.Network)
                vm.open(SearchHit.Chapter(88))
                runCurrent()
                expectNoEvents()
            }
            vm.state.test {
                advanceTimeBy(301)
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals(true, st.openFailed)
                assertEquals("ember", st.recents.first())
                vm.clearRecents()
                runCurrent()
                assertEquals(emptyList(), expectMostRecentItem().recents)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
