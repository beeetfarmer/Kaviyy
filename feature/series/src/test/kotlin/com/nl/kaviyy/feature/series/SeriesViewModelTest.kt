package com.nl.kaviyy.feature.series

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import com.nl.kaviyy.core.data.AdminRepository
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.LibraryQuery
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.PersonRepository
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.data.RelatedSeries
import com.nl.kaviyy.core.data.Relation
import com.nl.kaviyy.core.data.Review
import com.nl.kaviyy.core.data.ReviewRepository
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesMetadata
import com.nl.kaviyy.core.data.SeriesReviews
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
import kotlinx.coroutines.launch
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
    private var relatedCalls = 0
    private val marks = mutableListOf<String>()
    private var pushResult: AppResult<Unit> = AppResult.Success(Unit)
    private val savedReviews = mutableListOf<String>()
    private val reviews =
        object : ReviewRepository {
            override suspend fun reviews(
                server: Server,
                seriesId: Int,
                libraryType: Int,
            ) = AppResult.Success(
                SeriesReviews(listOf(Review("Mira", null, "Loved it", 4f, 0, 0, null, mine = true))),
            )

            override suspend fun saveReview(
                server: Server,
                seriesId: Int,
                body: String,
            ): AppResult<Unit> {
                savedReviews += body
                return AppResult.Success(Unit)
            }
        }
    private val readState =
        object : ReadStateRepository {
            override suspend fun setRead(
                server: Server,
                target: ReadTarget,
                read: Boolean,
            ): AppResult<Unit> {
                marks += "${target.key} $read"
                return pushResult
            }

            override suspend fun setRating(
                server: Server,
                seriesId: Int,
                rating: Float,
            ): AppResult<Unit> {
                marks += "rate $seriesId $rating"
                return pushResult
            }

            override suspend fun setWantToRead(
                server: Server,
                seriesId: Int,
                on: Boolean,
            ): AppResult<Unit> {
                marks += "wtr $seriesId $on"
                return pushResult
            }
        }
    private val adminCalls = mutableListOf<String>()
    private var adminResult: AppResult<Unit> = AppResult.Success(Unit)
    private val admin =
        object : AdminRepository {
            override suspend fun refreshMetadata(
                server: Server,
                libraryId: Int,
                seriesId: Int,
            ): AppResult<Unit> {
                adminCalls += "refresh $libraryId $seriesId"
                return adminResult
            }

            override suspend fun scan(
                server: Server,
                libraryId: Int,
                seriesId: Int,
            ): AppResult<Unit> {
                adminCalls += "scan $libraryId $seriesId"
                return adminResult
            }

            override suspend fun updateSummary(
                server: Server,
                seriesId: Int,
                summary: String,
            ): AppResult<Unit> {
                adminCalls += "summary $summary"
                return adminResult
            }
        }
    private val queries = mutableListOf<LibraryQuery>()
    private val described = mutableListOf<String>()
    private val people =
        object : PersonRepository {
            override suspend fun description(
                server: Server,
                name: String,
            ): AppResult<String?> {
                described += name
                return AppResult.Success("<p>Bio</p>")
            }
        }
    private var relatedResult: AppResult<List<RelatedSeries>> = AppResult.Failure(AppError.Network)

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
            ): Flow<SeriesDetail> = detail

            override suspend fun refreshSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<Unit> {
                refreshed += seriesId
                return refreshResult
            }

            override suspend fun relatedSeries(
                server: Server,
                seriesId: Int,
            ): AppResult<List<RelatedSeries>> {
                relatedCalls++
                return relatedResult
            }
        }

    @Test
    fun showsCachedDetailWithLibraryAndContinuePointAndRefreshes() =
        runTest {
            val vm =
                SeriesViewModel(session, repo, readState, reviews, admin, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
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
            val vm =
                SeriesViewModel(session, repo, readState, reviews, admin, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
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

    @Test
    fun relatedLoadsOnFirstOpenRetriesAfterFailureAndSheetsOpen() =
        runTest {
            val vm =
                SeriesViewModel(session, repo, readState, reviews, admin, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                vm.selectTab(SeriesTab.Related)
                runCurrent()
                assertEquals(RelatedState.Failed(AppError.Network), expectMostRecentItem().related)
                val sequel =
                    RelatedSeries(
                        Relation.Sequel,
                        SeriesEntity(1, 8, 3, "Ashes", "ashes", format = 0, pages = 1, pagesRead = 0),
                    )
                relatedResult = AppResult.Success(listOf(sequel))
                vm.selectTab(SeriesTab.Related)
                runCurrent()
                assertEquals(RelatedState.Loaded(listOf(sequel)), expectMostRecentItem().related)
                vm.selectTab(SeriesTab.Related) // loaded: no refetch
                assertEquals(2, relatedCalls)
                vm.showSheet(SeriesSheet.Chapter(49))
                runCurrent()
                val s = expectMostRecentItem()
                assertEquals(SeriesTab.Related, s.tab)
                assertEquals(SeriesSheet.Chapter(49), s.sheet)
            }
        }

    @Test
    fun marksPushAndRefreshOrReportPendingSync() =
        runTest {
            val vm =
                SeriesViewModel(session, repo, readState, reviews, admin, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                detail.value = detail.value.copy(metadata = SeriesMetadata(wantToRead = true))
                runCurrent()
                vm.toggleWantToRead()
                vm.showSheet(SeriesSheet.Volume(5))
                vm.setRead(ReadTarget.Volume(7, 5), read = true)
                runCurrent()
                assertEquals(listOf("wtr 7 false", "volume:5 true"), marks)
                assertEquals(null, expectMostRecentItem().sheet)
                assertEquals(listOf(7, 7), refreshed) // init + after the successful mark
                pushResult = AppResult.Failure(AppError.Network)
                vm.setRead(ReadTarget.Series(7), read = false)
                runCurrent()
                assertEquals(true, expectMostRecentItem().syncPending)
                assertEquals(listOf(7, 7), refreshed)
            }
        }

    @Test
    fun reviewsSheetLoadsPrefillsMyReviewAndSaves() =
        runTest {
            val vm =
                SeriesViewModel(session, repo, readState, reviews, admin, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                vm.showSheet(SeriesSheet.Reviews)
                runCurrent()
                assertEquals(true, expectMostRecentItem().reviews is ReviewsState.Loaded)
                assertEquals("Loved it", vm.reviewText.text.toString())
                vm.reviewText.setTextAndPlaceCursorAtEnd("Even better")
                vm.saveReview()
                vm.setRating(4f)
                runCurrent()
                assertEquals(listOf("Even better"), savedReviews)
                assertEquals("rate 7 4.0", marks.last())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun browse(
        field: FilterField,
        name: String,
    ) = BrowseViewModel(
        session,
        repo,
        people,
        SavedStateHandle(mapOf(BROWSE_FIELD_ARG to field.name, BROWSE_ID_ARG to 8, BROWSE_NAME_ARG to name)),
    )

    @Test
    fun browseQueriesAllLibrariesAndDescribesPeopleOnly() =
        runTest {
            val writer = browse(FilterField.Writers, "Mira Okafor")
            writer.state.test {
                runCurrent()
                assertEquals("<p>Bio</p>", expectMostRecentItem().description)
                cancelAndIgnoreRemainingEvents()
            }
            backgroundScope.launch { writer.series.collect {} }
            val genre = browse(FilterField.Genres, "Fantasy")
            backgroundScope.launch { genre.series.collect {} }
            runCurrent()
            assertEquals(listOf("Mira Okafor"), described)
            assertEquals(listOf<Int?>(null, null), queries.map { it.libraryId })
            val rule =
                queries
                    .last()
                    .filter.rules
                    .single()
            assertEquals(FilterField.Genres to listOf("8"), rule.field to rule.values.map { it.code })
        }

    @Test
    fun adminActionsRunAndReportTheirOutcome() =
        runTest {
            val vm =
                SeriesViewModel(session, repo, readState, reviews, admin, SavedStateHandle(mapOf(SERIES_ID_ARG to 7)))
            vm.state.test {
                runCurrent()
                detail.value =
                    SeriesDetail(
                        SeriesEntity(1, 7, 3, "Ember Road", "ember road", format = 0, pages = 1, pagesRead = 0),
                        emptyList(),
                        emptyList(),
                    )
                runCurrent()
                vm.editSummary() // prefill sanitizing is covered by SeriesFormatTest (needs Robolectric)
                runCurrent()
                assertEquals(SeriesSheet.EditSummary, expectMostRecentItem().sheet)
                assertEquals("", vm.summaryText.text.toString())
                vm.summaryText.setTextAndPlaceCursorAtEnd("New")
                vm.saveSummary()
                runCurrent()
                var st = expectMostRecentItem()
                assertEquals(null, st.sheet)
                assertEquals(AdminNotice.Saved, st.adminNotice)
                assertEquals(listOf(7, 7), refreshed) // init + after the save
                adminResult = AppResult.Failure(AppError.Forbidden)
                vm.scan()
                runCurrent()
                st = expectMostRecentItem()
                assertEquals(AdminNotice.Failed, st.adminNotice)
                val lib = st.detail?.series?.libraryId
                assertEquals(listOf("summary New", "scan $lib 7"), adminCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
