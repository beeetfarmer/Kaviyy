package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.HomeItem
import com.nl.kaviyy.core.data.HomeRepository
import com.nl.kaviyy.core.data.OptionSource
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which tag list a picker edits. */
enum class TagKind { Blur, Hide }

data class SensitiveContentUiState(
    val settings: AppSettings = AppSettings(),
    val serverId: Long = 0,
    /** Series matching the hide tags (from the last Home refresh), A–Z. */
    val hidden: List<HomeItem> = emptyList(),
    /** Genre and tag names on the server; null until loaded (or when it couldn't be reached). */
    val tagNames: List<String>? = null,
) {
    fun tags(kind: TagKind): List<String> = if (kind == TagKind.Blur) settings.blurTags else settings.hideTags

    fun isHidden(seriesId: Int): Boolean = !settings.isUnhidden(serverId, seriesId)
}

/** [tags] with [name] switched on or off (matched ignoring case). */
internal fun List<String>.toggled(name: String): List<String> =
    if (any { it.equals(name, ignoreCase = true) }) filterNot { it.equals(name, ignoreCase = true) } else this + name

/** Genres and tags as one list: names deduplicated ignoring case, A–Z. */
internal fun mergeTagNames(vararg lists: List<String>): List<String> =
    lists
        .flatMap { it }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SensitiveContentViewModel
    @Inject
    constructor(
        session: AuthSession,
        home: HomeRepository,
        private val catalog: CatalogRepository,
        private val store: AppSettingsStore,
    ) : ViewModel() {
        private val server = session.activeServer.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        private val tagNames = MutableStateFlow<List<String>?>(null)

        val state: StateFlow<SensitiveContentUiState> =
            combine(
                store.settings,
                server,
                server.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else home.home(s).map { it.tagHidden } },
                tagNames,
            ) { settings, s, hidden, names ->
                SensitiveContentUiState(settings, s?.id?.value ?: 0, hidden, names)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, SensitiveContentUiState())

        /** Fetches the server's genres and tags for the picker (once). */
        fun loadTags() {
            if (tagNames.value != null) return
            viewModelScope.launch {
                val s: Server = server.filterNotNull().first()
                val genres = catalog.filterOptions(s, OptionSource.Genres, emptyList()).getOrNull()
                val tags = catalog.filterOptions(s, OptionSource.Tags, emptyList()).getOrNull()
                if (genres != null || tags != null) {
                    tagNames.value =
                        mergeTagNames(genres.orEmpty().map { it.label }, tags.orEmpty().map { it.label })
                }
            }
        }

        fun toggleTag(
            kind: TagKind,
            name: String,
        ) = update {
            when (kind) {
                TagKind.Blur -> it.copy(blurTags = it.blurTags.toggled(name))
                TagKind.Hide -> it.copy(hideTags = it.hideTags.toggled(name))
            }
        }

        fun setBlurRadius(radius: Int) = update { it.copy(blurRadius = radius) }

        fun setHidden(
            seriesId: Int,
            hidden: Boolean,
        ) = update { it.withUnhidden(state.value.serverId, seriesId, show = !hidden) }

        private fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { store.update(transform) }
        }
    }

@Composable
fun SensitiveContentRoute(
    onBack: () -> Unit,
    viewModel: SensitiveContentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SensitiveContentScreen(
        state,
        onBack = onBack,
        onOpenPicker = { viewModel.loadTags() },
        onToggleTag = viewModel::toggleTag,
        onBlurRadius = viewModel::setBlurRadius,
        onHidden = viewModel::setHidden,
    )
}

