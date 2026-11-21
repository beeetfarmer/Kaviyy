package com.nl.kaviyy.feature.downloads

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.download.DownloadControls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal val AUTO_DOWNLOAD_COUNTS = listOf(0, 1, 2, 3, 5)
internal val AUTO_DELETE_DAYS = listOf(-1, 0, 1, 7)
internal val KEEP_READ_COUNTS = listOf(0, 1, 2, 5)
internal val STORAGE_LIMITS_GB = listOf(0, 1, 2, 5, 10)
internal val CONCURRENT = listOf(1, 2, 3, 4)
private const val MB_PER_GB = 1024

@HiltViewModel
class DownloadSettingsViewModel
    @Inject
    constructor(
        private val store: AppSettingsStore,
        private val controls: DownloadControls,
    ) : ViewModel() {
        val state: StateFlow<AppSettings> =
            store.settings.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                AppSettings(),
            )

        fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { store.update(transform) }
        }

        fun setWifiOnly(on: Boolean) {
            viewModelScope.launch { controls.setWifiOnly(on) }
        }

        fun clearAll() {
            viewModelScope.launch { controls.clearAll() }
        }
    }

data class DownloadSettingsActions(
    val onBack: () -> Unit = {},
    val onChange: ((AppSettings) -> AppSettings) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onClearAll: () -> Unit = {},
)

@Composable
fun DownloadSettingsRoute(
    onBack: () -> Unit,
    viewModel: DownloadSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DownloadSettingsScreen(
        state,
        DownloadSettingsActions(onBack, viewModel::update, viewModel::setWifiOnly, viewModel::clearAll),
    )
}

/** [derived] DESIGN.md §11 Download settings: Network, Automatic, Storage, Performance. */
@Composable
fun DownloadSettingsScreen(
    state: AppSettings,
    actions: DownloadSettingsActions,
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
        stringResource(R.string.downloads_back),
        actions.onBack,
        Modifier.padding(top = 8.dp),
    )
    LargeTitleHeader(stringResource(R.string.download_settings_title))
    Section(R.string.download_settings_network) {
        GroupedList {
            GroupedSwitchRow(
                stringResource(R.string.download_settings_wifi_only),
                state.downloadWifiOnly,
                actions.onWifiOnly,
            )
        }
        Note(R.string.download_settings_wifi_note)
    }
    Section(R.string.download_settings_automatic) { Automatic(state, actions.onChange) }
    Section(R.string.download_settings_storage) { Storage(state, actions) }
    Section(R.string.download_settings_performance) {
        Note(R.string.download_settings_concurrent)
        Choice(CONCURRENT.map { "$it" }, CONCURRENT.indexOf(state.concurrentDownloads)) { i ->
            actions.onChange { it.copy(concurrentDownloads = CONCURRENT[i]) }
        }
        GroupedList {
            GroupedRow(
                stringResource(R.string.download_settings_quality),
                value = stringResource(R.string.download_settings_original),
            )
        }
        Note(R.string.download_settings_quality_note)
    }
}

@Composable
private fun Automatic(
    state: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    Note(R.string.download_settings_auto_download)
    Choice(
        AUTO_DOWNLOAD_COUNTS.map { if (it == 0) stringResource(R.string.download_settings_off) else "$it" },
        AUTO_DOWNLOAD_COUNTS.indexOf(state.autoDownloadCount),
    ) { i -> onChange { it.copy(autoDownloadCount = AUTO_DOWNLOAD_COUNTS[i]) } }
    GroupedList {
        GroupedSwitchRow(stringResource(R.string.download_settings_charging), state.autoDownloadChargingOnly, { on ->
            onChange { it.copy(autoDownloadChargingOnly = on) }
        })
    }
    Note(R.string.download_settings_auto_delete)
    Choice(
        listOf(
            stringResource(R.string.download_settings_never),
            stringResource(R.string.download_settings_immediately),
            stringResource(R.string.download_settings_1_day),
            stringResource(R.string.download_settings_7_days),
        ),
        AUTO_DELETE_DAYS.indexOf(state.autoDeleteAfterDays),
    ) { i -> onChange { it.copy(autoDeleteAfterDays = AUTO_DELETE_DAYS[i]) } }
    if (state.autoDeleteAfterDays >= 0) {
        Note(R.string.download_settings_keep_read)
        Choice(KEEP_READ_COUNTS.map { "$it" }, KEEP_READ_COUNTS.indexOf(state.keepReadCount)) { i ->
            onChange { it.copy(keepReadCount = KEEP_READ_COUNTS[i]) }
        }
    }
}

@Composable
private fun Storage(
    state: AppSettings,
    actions: DownloadSettingsActions,
) {
    Note(R.string.download_settings_limit)
    Choice(
        STORAGE_LIMITS_GB.map {
            if (it ==
                0
            ) {
                stringResource(R.string.download_settings_none)
            } else {
                stringResource(R.string.download_settings_gb, it)
            }
        },
        STORAGE_LIMITS_GB.indexOf(state.storageLimitMb / MB_PER_GB),
    ) { i -> actions.onChange { it.copy(storageLimitMb = STORAGE_LIMITS_GB[i] * MB_PER_GB) } }
    var confirm by rememberSaveable { mutableStateOf(false) }
    GroupedList {
        // ponytail: internal storage only; an SD card folder (getExternalFilesDirs) when someone needs the space.
        GroupedRow(
            stringResource(R.string.download_settings_location),
            value = stringResource(R.string.download_settings_internal),
            divider = true,
        )
        GroupedRow(
            stringResource(if (confirm) R.string.download_settings_clear_confirm else R.string.download_settings_clear),
            onClick = {
                if (confirm) actions.onClearAll()
                confirm = !confirm
            },
            showChevron = false,
            labelColor = Kavita.colors.destructive,
        )
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionHeader(stringResource(title))
    content()
}

/** Segmented choice; an unknown stored value shows nothing selected rather than the wrong one. */
@Composable
private fun Choice(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) = SegmentedControl(labels, selected.coerceAtLeast(0), onSelect)

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(
    stringResource(text),
    Modifier.padding(horizontal = 16.dp),
    style = Kavita.type.footnote,
    color = Kavita.colors.labelSecondary,
)
