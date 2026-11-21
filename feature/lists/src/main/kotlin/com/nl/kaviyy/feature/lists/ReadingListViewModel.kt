package com.nl.kaviyy.feature.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.database.ReadingListItemEntity
import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val READING_LIST_ID_ARG = "readingListId"
private const val STOP_TIMEOUT_MS = 5_000L

data class ReadingListUiState(
    val server: Server? = null,
    val list: ReadingListRow? = null,
    val items: List<ReadingListItemEntity> = emptyList(),
    /** The last refresh failed (the cached items are still shown). */
    val error: AppError? = null,
)

/** [derived] One reading list: its items in order with progress; Continue opens the first unfinished one. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingListViewModel
    @Inject
    constructor(
        session: AuthSession,
        private val lists: ReadingListRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val id: Int = checkNotNull(saved.get<Int>(READING_LIST_ID_ARG)) { "missing $READING_LIST_ID_ARG" }
        private val server = session.activeServer.distinctUntilChangedBy { it?.id }
        private val error = MutableStateFlow<AppError?>(null)

        val state: StateFlow<ReadingListUiState> =
            server
                .flatMapLatest { s ->
                    if (s == null) {
                        flowOf(ReadingListUiState())
                    } else {
                        combine(
                            lists.list(s, id),
                            lists.items(s, id),
                            error,
                        ) { l, i, e -> ReadingListUiState(s, l, i, e) }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReadingListUiState())

        init {
            viewModelScope.launch {
                server.filterNotNull().collect { s ->
                    error.value =
                        (lists.refreshItems(s, id) as? AppResult.Failure)?.error
                }
            }
        }
    }
