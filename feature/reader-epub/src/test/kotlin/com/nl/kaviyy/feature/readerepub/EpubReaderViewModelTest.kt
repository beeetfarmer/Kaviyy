package com.nl.kaviyy.feature.readerepub

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.nl.kaviyy.core.data.AnnotationDraft
import com.nl.kaviyy.core.data.AnnotationRepository
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.BookFileRepository
import com.nl.kaviyy.core.data.Highlight
import com.nl.kaviyy.core.data.ImageReaderRepository
import com.nl.kaviyy.core.data.ProgressRepository
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.data.ReaderDisplay
import com.nl.kaviyy.core.data.ReaderDisplayRepository
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.data.ResumePosition
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class) // Readium locators use org.json
class EpubReaderViewModelTest {
    private val book = testBook

    @get:Rule
    val main = MainDispatcherRule()

    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val active = MutableStateFlow<Server?>(server)
    private val profile = MutableStateFlow(ReadingProfileDto())
    private val updates = mutableListOf<ReadingProfileDto>()
    private val display = MutableStateFlow(ReaderDisplay())
    private var fileResult: AppResult<File> = AppResult.Success(File("book.epub"))
    private val opened = mutableListOf<File>()

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

    private val chapters =
        object : ImageReaderRepository {
            override suspend fun chapter(
                server: Server,
                chapterId: Int,
            ): AppResult<ReaderChapter> =
                AppResult.Success(ReaderChapter(chapterId, 9, 3, 2, "Book", null, null, null, false, 0, 3))

            override suspend fun next(
                server: Server,
                chapter: ReaderChapter,
            ): AppResult<Int?> = AppResult.Success(null)

            override suspend fun previous(
                server: Server,
                chapter: ReaderChapter,
            ): AppResult<Int?> = AppResult.Success(null)
        }

    private val files =
        object : BookFileRepository {
            override suspend fun file(
                server: Server,
                chapterId: Int,
                extension: String,
            ) = fileResult
        }

    private val loader =
        object : PublicationLoader {
            override suspend fun open(file: File): AppResult<Publication> {
                opened += file
                return AppResult.Success(book)
            }
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

    private val displayRepo =
        object : ReaderDisplayRepository {
            override val display = this@EpubReaderViewModelTest.display

            override fun update(change: (ReaderDisplay) -> ReaderDisplay) {
                this@EpubReaderViewModelTest.display.value = change(this@EpubReaderViewModelTest.display.value)
            }
        }

    private var resumeAt = ResumePosition(0)
    private val recorded = mutableListOf<Int>()
    private val pushed = mutableListOf<Int>()
    private val progress =
        object : ProgressRepository {
            override suspend fun resume(
                server: Server,
                chapter: ReaderChapter,
            ) = resumeAt

            override suspend fun record(
                server: Server,
                chapter: ReaderChapter,
                page: Int,
                position: String?,
            ) {
                recorded += page
            }

            override suspend fun push(
                server: Server,
                chapterId: Int,
            ): AppResult<Unit> {
                pushed += chapterId
                return AppResult.Success(Unit)
            }
        }

    private val marks = MutableStateFlow(emptyList<Highlight>())
    private val annotationCalls = mutableListOf<String>()
    private val annotations =
        object : AnnotationRepository {
            override fun annotations(
                server: Server,
                chapterId: Int,
            ) = marks

            override suspend fun refresh(
                server: Server,
                chapterId: Int,
            ): AppResult<Unit> = AppResult.Success(Unit)

            override suspend fun create(
                server: Server,
                chapter: ReaderChapter,
                draft: AnnotationDraft,
            ): AppResult<Unit> {
                annotationCalls += "create ${draft.page} ${draft.text} ${draft.color} ${draft.note}"
                return AppResult.Success(Unit)
            }

            override suspend fun update(
                server: Server,
                chapter: ReaderChapter,
                uuid: String,
                color: Int,
                note: String?,
            ): AppResult<Unit> {
                annotationCalls += "update $uuid $color $note"
                return AppResult.Success(Unit)
            }

            override suspend fun delete(
                server: Server,
                uuid: String,
            ): AppResult<Unit> {
                annotationCalls += "delete $uuid"
                return AppResult.Success(Unit)
            }
        }

    private fun vm() =
        EpubReaderViewModel(
            session,
            chapters,
            files,
            loader,
            settings,
            displayRepo,
            progress,
            annotations,
            SavedStateHandle(mapOf(EPUB_SERIES_ARG to 9, EPUB_CHAPTER_ARG to 201)),
        )

