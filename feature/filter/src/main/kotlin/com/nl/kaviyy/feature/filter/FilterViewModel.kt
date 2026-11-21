package com.nl.kaviyy.feature.filter

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.FieldKind
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterPlacement
import com.nl.kaviyy.core.data.FilterScope
import com.nl.kaviyy.core.data.LibraryFilterStore
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SavedFilter
import com.nl.kaviyy.core.data.SeriesFilter
import com.nl.kaviyy.core.data.SeriesSort
import com.nl.kaviyy.core.data.SmartFilterRepository
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.result.onSuccess
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sheet pages: the filter itself, then pickers pushed on top of it. */
enum class FilterPage { Main, Fields, Rule, Sort, Libraries, Saved }

sealed interface Options {
    data object Loading : Options

    data class Loaded(
        val values: List<RuleValue>,
    ) : Options

    data class Failed(
        val error: AppError,
    ) : Options
}

/** Everything the sheet can ask of its owner; split out so previews/tests can drive [FilterContent]. */
data class FilterActions(
    val onCancel: () -> Unit = {},
    val onApply: () -> Unit = {},
    val onMatchAll: (Boolean) -> Unit = {},
    val onRemoveRule: (Int) -> Unit = {},
    val onEditRule: (Int) -> Unit = {},
    val onAddRule: () -> Unit = {},
    val onShow: (FilterPage) -> Unit = {},
    val onSort: (SeriesSort) -> Unit = {},
    val onToggleLibrary: (Int) -> Unit = {},
    val onPickField: (FilterField) -> Unit = {},
    val onComparison: (com.nl.kaviyy.core.data.Comparison) -> Unit = {},
    val onToggleValue: (RuleValue) -> Unit = {},
    val onCommitRule: () -> Unit = {},
    val onShowOnHome: (Boolean) -> Unit = {},
    val onPinInLibrary: (Boolean) -> Unit = {},
    val onOpenSaved: (SavedFilter) -> Unit = {},
    val onDeleteSaved: (SavedFilter) -> Unit = {},
)

/** The sheet's text inputs: a rule's typed value and the save name. */
data class FilterFields(
    val value: TextFieldState,
    val name: TextFieldState,
)

