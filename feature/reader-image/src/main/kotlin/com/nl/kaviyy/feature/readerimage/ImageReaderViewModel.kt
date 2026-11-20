package com.nl.kaviyy.feature.readerimage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.BookmarkRepository
import com.nl.kaviyy.core.data.ImageReaderRepository
import com.nl.kaviyy.core.data.ProgressRepository
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.data.ReaderDisplay
import com.nl.kaviyy.core.data.ReaderDisplayRepository
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ReadingProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav args: the series (for its reader settings) and the chapter to open. */
const val READER_SERIES_ARG = "seriesId"
const val READER_CHAPTER_ARG = "chapterId"

private const val KEY_CHAPTER = "reader.chapter"
private const val KEY_PAGE = "reader.page"
private const val STOP_TIMEOUT_MS = 5_000L

/** Kavita `ReadingDirection`. */
private const val DIRECTION_RTL = 1
private const val DIRECTION_LTR = 0

data class ImageReaderUiState(
    val server: Server? = null,
    val chapter: ReaderChapter? = null,
    /** 0-based. */
    val page: Int = 0,
    val rightToLeft: Boolean = false,
    val layout: ReaderLayout = ReaderLayout.Single,
    /** Double page: swap the two pages of a pair (Kavita "Double (Manga)"). */
    val reversedPairs: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val settingsOpen: Boolean = false,
    val chromeVisible: Boolean = true,
    val loading: Boolean = true,
    val error: AppError? = null,
    val nextChapterId: Int? = null,
    val previousChapterId: Int? = null,
    /** Bookmarked pages of this chapter. */
    val bookmarks: Set<Int> = emptySet(),
) {
    val pages: Int get() = chapter?.pages ?: 0

    /** Double page spreads; one page per spread otherwise. */
    val spreads: List<List<Int>>
        get() =
            if (layout == ReaderLayout.Double) {
                spreads(pages, chapter?.pageSizes.orEmpty())
            } else {
                List(pages) { listOf(it) }
            }
}

private data class Session(
    val chapter: ReaderChapter? = null,
    val page: Int = 0,
    val chromeVisible: Boolean = true,
    val settingsOpen: Boolean = false,
    val loading: Boolean = true,
    val error: AppError? = null,
    val next: Int? = null,
    val previous: Int? = null,
)