/** [derived] Sensitive content: blur or hide Home covers by genre / tag, blur strength, and the hidden series. */
@Composable
fun SensitiveContentScreen(
    state: SensitiveContentUiState,
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
    onToggleTag: (TagKind, String) -> Unit,
    onBlurRadius: (Int) -> Unit,
    onHidden: (seriesId: Int, hidden: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by rememberSaveable { mutableStateOf<TagKind?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(Kavita.colors.background)
            .statusBarsPadding()
            // ponytail: every hidden series composes at once; a LazyColumn if lists reach the thousands.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KavitaSpacing.screenHorizontal)
            .padding(bottom = KavitaSpacing.contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
    ) {
        CircleIconButton(
            KavitaIcons.ChevronLeft,
            stringResource(R.string.you_back),
            onBack,
            Modifier.padding(top = 8.dp),
        )
        LargeTitleHeader(stringResource(R.string.sensitive_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupedList {
                TagsRow(R.string.sensitive_blur_tags, state.settings.blurTags, divider = true) {
                    picker = TagKind.Blur
                    onOpenPicker()
                }
                val label = stringResource(R.string.sensitive_blur_strength)
                GroupedRow(label, value = "${state.settings.blurRadius}")
                Slider(
                    value = state.settings.blurRadius.toFloat(),
                    onValueChange = { onBlurRadius(it.toInt()) },
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .semantics { contentDescription = label },
                    valueRange = 1f..AppSettings.MAX_BLUR_RADIUS.toFloat(),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Kavita.colors.accent,
                            activeTrackColor = Kavita.colors.accent,
                            inactiveTrackColor = Kavita.colors.separator,
                        ),
                )
            }
            Note(R.string.sensitive_blur_note)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupedList {
                TagsRow(R.string.sensitive_hide_tags, state.settings.hideTags) {
                    picker = TagKind.Hide
                    onOpenPicker()
                }
            }
            Note(R.string.sensitive_hide_note)
        }
        if (state.settings.hideTags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.sensitive_hidden_series),
                    Modifier.padding(horizontal = 16.dp),
                    style = Kavita.type.headline,
                    color = Kavita.colors.label,
                )
                if (state.hidden.isEmpty()) {
                    Note(R.string.sensitive_hidden_empty)
                } else {
                    GroupedList {
                        state.hidden.forEachIndexed { i, item ->
                            GroupedSwitchRow(
                                item.title,
                                state.isHidden(item.seriesId),
                                { onHidden(item.seriesId, it) },
                                divider = i < state.hidden.lastIndex,
                            )
                        }
                    }
                    Note(R.string.sensitive_hidden_note)
                }
            }
        }
    }
    picker?.let { kind ->
        TagPickerSheet(
            title =
                stringResource(
                    if (kind ==
                        TagKind.Blur
                    ) {
                        R.string.sensitive_blur_tags
                    } else {
                        R.string.sensitive_hide_tags
                    },
                ),
            names = state.tagNames,
            selected = state.tags(kind),
            onToggle = { onToggleTag(kind, it) },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun TagsRow(
    @StringRes label: Int,
    tags: List<String>,
    divider: Boolean = false,
    onClick: () -> Unit,
) = GroupedRow(
    stringResource(label),
    value = tags.joinToString(", ").ifEmpty { stringResource(R.string.sensitive_none) },
    onClick = onClick,
    divider = divider,
)

/** Searchable checklist of the server's genres and tags; chosen names missing from the server stay listed. */
@Composable
private fun TagPickerSheet(
    title: String,
    names: List<String>?,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    KavitaBottomSheet(onDismiss) {
        SheetHeader(title, stringResource(R.string.sensitive_done), onDismiss)
        val query = rememberTextFieldState()
        GroupedList(Modifier.padding(horizontal = KavitaSpacing.screenHorizontal, vertical = 8.dp)) {
            GroupedInputRow(
                stringResource(R.string.sensitive_search),
                query,
                placeholder = stringResource(R.string.sensitive_search),
            )
        }
        val all = mergeTagNames(selected, names.orEmpty())
        val q = query.text.toString().trim()
        val shown = if (q.isEmpty()) all else all.filter { it.contains(q, ignoreCase = true) }
        if (names == null) {
            Text(
                stringResource(R.string.sensitive_loading),
                Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(bottom = 24.dp),
        ) {
            items(shown, key = { it.lowercase() }) { name ->
                val checked = selected.any { it.equals(name, ignoreCase = true) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(KavitaSpacing.rowHeight)
                        .clickable(role = Role.Checkbox) { onToggle(name) }
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(name, style = Kavita.type.body, color = Kavita.colors.label)
                    if (checked) {
                        Icon(
                            KavitaIcons.Check,
                            stringResource(R.string.sensitive_selected),
                            Modifier.size(18.dp),
                            tint = Kavita.colors.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(
    stringResource(text),
    Modifier.padding(horizontal = 16.dp),
    style = Kavita.type.footnote,
    color = Kavita.colors.labelSecondary,
)
