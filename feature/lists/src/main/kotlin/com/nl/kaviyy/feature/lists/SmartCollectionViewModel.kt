package com.nl.kaviyy.feature.lists

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SmartCollections
import com.nl.kaviyy.core.data.SmartRule
import com.nl.kaviyy.core.model.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How many series the rule matches: [Checking] while the server counts. */
sealed interface MatchCount {
    data object None : MatchCount

    data object Checking : MatchCount

    data class Count(
        val n: Int,
    ) : MatchCount

    data object Failed : MatchCount
}

data class SmartCollectionUiState(
    val open: Boolean = false,
    val matchAll: Boolean = false,
    val tags: List<RuleValue> = emptyList(),
    val genres: List<RuleValue> = emptyList(),
    val selectedTags: List<RuleValue> = emptyList(),
    val selectedGenres: List<RuleValue> = emptyList(),
    val query: String = "",
    val loadingOptions: Boolean = false,
    val count: MatchCount = MatchCount.None,
    val busy: Boolean = false,
    val failed: Boolean = false,
    /** The new collection's id, to open once. */
    val created: Int? = null,
) {
    val rule: SmartRule get() = SmartRule(matchAll, selectedTags, selectedGenres)

    /** Up to [SHOWN_OPTIONS] unselected options matching [query]. */
    fun visible(
        options: List<RuleValue>,
        selected: List<RuleValue>,
    ): List<RuleValue> =
        options
            .filter { it !in selected && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }
            .take(SHOWN_OPTIONS)

    val canCreate: Boolean get() = !busy && (count as? MatchCount.Count)?.n?.let { it > 0 } == true
}

data class SmartCollectionActions(
    val onClose: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onMatchAll: (Boolean) -> Unit = {},
    val onToggleTag: (RuleValue) -> Unit = {},
    val onToggleGenre: (RuleValue) -> Unit = {},
)

private const val SHOWN_OPTIONS = 30
private const val COUNT_DEBOUNCE_MS = 400L

/** [derived] New smart collection: pick tags/genres, see how many series match, create (synced to Kavita). */
@HiltViewModel
class SmartCollectionViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val catalog: CatalogRepository,
        private val smart: SmartCollections,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(SmartCollectionUiState())
        val state: StateFlow<SmartCollectionUiState> = mutable.asStateFlow()
        val name = TextFieldState()
        val search = TextFieldState()
        private var counting: Job? = null

        init {
            viewModelScope.launch {
                snapshotFlow { search.text.toString() }.collect { q -> mutable.update { it.copy(query = q) } }
            }
        }

        fun open() {
            name.clearText()
            search.clearText()
            mutable.value = SmartCollectionUiState(open = true, loadingOptions = true)
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                val tags = (catalog.filterOptions(s, OptionSource.Tags, emptyList()) as? AppResult.Success)?.value
                val genres = (catalog.filterOptions(s, OptionSource.Genres, emptyList()) as? AppResult.Success)?.value
                mutable.update {
                    it.copy(
                        tags = tags.orEmpty().sortedBy { t -> t.label.lowercase() },
                        genres = genres.orEmpty().sortedBy { g -> g.label.lowercase() },
                        loadingOptions = false,
                        failed = tags == null && genres == null,
                    )
                }
            }
        }

        fun close() = mutable.update { SmartCollectionUiState() }

        fun toggleTag(value: RuleValue) = change { it.copy(selectedTags = it.selectedTags.toggle(value)) }

        fun toggleGenre(value: RuleValue) = change { it.copy(selectedGenres = it.selectedGenres.toggle(value)) }

        fun setMatchAll(all: Boolean) = change { it.copy(matchAll = all) }

        fun create() {
            val st = mutable.value
            val title = name.text.toString().trim()
            if (!st.canCreate || title.isEmpty()) return
            mutable.update { it.copy(busy = true, failed = false) }
            viewModelScope.launch {
                val s = session.activeServer.first() ?: return@launch
                when (val r = smart.create(s, title, st.rule)) {
                    is AppResult.Success -> mutable.update { SmartCollectionUiState(created = r.value) }
                    is AppResult.Failure -> mutable.update { it.copy(busy = false, failed = true) }
                }
            }
        }

        fun consumeCreated() = mutable.update { it.copy(created = null) }

        /** Applies [block], then recounts (debounced, so tapping several chips sends one request). */
        private fun change(block: (SmartCollectionUiState) -> SmartCollectionUiState) {
            mutable.update(block)
            counting?.cancel()
            val rule = mutable.value.rule
            if (rule.isEmpty) {
                mutable.update { it.copy(count = MatchCount.None) }
                return
            }
            mutable.update { it.copy(count = MatchCount.Checking) }
            counting =
                viewModelScope.launch {
                    delay(COUNT_DEBOUNCE_MS)
                    val s = session.activeServer.first() ?: return@launch
                    val count =
                        when (val r = smart.count(s, rule)) {
                            is AppResult.Success -> MatchCount.Count(r.value)
                            is AppResult.Failure -> MatchCount.Failed
                        }
                    mutable.update { if (it.rule == rule) it.copy(count = count) else it }
                }
        }
    }

private fun List<RuleValue>.toggle(value: RuleValue) = if (value in this) this - value else this + value
