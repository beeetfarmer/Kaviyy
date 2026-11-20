package com.nl.kaviyy.feature.readerimage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ImageReaderRepository
import com.nl.kaviyy.core.data.ReaderChapter
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val chromeVisible: Boolean = true,
    val loading: Boolean = true,
    val error: AppError? = null,
    val nextChapterId: Int? = null,
    val previousChapterId: Int? = null,
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
        private val saved: SavedStateHandle,
    ) : ViewModel() {
        private val seriesId: Int = checkNotNull(saved.get<Int>(READER_SERIES_ARG)) { "missing $READER_SERIES_ARG" }
        private val session = MutableStateFlow(Session(page = saved[KEY_PAGE] ?: 0))
        private var loadJob: Job? = null

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

        val state: StateFlow<ImageReaderUiState> =
            combine(auth.activeServer, session, settings) { s, st, prefs ->
                ImageReaderUiState(
                    server = s,
                    chapter = st.chapter,
                    page = st.page,
                    rightToLeft = prefs.readingDirection == DIRECTION_RTL,
                    layout = prefs.layout,
                    reversedPairs = prefs.reversedPairs,
                    chromeVisible = st.chromeVisible,
                    loading = st.loading,
                    error = st.error,
                    nextChapterId = st.next,
                    previousChapterId = st.previous,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImageReaderUiState())

        init {
            val start =
                saved.get<Int>(KEY_CHAPTER)
                    ?: checkNotNull(saved.get<Int>(READER_CHAPTER_ARG)) { "missing $READER_CHAPTER_ARG" }
            open(start, page = session.value.page)
        }

        fun retry() = session.value.let { open(it.chapter?.id ?: saved[KEY_CHAPTER] ?: return, it.page) }

        fun toggleChrome() = session.update { it.copy(chromeVisible = !it.chromeVisible) }

        /** From the pager, the scrubber or a tap zone. */
        fun goTo(page: Int) {
            val last = (session.value.chapter?.pages ?: 1) - 1
            val p = page.coerceIn(0, last.coerceAtLeast(0))
            saved[KEY_PAGE] = p
            session.update { it.copy(page = p) }
        }

        /** Next page (spread in double page mode), or the next chapter after the last one. */
        fun nextPage() {
            val spreads = state.value.spreads
            val st = session.value
            if (spreads.isEmpty()) return
            val at = spreads.indexOfPage(st.page)
            if (at < spreads.lastIndex) goTo(spreads[at + 1].first()) else st.next?.let { open(it, page = 0) }
        }

        /** Previous page (spread), or the previous chapter's last page. */
        fun previousPage() {
            val spreads = state.value.spreads
            val st = session.value
            val at = spreads.indexOfPage(st.page)
            if (at > 0) goTo(spreads[at - 1].first()) else st.previous?.let { open(it, page = LAST_PAGE) }
        }

        fun setLayout(layout: ReaderLayout) = updateSettings { it.withLayout(layout) }

        fun nextChapter() = session.value.next?.let { open(it, page = 0) }

        fun previousChapter() = session.value.previous?.let { open(it, page = 0) }

        fun toggleDirection() =
            updateSettings {
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
        private fun updateSettings(change: (ReadingProfileDto) -> ReadingProfileDto) {
            viewModelScope.launch {
                val s = auth.activeServer.first() ?: return@launch
                val libraryId = session.value.chapter?.libraryId ?: return@launch
                settingsRepository.update(s, libraryId, seriesId, change(settings.first()))
            }
        }

        private fun open(
            chapterId: Int,
            page: Int,
        ) {
            loadJob?.cancel()
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
                            val start = if (page == LAST_PAGE) chapter.pages - 1 else page
                            session.update { it.copy(chapter = chapter, loading = false, next = null, previous = null) }
                            goTo(start)
                            session.update { it.copy(next = next.await(), previous = previous.await()) }
                            settingsRepository.refresh(s, chapter.libraryId, seriesId)
                        }
                    }
                }
        }

        private companion object {
            const val LAST_PAGE = -1
        }
    }