/** Screen 06, single page mode. Page index + chapter survive process death; progress sync is M10.5. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ImageReaderViewModel
    @Inject
    constructor(
        private val auth: AuthSession,
        private val repository: ImageReaderRepository,
        private val settingsRepository: ReaderSettingsRepository,
        private val bookmarkRepository: BookmarkRepository,
        private val progressRepository: ProgressRepository,
        private val displayRepository: ReaderDisplayRepository,
        private val saved: SavedStateHandle,
    ) : ViewModel() {
        private val seriesId: Int = checkNotNull(saved.get<Int>(READER_SERIES_ARG)) { "missing $READER_SERIES_ARG" }
        private val session = MutableStateFlow(Session(page = saved[KEY_PAGE] ?: 0))
        private var loadJob: Job? = null
        private var pushJob: Job? = null

        private val settings =
            auth.activeServer.flatMapLatest { s ->
                if (s ==
                    null
                ) {
                    flowOf(ReadingProfileDto())
                } else {
                    settingsRepository.settings(s, seriesId)
                }
            }

        private val chapterId = session.map { it.chapter?.id }.distinctUntilChanged()

        private val bookmarks =
            combine(auth.activeServer, chapterId, ::Pair).flatMapLatest { (s, id) ->
                if (s == null || id == null) flowOf(emptySet()) else bookmarkRepository.pages(s, id)
            }

        val state: StateFlow<ImageReaderUiState> =
            combine(
                auth.activeServer,
                session,
                settings,
                bookmarks,
                displayRepository.display,
            ) { s, st, prefs, marks, device ->
                ImageReaderUiState(
                    server = s,
                    chapter = st.chapter,
                    page = st.page,
                    rightToLeft = prefs.readingDirection == DIRECTION_RTL,
                    layout = prefs.layout,
                    reversedPairs = prefs.reversedPairs,
                    settings =
                        ReaderSettings(
                            scaling = prefs.scalingOption,
                            background = prefs.backgroundColor,
                            swipeToTurn = prefs.swipeToPaginate,
                            autoCloseMenu = prefs.autoCloseMenu,
                            keepScreenOn = device.keepScreenOn,
                            brightness = device.brightness,
                        ),
                    settingsOpen = st.settingsOpen,
                    chromeVisible = st.chromeVisible,
                    loading = st.loading,
                    error = st.error,
                    nextChapterId = st.next,
                    previousChapterId = st.previous,
                    bookmarks = marks,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImageReaderUiState())

        init {
            val start =
                saved.get<Int>(KEY_CHAPTER)
                    ?: checkNotNull(saved.get<Int>(READER_CHAPTER_ARG)) { "missing $READER_CHAPTER_ARG" }
            // After process death the saved page wins; a fresh open resumes from the read progress.
            open(start, page = saved[KEY_PAGE] ?: RESUME)
        }

        fun retry() = session.value.let { open(it.chapter?.id ?: saved[KEY_CHAPTER] ?: return, it.page) }

        fun toggleChrome() = session.update { it.copy(chromeVisible = !it.chromeVisible) }

        /** From the pager, the scrubber or a tap zone. */
        fun goTo(page: Int) {
            val before = session.value.page
            val p = show(page)
            if (p != before) record(p)
        }

        private fun show(page: Int): Int {
            val last = (session.value.chapter?.pages ?: 1) - 1
            val p = page.coerceIn(0, last.coerceAtLeast(0))
            saved[KEY_PAGE] = p
            session.update { it.copy(page = p) }
            return p
        }

        /** Room now; the server after a pause in paging (the outbox keeps it if the reader closes first). */
        private fun record(page: Int) {
            val chapter = session.value.chapter ?: return
            // A double spread counts as read up to its right-hand page.
            val reached = state.value.spreads.let { it.getOrNull(it.indexOfPage(page))?.last() } ?: page
            pushJob?.cancel()
            pushJob =
                viewModelScope.launch {
                    val s = auth.activeServer.first() ?: return@launch
                    progressRepository.record(s, chapter, maxOf(page, reached))
                    delay(PUSH_DEBOUNCE_MS)
                    progressRepository.push(s, chapter.id)
                }
        }

        /** Next page (spread in double page mode), or the next chapter after the last one. */
        fun nextPage() {
            val spreads = state.value.spreads
            val st = session.value
            if (spreads.isEmpty()) return
            val at = spreads.indexOfPage(st.page)
            if (at < spreads.lastIndex) goTo(spreads[at + 1].first()) else st.next?.let { open(it, page = 0) }
            autoClose()
        }

        /** Previous page (spread), or the previous chapter's last page. */
        fun previousPage() {
            val spreads = state.value.spreads
            val st = session.value
            val at = spreads.indexOfPage(st.page)
            if (at > 0) goTo(spreads[at - 1].first()) else st.previous?.let { open(it, page = LAST_PAGE) }
            autoClose()
        }

        /** Kavita "auto close menu": turning a page hides the chrome. */
        private fun autoClose() {
            if (state.value.settings.autoCloseMenu) session.update { it.copy(chromeVisible = false) }
        }

        fun showSettings(open: Boolean) = session.update { it.copy(settingsOpen = open) }

        /** Account settings go to the series reading profile; device ones stay on this device. */
        fun updateSettings(new: ReaderSettings) {
            val old = state.value.settings
            if (new.keepScreenOn != old.keepScreenOn || new.brightness != old.brightness) {
                displayRepository.update(ReaderDisplay(new.keepScreenOn, new.brightness))
            }
            if (new.copy(keepScreenOn = old.keepScreenOn, brightness = old.brightness) != old) {
                updateProfile {
                    it.copy(
                        scalingOption = new.scaling,
                        backgroundColor = new.background,
                        swipeToPaginate = new.swipeToTurn,
                        autoCloseMenu = new.autoCloseMenu,
                    )
                }
            }
        }

        fun setLayout(layout: ReaderLayout) = updateProfile { it.withLayout(layout) }

        fun nextChapter() = session.value.next?.let { open(it, page = 0) }

        fun previousChapter() = session.value.previous?.let { open(it, page = 0) }

        fun toggleDirection() =
            updateProfile {
                it.copy(
                    readingDirection =
                        if (it.readingDirection ==
                            DIRECTION_RTL
                        ) {
                            DIRECTION_LTR
                        } else {
                            DIRECTION_RTL
                        },
                )
            }

        /** Local first; a failed push waits in the outbox (M17). */
        private fun updateProfile(change: (ReadingProfileDto) -> ReadingProfileDto) {
            viewModelScope.launch {
                val s = auth.activeServer.first() ?: return@launch
                val libraryId = session.value.chapter?.libraryId ?: return@launch
                settingsRepository.update(s, libraryId, seriesId, change(settings.first()))
            }
        }

        /** Bookmarks or un-bookmarks [page] (the current page by default). */
        fun toggleBookmark(page: Int = session.value.page) {
            val chapter = session.value.chapter ?: return
            val on = page !in state.value.bookmarks
            viewModelScope.launch {
                val s = auth.activeServer.first() ?: return@launch
                bookmarkRepository.set(s, chapter, page, on)
            }
        }

        private fun open(
            chapterId: Int,
            page: Int,
        ) {
            loadJob?.cancel()
            // Leaving a chapter: send its position now rather than after the debounce.
            session.value.chapter?.takeIf { it.id != chapterId }?.let { left ->
                pushJob = null
                viewModelScope.launch { auth.activeServer.first()?.let { progressRepository.push(it, left.id) } }
            }
            saved[KEY_CHAPTER] = chapterId
            session.update { it.copy(loading = true, error = null) }
            loadJob =
                viewModelScope.launch {
                    val s = auth.activeServer.first() ?: return@launch
                    when (val r = repository.chapter(s, chapterId)) {
                        is AppResult.Failure -> {
                            session.update { it.copy(loading = false, error = r.error) }
                        }

                        is AppResult.Success -> {
                            val chapter = r.value
                            val next = async { repository.next(s, chapter).getOrNull() }
                            val previous = async { repository.previous(s, chapter).getOrNull() }
                            val start =
                                when (page) {
                                    LAST_PAGE -> chapter.pages - 1
                                    RESUME -> progressRepository.resume(s, chapter)
                                    else -> page
                                }
                            session.update { it.copy(chapter = chapter, loading = false, next = null, previous = null) }
                            show(start)
                            session.update { it.copy(next = next.await(), previous = previous.await()) }
                            settingsRepository.refresh(s, chapter.libraryId, seriesId)
                            bookmarkRepository.refresh(s, chapter.id)
                        }
                    }
                }
        }

        private companion object {
            const val LAST_PAGE = -1
            const val RESUME = -2
            const val PUSH_DEBOUNCE_MS = 1_500L
        }
    }
