package com.nl.kaviyy.feature.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.SearchRepository
import com.nl.kaviyy.core.data.SearchResults
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.map
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Canvas scope chips. Tags = genres + tags; Lists = collections + reading lists. */
enum class SearchScope { All, Series, Chapters, People, Tags, Lists }

sealed interface Results {
    /** Empty query: recents are shown. */
    data object Idle : Results

    data object Loading : Results

    data class Loaded(
        val query: String,
        val value: SearchResults,
    ) : Results

    data class Failed(
        val error: AppError,
    ) : Results
}

data class SearchUiState(
    val server: Server? = null,
    val scope: SearchScope = SearchScope.All,
    val recents: List<String> = emptyList(),
    val results: Results = Results.Idle,
    /** Opening a chapter/file/person failed (needs a lookup first). */
    val openFailed: Boolean = false,
)

/** Where a tapped result leads; the app maps it to a route. */
sealed interface SearchDestination {
    data class Series(
        val id: Int,
    ) : SearchDestination

    data class Browse(
        val field: FilterField,
        val id: Int,
        val name: String,
    ) : SearchDestination
}

/** What was tapped; some hits need a lookup before they can open. */
sealed interface SearchHit {
    data class Series(
        val id: Int,
    ) : SearchHit

    data class Chapter(
        val id: Int,
    ) : SearchHit

    data class File(
        val id: Int,
    ) : SearchHit

    data class Person(
        val id: Int,
        val name: String,
    ) : SearchHit

    data class Genre(
        val id: Int,
        val name: String,
    ) : SearchHit

    data class Tag(
        val id: Int,
        val name: String,
    ) : SearchHit
}

/** Screen callbacks. */
data class SearchActions(
    val onScope: (SearchScope) -> Unit = {},
    val onOpen: (SearchHit) -> Unit = {},
    val onRecent: (String) -> Unit = {},
    val onClearRecents: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onHome: () -> Unit = {},
)

private const val DEBOUNCE_MS = 300L
private const val STOP_TIMEOUT_MS = 5_000L

private data class Local(
    val scope: SearchScope = SearchScope.All,
    val openFailed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val repository: SearchRepository,
    ) : ViewModel() {
        val query = TextFieldState()
        private val local = MutableStateFlow(Local())
        private val opens = Channel<SearchDestination>(Channel.BUFFERED)

        /** One-shot navigation events. */
        val destinations: Flow<SearchDestination> = opens.receiveAsFlow()

        private val results: Flow<Results> =
            combine(
                session.activeServer,
                snapshotFlow {
                    query.text.toString().trim()
                }.debounce { if (it.isEmpty()) 0L else DEBOUNCE_MS }.distinctUntilChanged(),
            ) { s, q ->
                s to q
            }.flatMapLatest { (s, q) ->
                if (s == null || q.isEmpty()) {
                    flowOf<Results>(Results.Idle)
                } else {
                    flow<Results> {
                        emit(Results.Loading)
                        emit(
                            when (val r = repository.search(s, q)) {
                                is AppResult.Success -> Results.Loaded(q, r.value)
                                is AppResult.Failure -> Results.Failed(r.error)
                            },
                        )
                    }
                }
            }

        private val recents: Flow<List<String>> =
            session.activeServer.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repository.recents(s) }

        val state: StateFlow<SearchUiState> =
            combine(session.activeServer, local, recents, results) { s, l, rec, res ->
                SearchUiState(s, l.scope, rec, res, l.openFailed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState())

        fun setScope(scope: SearchScope) = local.update { it.copy(scope = scope) }

        fun useRecent(text: String) = query.setTextAndPlaceCursorAtEnd(text)

        fun clear() = query.clearText()

        fun clearRecents() = withServer { repository.clearRecents(it) }

        /** IME search: remember the query. */
        fun submit() = withServer { repository.addRecent(it, query.text.toString()) }

        fun open(hit: SearchHit) =
            withServer { s ->
                repository.addRecent(s, query.text.toString())
                val destination: AppResult<SearchDestination> =
                    when (hit) {
                        is SearchHit.Series -> {
                            AppResult.Success(SearchDestination.Series(hit.id))
                        }

                        is SearchHit.Genre -> {
                            AppResult.Success(SearchDestination.Browse(FilterField.Genres, hit.id, hit.name))
                        }

                        is SearchHit.Tag -> {
                            AppResult.Success(SearchDestination.Browse(FilterField.Tags, hit.id, hit.name))
                        }

                        is SearchHit.Chapter -> {
                            repository.seriesForChapter(s, hit.id).map { SearchDestination.Series(it) }
                        }

                        is SearchHit.File -> {
                            repository.seriesForFile(s, hit.id).map { SearchDestination.Series(it) }
                        }

                        is SearchHit.Person -> {
                            repository.personField(s, hit.id).map { SearchDestination.Browse(it, hit.id, hit.name) }
                        }
                    }
                local.update { it.copy(openFailed = destination is AppResult.Failure) }
                if (destination is AppResult.Success) opens.send(destination.value)
            }

        private fun withServer(block: suspend (Server) -> Unit) {
            viewModelScope.launch { session.activeServer.first()?.let { block(it) } }
        }
    }
