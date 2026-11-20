package com.nl.kaviyy.feature.readerepub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.BookFileRepository
import com.nl.kaviyy.core.data.ImageReaderRepository
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.data.ReaderDisplayRepository
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.flatMap
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ReadingProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import javax.inject.Inject

const val EPUB_SERIES_ARG = "seriesId"
const val EPUB_CHAPTER_ARG = "chapterId"

private const val STOP_TIMEOUT_MS = 5_000L
private const val EXTENSION = "epub"

data class EpubReaderUiState(
    val chapter: ReaderChapter? = null,
    val publication: Publication? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val chromeVisible: Boolean = true,
    val settings: BookSettings = BookSettings(),
    val settingsOpen: Boolean = false,
    /** 0..1 through the book. */
    val progression: Float = 0f,
    /** 1-based Readium position ("page") and the total. */
    val position: Int? = null,
    val positions: Int = 0,
    /** Title of the current resource, when the TOC names it. */
    val section: String? = null,
    /** Last reported position; a recreated navigator reopens here. */
    val locator: Locator? = null,
)

private data class Session(
    val chapter: ReaderChapter? = null,
    val publication: Publication? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val chromeVisible: Boolean = true,
    val settingsOpen: Boolean = false,
    val locator: Locator? = null,
    val positions: Int = 0,
)

/** Screen 07. The original EPUB is fetched into the book cache (Download role) and opened with Readium. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpubReaderViewModel
    @Inject
    constructor(
        private val auth: AuthSession,
        private val chapters: ImageReaderRepository,
        private val files: BookFileRepository,
        private val loader: PublicationLoader,
        private val settingsRepository: ReaderSettingsRepository,
        private val displayRepository: ReaderDisplayRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val seriesId: Int = checkNotNull(saved.get<Int>(EPUB_SERIES_ARG)) { "missing $EPUB_SERIES_ARG" }
        private val chapterId: Int = checkNotNull(saved.get<Int>(EPUB_CHAPTER_ARG)) { "missing $EPUB_CHAPTER_ARG" }
        private val session = MutableStateFlow(Session())
        private var loadJob: Job? = null

        private val profile =
            auth.activeServer.flatMapLatest { s ->
                if (s ==
                    null
                ) {
                    flowOf(ReadingProfileDto())
                } else {
                    settingsRepository.settings(s, seriesId)
                }
            }

        val state: StateFlow<EpubReaderUiState> =
            combine(session, profile, displayRepository.display) { st, prefs, device ->
                EpubReaderUiState(
                    chapter = st.chapter,
                    publication = st.publication,
                    loading = st.loading,
                    error = st.error,
                    chromeVisible = st.chromeVisible,
                    settings = bookSettings(prefs, device),
                    settingsOpen = st.settingsOpen,
                    progression =
                        st.locator
                            ?.locations
                            ?.totalProgression
                            ?.toFloat() ?: 0f,
                    position = st.locator?.locations?.position,
                    positions = st.positions,
                    section = st.locator?.title,
                    locator = st.locator,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EpubReaderUiState())

        init {
            load()
        }

        fun retry() = load()

        fun toggleChrome() = session.update { it.copy(chromeVisible = !it.chromeVisible) }

        fun showSettings(open: Boolean) = session.update { it.copy(settingsOpen = open) }

        /** Where the navigator is now (progress sync is M11.2). */
        fun onLocator(locator: Locator) = session.update { it.copy(locator = locator) }

        fun updateSettings(new: BookSettings) {
            displayRepository.update {
                it.copy(bookFont = new.font.key, keepScreenOn = new.keepScreenOn, brightness = new.brightness)
            }
            viewModelScope.launch {
                val s = auth.activeServer.first() ?: return@launch
                val libraryId = session.value.chapter?.libraryId ?: return@launch
                val current = profile.first()
                val updated = current.with(new)
                if (updated != current) settingsRepository.update(s, libraryId, seriesId, updated)
            }
        }

        private fun load() {
            loadJob?.cancel()
            session.update { it.copy(loading = true, error = null) }
            loadJob =
                viewModelScope.launch {
                    val s = auth.activeServer.first() ?: return@launch
                    when (val r = open(s)) {
                        is AppResult.Failure -> session.update { it.copy(loading = false, error = r.error) }
                        is AppResult.Success -> Unit
                    }
                }
        }

        private suspend fun open(server: Server): AppResult<Unit> =
            chapters
                .chapter(server, chapterId)
                .flatMap { chapter ->
                    session.update { it.copy(chapter = chapter) }
                    settingsRepository.refresh(server, chapter.libraryId, seriesId)
                    files.file(server, chapterId, EXTENSION)
                }.flatMap { loader.open(it) }
                .map { publication ->
                    val positions = publication.positions().size
                    session.update { it.copy(publication = publication, positions = positions, loading = false) }
                }

        override fun onCleared() {
            session.value.publication?.close()
        }
    }
