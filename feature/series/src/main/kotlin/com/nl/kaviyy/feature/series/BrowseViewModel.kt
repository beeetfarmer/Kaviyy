package com.nl.kaviyy.feature.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.LibraryQuery
import com.nl.kaviyy.core.data.PersonRepository
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesFilter
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav args of the Browse screen: a [FilterField] name, the value's id and display name. */
const val BROWSE_FIELD_ARG = "field"
const val BROWSE_ID_ARG = "id"
const val BROWSE_NAME_ARG = "name"

/** A person (any role), genre or tag to browse. */
data class BrowseTarget(
    val field: FilterField,
    val id: Int,
    val name: String,
)

data class BrowseUiState(
    val target: BrowseTarget,
    val server: Server? = null,
    /** People only; plain text after sanitizing. */
    val description: String? = null,
)

private const val STOP_TIMEOUT_MS = 5_000L

/** Series across all libraries for one genre, tag or person (a one-rule smart filter). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BrowseViewModel
    @Inject
    constructor(
        session: AuthSession,
        repository: CatalogRepository,
        private val people: PersonRepository,
        saved: SavedStateHandle,
    ) : ViewModel() {
        private val target =
            BrowseTarget(
                FilterField.valueOf(checkNotNull(saved.get<String>(BROWSE_FIELD_ARG)) { "missing $BROWSE_FIELD_ARG" }),
                checkNotNull(saved.get<Int>(BROWSE_ID_ARG)) { "missing $BROWSE_ID_ARG" },
                saved.get<String>(BROWSE_NAME_ARG).orEmpty(),
            )
        private val description = MutableStateFlow<String?>(null)
        private val server = session.activeServer

        val state: StateFlow<BrowseUiState> =
            combine(server, description) { s, d -> BrowseUiState(target, s, d) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BrowseUiState(target))

        val series: Flow<PagingData<SeriesEntity>> =
            server
                .distinctUntilChangedBy { it?.id }
                .flatMapLatest { s -> if (s == null) emptyFlow() else repository.librarySeries(s, query(target)) }
                .cachedIn(viewModelScope)

        init {
            if (target.field.isPerson) {
                viewModelScope.launch {
                    server.filterNotNull().distinctUntilChangedBy { it.id }.collect { s ->
                        description.value = people.description(s, target.name).getOrNull()
                    }
                }
            }
        }
    }

internal val FilterField.isPerson: Boolean get() = this != FilterField.Genres && this != FilterField.Tags

internal fun query(target: BrowseTarget) =
    LibraryQuery(
        libraryId = null,
        filter =
            SeriesFilter(
                rules =
                    listOf(
                        FilterRule(
                            target.field,
                            Comparison.Contains,
                            listOf(RuleValue(target.id.toString(), target.name)),
                        ),
                    ),
                sort = SeriesSort.Title,
            ),
    )
