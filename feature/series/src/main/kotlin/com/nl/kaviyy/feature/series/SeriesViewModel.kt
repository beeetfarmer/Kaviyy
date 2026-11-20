package com.nl.kaviyy.feature.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.SeriesDetail
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
) {
    /** The server's continue point, when it's among the cached chapters. */
    val continueChapter: ChapterEntity?
        get() = detail?.metadata?.continueChapterId?.let { id -> detail.chapters.firstOrNull { it.id == id } }
}

private data class Status(
    val refreshing: Boolean = true,
    val error: AppError? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val repository: CatalogRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        val seriesId: Int = checkNotNull(saved.get<Int>(SERIES_ID_ARG)) { "missing $SERIES_ID_ARG" }
        private val status = MutableStateFlow(Status())

        val state: StateFlow<SeriesUiState> =
            session.activeServer
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(SeriesUiState(refreshing = false))
                    } else {
                        combine(repository.seriesDetail(s, seriesId), repository.libraries(s), status) { d, libs, st ->
                            val name = libs.firstOrNull { it.id == d.series?.libraryId }?.name
                            SeriesUiState(s, d, name, st.refreshing, st.error)
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SeriesUiState())

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                status.update { it.copy(refreshing = true) }
                val result = repository.refreshSeries(s, seriesId)
                status.value = Status(refreshing = false, error = (result as? AppResult.Failure)?.error)
            }
        }
    }

private const val STOP_TIMEOUT_MS = 5_000L