    @Test
    fun opensTheCachedFileWithReadium() =
        runTest {
            vm().state.test {
                runCurrent()
                val st = expectMostRecentItem()
                assertNotNull(st.publication)
                assertEquals(false, st.loading)
                assertEquals("Book", st.chapter?.seriesName)
                assertEquals(listOf(File("book.epub")), opened)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resumesAtTheSavedPageAndRecordsReading() =
        runTest {
            resumeAt = ResumePosition(1)
            val vm = vm()
            vm.state.test {
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals("ch2.xhtml", st.locator?.href.toString())
                assertEquals(3, st.toc.size)
                st.locator?.let(vm::onLocator) // the navigator echoes the start: not reading
                runCurrent()
                assertEquals(emptyList(), recorded)
                vm.onLocator(at("ch3.xhtml", 0.2))
                runCurrent()
                assertEquals(listOf(1), recorded) // last item not finished yet
                advanceTimeBy(2_000)
                runCurrent()
                assertEquals(listOf(201), pushed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun selectionsAndHighlightsGoThroughTheToolbar() =
        runTest {
            marks.value = listOf(Highlight("h1", 0, "One", 1, "old"))
            val vm = vm()
            vm.state.test {
                runCurrent()
                val selected = at("ch2.xhtml", 0.5).copy(text = Locator.Text(highlight = " a phrase "))
                vm.onSelection(selected, 300f, 340f)
                assertEquals("a phrase", expectMostRecentItem().highlightTarget?.text)
                vm.pickColor(2)
                runCurrent()
                assertEquals(null, expectMostRecentItem().highlightTarget)
                vm.onHighlightTapped("h1", 10f, 40f)
                assertEquals(1, expectMostRecentItem().highlightTarget?.color)
                vm.pickColor(3) // recolour keeps the note
                runCurrent()
                vm.onHighlightTapped("h1", 10f, 40f)
                vm.pickColor(1) // its own colour again: remove
                runCurrent()
                vm.onSelection(selected, 300f, 340f)
                vm.editNote()
                assertEquals("", expectMostRecentItem().noteEditor?.initial)
                vm.saveNote("  why  ")
                runCurrent()
                vm.onHighlightTapped("h1", 10f, 40f)
                vm.editNote()
                assertEquals("old", expectMostRecentItem().noteEditor?.initial)
                vm.saveNote(" ") // blank removes the note
                runCurrent()
                assertEquals(
                    listOf(
                        "create 1 a phrase 2 null",
                        "update h1 3 old",
                        "delete h1",
                        "create 1 a phrase 0 why",
                        "update h1 1 null",
                    ),
                    annotationCalls,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun onlyWebLinksFromTheBookAreOffered() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                vm.onExternalLink("intent://evil#Intent;end")
                vm.onExternalLink("javascript:alert(1)")
                assertEquals(null, expectMostRecentItem().externalLink)
                vm.onExternalLink("https://example.org/notes")
                assertEquals("https://example.org/notes", expectMostRecentItem().externalLink)
                vm.closeLink()
                assertEquals(null, expectMostRecentItem().externalLink)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun missingDownloadRoleIsReportedAndRetryRecovers() =
        runTest {
            fileResult = AppResult.Failure(AppError.Forbidden)
            val vm = vm()
            vm.state.test {
                runCurrent()
                assertEquals(AppError.Forbidden, expectMostRecentItem().error)
                fileResult = AppResult.Success(File("book.epub"))
                vm.retry()
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals(null, st.error)
                assertNotNull(st.publication)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun settingsSplitBetweenTheProfileAndTheDevice() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                val st = expectMostRecentItem()
                vm.updateSettings(st.settings.copy(font = BookFont.Serif, brightness = 0.4f))
                runCurrent()
                assertEquals(ReaderDisplay(brightness = 0.4f, bookFont = "serif"), display.value)
                assertEquals(emptyList(), updates) // nothing in the Kavita profile changed
                vm.updateSettings(expectMostRecentItem().settings.copy(theme = BookTheme.White, fontSize = 130))
                runCurrent()
                assertEquals("White" to 130, updates.single().let { it.bookReaderThemeName to it.bookReaderFontSize })
                assertEquals(BookTheme.White, expectMostRecentItem().settings.theme)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