data class FilterUiState(
    val draft: SeriesFilter = SeriesFilter(),
    val page: FilterPage = FilterPage.Main,
    val rule: RuleDraft? = null,
    val ruleError: DraftError? = null,
    val options: Options = Options.Loading,
    /** The library the grid is showing: the scope when [SeriesFilter.libraryIds] is empty. */
    val currentLibrary: Int? = null,
    /** Which list the filter applies to. */
    val listScope: FilterScope = FilterScope.Library,
    val placement: FilterPlacement = FilterPlacement(),
    /** The user's saved filters (Saved page); null until loaded. */
    val saved: List<SavedFilter>? = null,
    val busy: Boolean = false,
    val error: AppError? = null,
    /** Apply finished (applied, and saved when named): the sheet closes. */
    val done: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FilterViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val store: LibraryFilterStore,
        private val repository: CatalogRepository,
        private val smartFilters: SmartFilterRepository,
    ) : ViewModel() {
        /** Typed value for text/number/date rules. */
        val valueText = TextFieldState()

        /** "Save as smart filter" name; blank = apply without saving. */
        val nameText = TextFieldState()

        private val _state = MutableStateFlow(FilterUiState())
        val state: StateFlow<FilterUiState> = _state.asStateFlow()

        val libraries: StateFlow<List<LibraryEntity>> =
            session.activeServer
                .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else repository.libraries(s) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /** Fresh draft from the list's current filter each time the sheet opens. */
        fun open(
            currentLibrary: Int?,
            listScope: FilterScope = FilterScope.Library,
        ) {
            viewModelScope.launch {
                val s = server() ?: return@launch
                _state.value =
                    FilterUiState(
                        draft = store.current(s.id, listScope),
                        currentLibrary = currentLibrary,
                        listScope = listScope,
                    )
                nameText.clearText()
            }
        }

        fun setShowOnHome(on: Boolean) = _state.update { it.copy(placement = it.placement.copy(onHome = on)) }

        fun setPinInLibrary(on: Boolean) = _state.update { it.copy(placement = it.placement.copy(inLibrary = on)) }

        /** Loads a saved filter into the draft for editing; saving under its name updates it. */
        fun openSaved(saved: SavedFilter) =
            launchBusy { s ->
                smartFilters.load(s, saved).onSuccess { filter ->
                    val placement = smartFilters.placement(s, saved.id).getOrNull() ?: FilterPlacement()
                    nameText.setTextAndPlaceCursorAtEnd(saved.name)
                    _state.update { it.copy(draft = filter, placement = placement, page = FilterPage.Main) }
                }
            }

        fun deleteSaved(saved: SavedFilter) =
            launchBusy { s ->
                smartFilters.delete(s, saved.id).onSuccess {
                    _state.update { st -> st.copy(saved = st.saved?.filter { it.id != saved.id }) }
                }
            }

        fun setMatchAll(all: Boolean) = edit { it.copy(matchAll = all) }

        fun removeRule(index: Int) = edit { it.copy(rules = it.rules.filterIndexed { i, _ -> i != index }) }

        fun setSort(sort: SeriesSort) {
            edit { it.copy(sort = sort) }
            show(FilterPage.Main)
        }

        fun toggleLibrary(id: Int) =
            edit { f ->
                val current = f.libraryIds.ifEmpty { listOfNotNull(_state.value.currentLibrary) }
                val next = if (id in current) current - id else current + id
                f.copy(libraryIds = next.ifEmpty { listOfNotNull(_state.value.currentLibrary) })
            }

        fun show(page: FilterPage) {
            _state.update { it.copy(page = page, error = null) }
            if (page == FilterPage.Saved) {
                launchBusy { s -> smartFilters.list(s).onSuccess { list -> _state.update { it.copy(saved = list) } } }
            }
        }

        fun addRule() = show(FilterPage.Fields)

        fun editRule(index: Int) {
            val rule =
                _state.value.draft.rules
                    .getOrNull(index) ?: return
            startRule(RuleDraft.from(rule, index))
        }

        fun pickField(field: FilterField) = startRule(RuleDraft(field))

        fun setComparison(c: com.nl.kaviyy.core.data.Comparison) =
            _state.update { s -> s.copy(rule = s.rule?.withComparison(c), ruleError = null) }

        fun toggleValue(value: RuleValue) =
            _state.update { s -> s.copy(rule = s.rule?.toggle(value), ruleError = null) }

        /** Saves the rule into the draft, or shows why it can't be saved. */
        fun commitRule() {
            val s = _state.value
            val rule = s.rule ?: return
            rule.build(valueText.text.toString()).fold(
                onSuccess = { built ->
                    val rules = s.draft.rules.toMutableList()
                    if (rule.index != null && rule.index in rules.indices) rules[rule.index] = built else rules += built
                    _state.update { it.copy(draft = it.draft.copy(rules = rules), rule = null, page = FilterPage.Main) }
                },
                onFailure = { e -> _state.update { it.copy(ruleError = (e as? DraftException)?.error) } },
            )
        }

        /** Apply: saves first when named (staying open on failure), then the grid re-queries with the draft. */
        fun apply() =
            launchBusy { s ->
                val st = _state.value
                val name = nameText.text.toString().trim()
                val scope = st.draft.libraryIds.ifEmpty { listOfNotNull(st.currentLibrary) }
                val saved =
                    if (name.isEmpty()) {
                        AppResult.Success(
                            Unit,
                        )
                    } else {
                        smartFilters.save(s, name, st.draft, scope, st.placement)
                    }
                saved.onSuccess {
                    store.update(s.id, st.listScope) { st.draft }
                    _state.update { it.copy(done = true) }
                }
            }

        /** Runs [block] with a busy flag; a failure is shown in the sheet. */
        private fun launchBusy(block: suspend (Server) -> AppResult<*>) {
            viewModelScope.launch {
                val s = server() ?: return@launch
                _state.update { it.copy(busy = true, error = null) }
                val result = block(s)
                _state.update { it.copy(busy = false, error = (result as? AppResult.Failure)?.error) }
            }
        }

        private fun startRule(draft: RuleDraft) {
            valueText.setTextAndPlaceCursorAtEnd(
                if (draft.usesOptions ||
                    draft.field.kind == FieldKind.Boolean
                ) {
                    ""
                } else {
                    draft.selected
                        .firstOrNull()
                        ?.code
                        .orEmpty()
                },
            )
            _state.update { it.copy(rule = draft, ruleError = null, page = FilterPage.Rule, options = Options.Loading) }
            val source = draft.field.source ?: return
            if (source == OptionSource.Formats) return // local list
            viewModelScope.launch {
                val s = server() ?: return@launch
                val scope =
                    _state.value.draft.libraryIds
                        .ifEmpty { listOfNotNull(_state.value.currentLibrary) }
                val options =
                    when (val r = repository.filterOptions(s, source, scope)) {
                        is AppResult.Success -> Options.Loaded(r.value)
                        is AppResult.Failure -> Options.Failed(r.error)
                    }
                _state.update { if (it.rule?.field == draft.field) it.copy(options = options) else it }
            }
        }

        private fun edit(transform: (SeriesFilter) -> SeriesFilter) =
            _state.update { it.copy(draft = transform(it.draft)) }

        private suspend fun server(): Server? = session.activeServer.first()
    }
