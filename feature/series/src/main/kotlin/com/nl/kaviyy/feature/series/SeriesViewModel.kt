package com.nl.kaviyy.feature.series

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AdminRepository
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.data.RelatedSeries
import com.nl.kaviyy.core.data.ReviewRepository
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesReviews
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Navigation argument name (the app's type-safe route stores it under this key). */
const val SERIES_ID_ARG = "seriesId"

data class SeriesUiState(
    val server: Server? = null,
    val detail: SeriesDetail? = null,
    val libraryName: String? = null,
    val refreshing: Boolean = true,
    /** Last refresh failed; cached data (if any) is still shown. */
    val error: AppError? = null,
    val tab: SeriesTab = SeriesTab.Volumes,
    val sheet: SeriesSheet? = null,
    val related: RelatedState = RelatedState.Idle,
    /** A change was saved locally but not pushed yet (offline); it syncs later. */
    val syncPending: Boolean = false,
    val reviews: ReviewsState = ReviewsState.Loading,
    /** Saving the review failed (reviews are online only). */
    val reviewError: AppError? = null,
    val adminNotice: AdminNotice? = null,
) {
    /** The server's continue point, when it's among the cached chapters. */
    val continueChapter: ChapterEntity?
        get() = detail?.metadata?.continueChapterId?.let { id -> detail.chapters.firstOrNull { it.id == id } }
}

/** Opens a detail sheet for a row. */
sealed interface SeriesSheet {
    data class Volume(
        val id: Int,
    ) : SeriesSheet

    data class Chapter(
        val id: Int,
    ) : SeriesSheet

    /** The top bar's "more" menu. */
    data object Actions : SeriesSheet

    /** Your rating and review, community ratings, others' reviews, links. */
    data object Reviews : SeriesSheet

    /** Admin: edit the summary. */
    data object EditSummary : SeriesSheet
}

/** Result line for an admin action. */
enum class AdminNotice { Started, Saved, Failed }

sealed interface ReviewsState {
    data object Loading : ReviewsState

    data class Loaded(
        val value: SeriesReviews,
    ) : ReviewsState

    data class Failed(
        val error: AppError,
    ) : ReviewsState
}

/** Related tab: fetched on first open (network only). */
sealed interface RelatedState {
    data object Idle : RelatedState

    data object Loading : RelatedState

    data class Loaded(
        val items: List<RelatedSeries>,
    ) : RelatedState

    data class Failed(
        val error: AppError,
    ) : RelatedState
}

private data class Status(
    val refreshing: Boolean = true,
    val error: AppError? = null,
    val tab: SeriesTab = SeriesTab.Volumes,
    val sheet: SeriesSheet? = null,
    val syncPending: Boolean = false,
    val reviews: ReviewsState = ReviewsState.Loading,
    val reviewError: AppError? = null,
    val adminNotice: AdminNotice? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val repository: CatalogRepository,
        private val readState: ReadStateRepository,
        private val reviewRepository: ReviewRepository,
        private val admin: AdminRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        /** Admin summary draft (EditSummary sheet). */
        val summaryText = TextFieldState()

        /** The user's review draft (Reviews sheet). */
        val reviewText = TextFieldState()
        private var libraryType = 0
        val seriesId: Int = checkNotNull(saved.get<Int>(SERIES_ID_ARG)) { "missing $SERIES_ID_ARG" }
        private val status = MutableStateFlow(Status())
        private val relatedState = MutableStateFlow<RelatedState>(RelatedState.Idle)

        val state: StateFlow<SeriesUiState> =
            session.activeServer
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(SeriesUiState(refreshing = false))
                    } else {
                        combine(
                            repository.seriesDetail(s, seriesId),
                            repository.libraries(s),
                            status,
                            relatedState,
                        ) { d, libs, st, rel ->
                            val library = libs.firstOrNull { it.id == d.series?.libraryId }
                            libraryType = library?.type ?: 0
                            SeriesUiState(
                                s,
                                d,
                                library?.name,
                                st.refreshing,
                                st.error,
                                st.tab,
                                st.sheet,
                                rel,
                                st.syncPending,
                                st.reviews,
                                st.reviewError,
                                st.adminNotice,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SeriesUiState())

        init {
            refresh()
        }

        fun selectTab(tab: SeriesTab) {
            status.update { it.copy(tab = tab) }
            if (tab == SeriesTab.Related) loadRelated()
        }

        fun showSheet(sheet: SeriesSheet?) {
            status.update { it.copy(sheet = sheet, reviewError = null) }
            if (sheet == SeriesSheet.Reviews) loadReviews()
        }

        fun setRating(stars: Float) = push { s -> readState.setRating(s, seriesId, stars) }

        /** Blank deletes; the list reloads afterwards. */
        fun saveReview() {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                val result = reviewRepository.saveReview(s, seriesId, reviewText.text.toString())
                status.update { it.copy(reviewError = (result as? AppResult.Failure)?.error) }
                if (result is AppResult.Success) loadReviews()
            }
        }

        private fun loadReviews() {
            status.update { it.copy(reviews = ReviewsState.Loading) }
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                val loaded =
                    when (val r = reviewRepository.reviews(s, seriesId, libraryType)) {
                        is AppResult.Success -> ReviewsState.Loaded(r.value)
                        is AppResult.Failure -> ReviewsState.Failed(r.error)
                    }
                (loaded as? ReviewsState.Loaded)?.value?.reviews?.firstOrNull { it.mine }?.let { mine ->
                    if (reviewText.text.isEmpty()) reviewText.setTextAndPlaceCursorAtEnd(mine.body)
                }
                status.update { it.copy(reviews = loaded) }
            }
        }

        fun toggleWantToRead() {
            val on =
                state.value.detail
                    ?.metadata
                    ?.wantToRead != true
            push { s -> readState.setWantToRead(s, seriesId, on) }
        }

        /** Closes the sheet; on a successful push, refreshes so the continue point follows. */
        fun setRead(
            target: ReadTarget,
            read: Boolean,
        ) {
            showSheet(null)
            push { s -> readState.setRead(s, target, read).also { if (it is AppResult.Success) refresh() } }
        }

        private fun push(block: suspend (Server) -> AppResult<Unit>) {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                val result = block(s)
                status.update { it.copy(syncPending = result is AppResult.Failure) }
            }
        }

        /** No-op while loading or once loaded; retries after a failure. */
        fun loadRelated() {
            val current = relatedState.value
            if (current is RelatedState.Loading || current is RelatedState.Loaded) return
            relatedState.value = RelatedState.Loading
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                relatedState.value =
                    when (val r = repository.relatedSeries(s, seriesId)) {
                        is AppResult.Success -> RelatedState.Loaded(r.value)
                        is AppResult.Failure -> RelatedState.Failed(r.error)
                    }
            }
        }

        fun refreshMetadata() =
            adminJob(AdminNotice.Started) { s, libraryId -> admin.refreshMetadata(s, libraryId, seriesId) }

        fun scan() = adminJob(AdminNotice.Started) { s, libraryId -> admin.scan(s, libraryId, seriesId) }

        fun editSummary() {
            summaryText.setTextAndPlaceCursorAtEnd(
                plainSummary(
                    state.value.detail
                        ?.metadata
                        ?.summary,
                ),
            )
            showSheet(SeriesSheet.EditSummary)
        }

        fun saveSummary() =
            adminJob(AdminNotice.Saved) { s, _ ->
                admin
                    .updateSummary(
                        s,
                        seriesId,
                        summaryText.text.toString(),
                    ).also { if (it is AppResult.Success) refresh() }
            }

        /** Closes the sheet, runs [block] and reports its outcome under the actions. */
        private fun adminJob(
            success: AdminNotice,
            block: suspend (Server, libraryId: Int) -> AppResult<Unit>,
        ) {
            val libraryId =
                state.value.detail
                    ?.series
                    ?.libraryId ?: return
            status.update { it.copy(sheet = null, adminNotice = null) }
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                val notice = if (block(s, libraryId) is AppResult.Success) success else AdminNotice.Failed
                status.update { it.copy(adminNotice = notice) }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                status.update { it.copy(refreshing = true) }
                val result = repository.refreshSeries(s, seriesId)
                status.update { it.copy(refreshing = false, error = (result as? AppResult.Failure)?.error) }
            }
        }
    }

private const val STOP_TIMEOUT_MS = 5_000L
