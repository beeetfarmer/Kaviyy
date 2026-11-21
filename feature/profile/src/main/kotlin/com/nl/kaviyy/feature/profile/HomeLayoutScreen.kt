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
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.HomeSection
import com.nl.kaviyy.core.data.HomeSectionPref
import com.nl.kaviyy.core.data.ListsRepository
import com.nl.kaviyy.core.data.SectionOrder
import com.nl.kaviyy.core.data.encodeHomeLayout
import com.nl.kaviyy.core.data.encodeHomeOrders
import com.nl.kaviyy.core.data.homeOrders
import com.nl.kaviyy.core.data.move
import com.nl.kaviyy.core.data.orderKey
import com.nl.kaviyy.core.data.parseHomeLayout
import com.nl.kaviyy.core.data.toggle
import com.nl.kaviyy.core.data.withItems
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.KavitaSwitch
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.rowDivider
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeLayoutUiState(
    /** Every section, with a row for each collection and library (new ones hidden at the end). */
    val layout: List<HomeSectionPref> = parseHomeLayout(""),
    /** Chosen order per section, by [com.nl.kaviyy.core.data.orderKey]. */
    val orders: Map<String, SectionOrder> = emptyMap(),
    /** Names of the collection / library rows. */
    val collectionNames: Map<Int, String> = emptyMap(),
    val libraryNames: Map<Int, String> = emptyMap(),
)

data class HomeLayoutActions(
    val onBack: () -> Unit = {},
    val onToggle: (HomeSectionPref, Boolean) -> Unit = { _, _ -> },
    val onMove: (HomeSectionPref, Int) -> Unit = { _, _ -> },
    val onOrder: (HomeSectionPref, SectionOrder) -> Unit = { _, _ -> },
)

private const val STOP_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeLayoutViewModel
    @Inject
    constructor(
        private val settings: AppSettingsStore,
        session: AuthSession,
        lists: ListsRepository,
        catalog: CatalogRepository,
    ) : ViewModel() {
        private val names: Flow<Pair<Map<Int, String>, Map<Int, String>>> =
            session.activeServer.flatMapLatest { s ->
                if (s == null) {
                    flowOf(emptyMap<Int, String>() to emptyMap())
                } else {
                    combine(lists.collections(s), catalog.libraries(s)) { c, l ->
                        c.associate { it.id to it.title } to l.associate { it.id to it.name }
                    }
                }
            }

        val state: StateFlow<HomeLayoutUiState> =
            combine(settings.settings, names) { a, (collections, libraries) ->
                HomeLayoutUiState(
                    parseHomeLayout(a.homeLayout).withItems(collections.keys.toList(), libraries.keys.toList()),
                    homeOrders(a),
                    collections,
                    libraries,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeLayoutUiState())

        fun toggle(
            row: HomeSectionPref,
            visible: Boolean,
        ) = edit { it.toggle(row, visible) }

        fun move(
            row: HomeSectionPref,
            by: Int,
        ) = edit { it.move(row, by) }

        fun setOrder(
            row: HomeSectionPref,
            order: SectionOrder,
        ) = viewModelScope.launch {
            settings.update { a -> a.copy(homeOrder = encodeHomeOrders(homeOrders(a) + (row.orderKey to order))) }
        }

        /** Edits what's on screen (rows for every collection / library included) and saves it. */
        private fun edit(change: (List<HomeSectionPref>) -> List<HomeSectionPref>) {
            val layout = change(state.value.layout)
            viewModelScope.launch { settings.update { it.copy(homeLayout = encodeHomeLayout(layout)) } }
        }
    }

@Composable
fun HomeLayoutRoute(
    onBack: () -> Unit,
    viewModel: HomeLayoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeLayoutScreen(
        state,
        HomeLayoutActions(onBack, viewModel::toggle, viewModel::move, viewModel::setOrder),
    )
}

/** [derived] You → Home Screen: show/hide and reorder Home sections and collection / library rails. */
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
                SectionRow(pref, state, first = i == 0, last = i == state.layout.lastIndex, actions)
            }
        }
        LayoutFootnoteText(stringResource(R.string.home_layout_footnote))
    }
    OrderGroup(state, actions)
}

@Composable
private fun SectionRow(
    pref: HomeSectionPref,
    state: HomeLayoutUiState,
    first: Boolean,
    last: Boolean,
    actions: HomeLayoutActions,
) {
    val label = rowLabel(pref, state)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KavitaSpacing.rowHeight)
            .rowDivider(!last, 16.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                label,
                style = Kavita.type.body,
                color = if (pref.visible) Kavita.colors.label else Kavita.colors.labelSecondary,
            )
            if (pref.section.perItem) {
                Text(
                    stringResource(
                        if (pref.section ==
                            HomeSection.Collection
                        ) {
                            R.string.home_row_collection
                        } else {
                            R.string.home_row_library
                        },
                    ),
                    style = Kavita.type.footnote,
                    color = Kavita.colors.labelSecondary,
                )
            }
        }
        MoveButton(KavitaIcons.ChevronUp, stringResource(R.string.home_layout_up, label), !first) {
            actions.onMove(pref, -1)
        }
        MoveButton(KavitaIcons.ChevronDown, stringResource(R.string.home_layout_down, label), !last) {
            actions.onMove(pref, 1)
        }
        KavitaSwitch(pref.visible, { actions.onToggle(pref, it) }, contentDescription = label)
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
internal fun LayoutFootnoteText(text: String) =
    Text(
        text,
        style = Kavita.type.footnote,
        color = Kavita.colors.labelSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )

/** A collection / library row's name, else the section's. */
@Composable
internal fun rowLabel(
    pref: HomeSectionPref,
    state: HomeLayoutUiState,
) = when (pref.section) {
    HomeSection.Collection -> state.collectionNames[pref.id].orEmpty()
    HomeSection.Library -> state.libraryNames[pref.id].orEmpty()
    else -> stringResource(homeSectionName(pref.section))
}

@StringRes
internal fun homeSectionName(section: HomeSection) =
    when (section) {
        HomeSection.ContinueReading -> R.string.home_section_continue
        HomeSection.OnDeck -> R.string.home_section_on_deck
        HomeSection.RecentlyUpdated -> R.string.home_section_updated
        HomeSection.RecentlyAdded -> R.string.home_section_added
        HomeSection.SmartFilters -> R.string.home_section_smart_filters
        HomeSection.WantToRead -> R.string.home_section_want_to_read
        HomeSection.ReadingLists -> R.string.home_section_reading_lists
        HomeSection.Downloaded -> R.string.home_section_downloaded
        HomeSection.Collection -> R.string.home_row_collection
        HomeSection.Library -> R.string.home_row_library
    }
