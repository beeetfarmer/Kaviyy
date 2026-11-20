package com.nl.kaviyy.feature.readerimage

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.BookmarkRepository
import com.nl.kaviyy.core.data.ImageReaderRepository
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.ReadingProfileDto
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ImageReaderViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val profile = MutableStateFlow(ReadingProfileDto())
    private val updates = mutableListOf<ReadingProfileDto>()
    private var failChapter: Int? = null

    private fun chapter(id: Int) =
        ReaderChapter(id, 7, 5, 1, "Ember Road", "5", "$id", null, false, pages = 3, format = 1)

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
        object : ImageReaderRepository {
            override suspend fun chapter(
                server: Server,
                chapterId: Int,
            ): AppResult<ReaderChapter> =
                if (chapterId ==
                    failChapter
                ) {
                    AppResult.Failure(AppError.Network)
                } else {
                    AppResult.Success(chapter(chapterId))
                }

            override suspend fun next(
                server: Server,
                chapter: ReaderChapter,
            ): AppResult<Int?> = AppResult.Success((chapter.id + 1).takeIf { it <= 50 })

            override suspend fun previous(
                server: Server,
                chapter: ReaderChapter,
            ): AppResult<Int?> = AppResult.Success((chapter.id - 1).takeIf { it >= 48 })
        }

    private val settings =
        object : ReaderSettingsRepository {
            override fun settings(
                server: Server,
                seriesId: Int,
            ) = profile

            override suspend fun refresh(
                server: Server,
                libraryId: Int,
                seriesId: Int,
            ): AppResult<Unit> = AppResult.Success(Unit)

            override suspend fun update(
                server: Server,
                libraryId: Int,
                seriesId: Int,
                settings: ReadingProfileDto,
            ): AppResult<Unit> {
                updates += settings
                profile.value = settings
                return AppResult.Success(Unit)
            }
        }

    private val marks = MutableStateFlow(emptySet<Int>())
    private val bookmarks =
        object : BookmarkRepository {
            override fun pages(
                server: Server,
                chapterId: Int,
            ) = marks

            override suspend fun refresh(
                server: Server,
                chapterId: Int,
            ): AppResult<Unit> = AppResult.Success(Unit)

            override suspend fun set(
                server: Server,
                chapter: ReaderChapter,
                page: Int,
                on: Boolean,
            ): AppResult<Unit> {
                marks.value = if (on) marks.value + page else marks.value - page
                return AppResult.Success(Unit)
            }
        }

    private fun vm(
        saved: SavedStateHandle = SavedStateHandle(mapOf(READER_SERIES_ARG to 7, READER_CHAPTER_ARG to 49)),
    ) = ImageReaderViewModel(session, repo, settings, bookmarks, saved)

    @Test
    fun pagesFlowIntoNeighbouringChapters() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                var st = expectMostRecentItem()
                assertEquals(49 to 0, st.chapter?.id to st.page)
                assertEquals(50 to 48, st.nextChapterId to st.previousChapterId)
                vm.goTo(99)
                runCurrent()
                assertEquals(2, expectMostRecentItem().page) // clamped
                vm.nextPage() // past the last page: next chapter, first page
                runCurrent()
                st = expectMostRecentItem()
                assertEquals(50 to 0, st.chapter?.id to st.page)
                assertEquals(null, st.nextChapterId)
                vm.previousPage() // before the first page: previous chapter, last page
                runCurrent()
                st = expectMostRecentItem()
                assertEquals(49 to 2, st.chapter?.id to st.page)
                vm.previousChapter()
                runCurrent()
                assertEquals(48 to 0, expectMostRecentItem().let { it.chapter?.id to it.page })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun directionComesFromTheProfileAndTogglesThroughIt() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                assertEquals(false, expectMostRecentItem().rightToLeft)
                vm.toggleDirection()
                runCurrent()
                assertEquals(true, expectMostRecentItem().rightToLeft)
                assertEquals(listOf(1), updates.map { it.readingDirection })
                vm.toggleChrome()
                runCurrent()
                assertEquals(false, expectMostRecentItem().chromeVisible)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun bookmarkTogglesTheCurrentPage() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                vm.goTo(2)
                vm.toggleBookmark()
                runCurrent()
                assertEquals(setOf(2), expectMostRecentItem().bookmarks)
                vm.toggleBookmark(2)
                runCurrent()
                assertEquals(emptySet(), expectMostRecentItem().bookmarks)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun doublePageTurnsBySpreadAndLayoutIsSaved() =
        runTest {
            profile.value = ReadingProfileDto(layoutMode = 2)
            val vm = vm()
            vm.state.test {
                runCurrent()
                assertEquals(ReaderLayout.Double, expectMostRecentItem().layout)
                vm.nextPage() // cover alone, then pages 1-2
                runCurrent()
                assertEquals(1, expectMostRecentItem().page)
                vm.nextPage() // last spread: next chapter
                runCurrent()
                assertEquals(50, expectMostRecentItem().chapter?.id)
                vm.setLayout(ReaderLayout.Webtoon)
                runCurrent()
                assertEquals(ReaderLayout.Webtoon, expectMostRecentItem().layout)
                assertEquals(listOf(2), updates.map { it.readerMode })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun positionSurvivesProcessDeathAndFailuresRetry() =
        runTest {
            val saved = SavedStateHandle(mapOf(READER_SERIES_ARG to 7, READER_CHAPTER_ARG to 49))
            vm(saved).run {
                state.test {
                    runCurrent()
                    nextChapter()
                    runCurrent()
                    goTo(1)
                    runCurrent()
                    cancelAndIgnoreRemainingEvents()
                }
            }
            failChapter = 50
            val restored = vm(saved)
            restored.state.test {
                runCurrent()
                assertEquals(AppError.Network, expectMostRecentItem().error)
                failChapter = null
                restored.retry()
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals(50 to 1, st.chapter?.id to st.page)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
