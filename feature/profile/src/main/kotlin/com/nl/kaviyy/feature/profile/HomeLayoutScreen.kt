package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.HomeSection
import com.nl.kaviyy.core.data.HomeSectionPref
import com.nl.kaviyy.core.data.ListSort
import com.nl.kaviyy.core.data.encodeHomeLayout
import com.nl.kaviyy.core.data.move
import com.nl.kaviyy.core.data.parseHomeLayout
import com.nl.kaviyy.core.data.toggle
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.KavitaSwitch
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.rowDivider
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The rails with a choosable order. */
enum class ShelfKind { Collections, ReadingLists, Libraries }

data class HomeLayoutUiState(
    val layout: List<HomeSectionPref> = parseHomeLayout(""),
    val sorts: Map<ShelfKind, ListSort> = emptyMap(),
) {
    fun sort(kind: ShelfKind): ListSort =
        sorts[kind] ?: if (kind == ShelfKind.Libraries) ListSort.NameAsc else ListSort.Recent
}

data class HomeLayoutActions(
    val onBack: () -> Unit = {},
    val onToggle: (HomeSection, Boolean) -> Unit = { _, _ -> },
    val onMove: (HomeSection, Int) -> Unit = { _, _ -> },
    val onSort: (ShelfKind, ListSort) -> Unit = { _, _ -> },
)

private const val STOP_TIMEOUT_MS = 5_000L

@HiltViewModel
class HomeLayoutViewModel
    @Inject
    constructor(
        private val settings: AppSettingsStore,
    ) : ViewModel() {
        val state: StateFlow<HomeLayoutUiState> =
            settings.settings
                .map { a ->
                    HomeLayoutUiState(
                        parseHomeLayout(a.homeLayout),
                        mapOf(
                            ShelfKind.Collections to ListSort.of(a.collectionSort),
                            ShelfKind.ReadingLists to ListSort.of(a.readingListSort),
                            ShelfKind.Libraries to ListSort.of(a.librarySort, ListSort.NameAsc),
                        ),
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeLayoutUiState())

        fun toggle(
            section: HomeSection,
            visible: Boolean,
        ) = edit { it.toggle(section, visible) }

        fun move(
            section: HomeSection,
            by: Int,
        ) = edit { it.move(section, by) }

        fun sort(
            kind: ShelfKind,
            sort: ListSort,
        ) = viewModelScope.launch {
            settings.update {
                when (kind) {
                    ShelfKind.Collections -> it.copy(collectionSort = sort.name)
                    ShelfKind.ReadingLists -> it.copy(readingListSort = sort.name)
                    ShelfKind.Libraries -> it.copy(librarySort = sort.name)
                }
            }
        }

        private fun edit(change: (List<HomeSectionPref>) -> List<HomeSectionPref>) =
            viewModelScope.launch {
                settings.update { it.copy(homeLayout = encodeHomeLayout(change(parseHomeLayout(it.homeLayout)))) }
            }
    }

@Composable
fun HomeLayoutRoute(
    onBack: () -> Unit,
    viewModel: HomeLayoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeLayoutScreen(state, HomeLayoutActions(onBack, viewModel::toggle, viewModel::move, viewModel::sort))
}

/** [derived] You → Home Screen: show/hide and reorder Home sections, and how the shelf rails are sorted. */
@Composable
fun HomeLayoutScreen(
    state: HomeLayoutUiState,
    actions: HomeLayoutActions,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .fillMaxSize()
        .background(Kavita.colors.background)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = KavitaSpacing.screenHorizontal)
        .padding(bottom = KavitaSpacing.contentBottomPadding),
    verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
) {
    CircleIconButton(
        KavitaIcons.ChevronLeft,
        stringResource(R.string.you_back),
        actions.onBack,
        Modifier.padding(top = 8.dp),
    )
    LargeTitleHeader(stringResource(R.string.home_layout_title))
    Column {
        SectionHeader(stringResource(R.string.home_layout_sections))
        GroupedList {
            state.layout.forEachIndexed { i, pref ->
                SectionRow(pref, first = i == 0, last = i == state.layout.lastIndex, actions)
            }
        }
        LayoutFootnote(stringResource(R.string.home_layout_footnote))
    }
    ShelfKind.entries.forEach { kind -> SortGroup(kind, state.sort(kind), actions) }
}

@Composable
private fun SectionRow(
    pref: HomeSectionPref,
    first: Boolean,
    last: Boolean,
    actions: HomeLayoutActions,
) {
    val label = stringResource(homeSectionName(pref.section))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KavitaSpacing.rowHeight)
            .rowDivider(!last, 16.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = Kavita.type.body,
            color = if (pref.visible) Kavita.colors.label else Kavita.colors.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        MoveButton(KavitaIcons.ChevronUp, stringResource(R.string.home_layout_up, label), !first) {
            actions.onMove(pref.section, -1)
        }
        MoveButton(KavitaIcons.ChevronDown, stringResource(R.string.home_layout_down, label), !last) {
            actions.onMove(pref.section, 1)
        }
        KavitaSwitch(pref.visible, { actions.onToggle(pref.section, it) }, contentDescription = label)
    }
}

@Composable
private fun MoveButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) = IconButton(onClick, enabled = enabled) {
    Icon(
        icon,
        description,
        Modifier.size(18.dp),
        tint = if (enabled) Kavita.colors.accent else Kavita.colors.labelTertiary,
    )
}

@Composable
private fun SortGroup(
    kind: ShelfKind,
    sort: ListSort,
    actions: HomeLayoutActions,
) {
    // Libraries carry no size, so they don't offer "Largest".
    val options = if (kind == ShelfKind.Libraries) ListSort.entries - ListSort.Largest else ListSort.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.home_layout_sort, stringResource(shelfName(kind))))
        SegmentedControl(
            options.map { stringResource(sortName(it, kind)) },
            options.indexOf(sort).coerceAtLeast(0),
            { actions.onSort(kind, options[it]) },
        )
    }
}

@Composable
private fun LayoutFootnote(text: String) =
    Text(
        text,
        style = Kavita.type.footnote,
        color = Kavita.colors.labelSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )

@StringRes
internal fun homeSectionName(section: HomeSection) =
    when (section) {
        HomeSection.ContinueReading -> R.string.home_section_continue
        HomeSection.OnDeck -> R.string.home_section_on_deck
        HomeSection.RecentlyUpdated -> R.string.home_section_updated
        HomeSection.RecentlyAdded -> R.string.home_section_added
        HomeSection.SmartFilters -> R.string.home_section_smart_filters
        HomeSection.WantToRead -> R.string.home_section_want_to_read
        HomeSection.Collections -> R.string.home_section_collections
        HomeSection.ReadingLists -> R.string.home_section_reading_lists
        HomeSection.Libraries -> R.string.home_section_libraries
        HomeSection.Downloaded -> R.string.home_section_downloaded
    }

@StringRes
private fun shelfName(kind: ShelfKind) =
    when (kind) {
        ShelfKind.Collections -> R.string.home_section_collections
        ShelfKind.ReadingLists -> R.string.home_section_reading_lists
        ShelfKind.Libraries -> R.string.home_section_libraries
    }

@StringRes
private fun sortName(
    sort: ListSort,
    kind: ShelfKind,
) = when (sort) {
    ListSort.Recent -> if (kind == ShelfKind.Libraries) R.string.home_sort_scanned else R.string.home_sort_recent
    ListSort.NameAsc -> R.string.home_sort_az
    ListSort.NameDesc -> R.string.home_sort_za
    ListSort.Largest -> R.string.home_sort_largest
}
