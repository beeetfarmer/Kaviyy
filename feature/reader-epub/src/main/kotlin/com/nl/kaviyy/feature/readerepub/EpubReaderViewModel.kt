package com.nl.kaviyy.feature.readerepub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AnnotationRepository
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.BookFileRepository
import com.nl.kaviyy.core.data.Highlight
import com.nl.kaviyy.core.data.ImageReaderRepository
import com.nl.kaviyy.core.data.ProgressRepository
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.data.ReaderDisplayRepository
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.flatMap
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.webUrl
import com.nl.kaviyy.core.network.ReadingProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val toc: List<TocEntry> = emptyList(),
    val tocOpen: Boolean = false,
    val highlights: List<Highlight> = emptyList(),
    /** Selection or highlight the toolbar is shown for. */
    val highlightTarget: HighlightTarget? = null,
    val noteEditor: NoteEditor? = null,
    /** An http(s) link from the book waiting for the user to confirm opening it. */
    val externalLink: String? = null,
)

private data class Session(
    val chapter: ReaderChapter? = null,
    val publication: Publication? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val chromeVisible: Boolean = true,
    val settingsOpen: Boolean = false,
    val tocOpen: Boolean = false,
    val locator: Locator? = null,
    val positions: Int = 0,
    val toc: List<TocEntry> = emptyList(),
    val highlightTarget: HighlightTarget? = null,
    val noteEditor: NoteEditor? = null,
    val externalLink: String? = null,
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
        private val progressRepository: ProgressRepository,
        private val annotationRepository: AnnotationRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val seriesId: Int = checkNotNull(saved.get<Int>(EPUB_SERIES_ARG)) { "missing $EPUB_SERIES_ARG" }
        private val chapterId: Int = checkNotNull(saved.get<Int>(EPUB_CHAPTER_ARG)) { "missing $EPUB_CHAPTER_ARG" }
        private val session = MutableStateFlow(Session())
        private var loadJob: Job? = null
        private var pushJob: Job? = null

        /** Where the book opened; the navigator echoes it back first, which is not reading. */
        private var opened: Locator? = null

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

        private val highlights =
            auth.activeServer.flatMapLatest { s ->
                if (s == null) flowOf(emptyList()) else annotationRepository.annotations(s, chapterId)
            }

        val state: StateFlow<EpubReaderUiState> =
            combine(session, profile, displayRepository.display, highlights) { st, prefs, device, marks ->
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
                    toc = st.toc,
                    tocOpen = st.tocOpen,
                    highlights = marks,
                    highlightTarget = st.highlightTarget,
                    noteEditor = st.noteEditor,
                    externalLink = st.externalLink,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EpubReaderUiState())

        init {
            load()
        }

        fun retry() = load()

        fun toggleChrome() = session.update { it.copy(chromeVisible = !it.chromeVisible) }

        fun showSettings(open: Boolean) = session.update { it.copy(settingsOpen = open) }

        fun showToc(open: Boolean) = session.update { it.copy(tocOpen = open) }

        /** Where the navigator is now: saved locally at once, sent to Kavita after a pause. */
        fun onLocator(locator: Locator) {
            session.update { it.copy(locator = locator) }
            val st = session.value
            val chapter = st.chapter
            val book = st.publication
            if (chapter == null || book == null || locator == opened) return
            pushJob?.cancel()
            pushJob =
                viewModelScope.launch {
                    val s = auth.activeServer.first() ?: return@launch
                    progressRepository.record(
                        s,
                        chapter,
                        book.kavitaPage(locator, chapter.pages),
                        locator.toJSON().toString(),
                    )
                    delay(PUSH_DEBOUNCE_MS)
                    progressRepository.push(s, chapter.id)
                }
        }

        /** Text was selected in the book. */
        fun onSelection(
            locator: Locator,
            top: Float,
            bottom: Float,
        ) {
            val text =
                locator.text.highlight
                    ?.trim()
                    .orEmpty()
            if (text.isNotEmpty()) {
                session.update { it.copy(highlightTarget = HighlightTarget(text, top, bottom, locator = locator)) }
            }
        }

        /** An existing highlight was tapped. */
        fun onHighlightTapped(
            uuid: String,
            top: Float,
            bottom: Float,
        ) {
            val h = state.value.highlights.firstOrNull { it.uuid == uuid } ?: return
            session.update { it.copy(highlightTarget = HighlightTarget(h.text, top, bottom, uuid, h.color, h.note)) }
        }

        fun dismissHighlight() = session.update { it.copy(highlightTarget = null) }

        /** New selection: highlight in [color]. Existing: recolour, or remove when its own colour is picked again. */
        fun pickColor(color: Int) {
            val target = session.value.highlightTarget ?: return
            session.update { it.copy(highlightTarget = null) }
            withServer { s, chapter ->
                val uuid = target.uuid
                when {
                    uuid == null -> draftOf(target, color)?.let { annotationRepository.create(s, chapter, it) }
                    color == target.color -> annotationRepository.delete(s, uuid)
                    else -> annotationRepository.update(s, chapter, uuid, color, target.note)
                }
            }
        }

        fun editNote() {
            val target = session.value.highlightTarget ?: return
            val uuid = target.uuid
            val editor =
                if (uuid != null) {
                    NoteEditor(target.note.orEmpty(), uuid, target.color ?: 0)
                } else {
                    draftOf(target, 0)?.let { NoteEditor("", draft = it) }
                }
            session.update { it.copy(highlightTarget = null, noteEditor = editor) }
        }

        fun saveNote(text: String) {
            val editor = session.value.noteEditor ?: return
            session.update { it.copy(noteEditor = null) }
            val note = text.trim().takeIf { it.isNotEmpty() }
            withServer { s, chapter ->
                val uuid = editor.uuid
                if (uuid != null) {
                    annotationRepository.update(s, chapter, uuid, editor.color, note)
                } else {
                    editor.draft?.let { annotationRepository.create(s, chapter, it.copy(note = note)) }
                }
            }
        }

        /** Only http(s) links are offered; anything else (intent:, javascript:, file:) is dropped. */
        fun onExternalLink(url: String) {
            webUrl(url)?.let { link -> session.update { it.copy(externalLink = link) } }
        }

        fun closeLink() = session.update { it.copy(externalLink = null) }

        fun closeNote() = session.update { it.copy(noteEditor = null) }

        private fun draftOf(
            target: HighlightTarget,
            color: Int,
        ) = target.locator?.let { session.value.publication?.draft(it, color) }

        private fun withServer(block: suspend (Server, ReaderChapter) -> Unit) {
            viewModelScope.launch {
                val s = auth.activeServer.first()
                val chapter = session.value.chapter
                if (s != null && chapter != null) block(s, chapter)
            }
        }

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
                    val chapter = session.value.chapter
                    // Keep a position from before a reload (retry) rather than jumping back.
                    val start =
                        session.value.locator
                            ?: chapter?.let { publication.startLocator(progressRepository.resume(server, it)) }
                    opened = start
                    viewModelScope.launch { annotationRepository.refresh(server, chapterId) }
                    session.update {
                        it.copy(
                            publication = publication,
                            positions = positions,
                            locator = start,
                            toc = publication.toc(),
                            loading = false,
                        )
                    }
                }

        private companion object {
            const val PUSH_DEBOUNCE_MS = 1_500L
        }

        override fun onCleared() {
            session.value.publication?.close()
        }
    }
