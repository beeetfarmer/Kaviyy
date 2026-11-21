package com.nl.kaviyy.feature.profile

import android.app.KeyguardManager
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
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
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Lock after" choices, in minutes (0 = immediately). */
internal val LOCK_AFTER_MINUTES = listOf(0, 1, 5)

@HiltViewModel
class PrivacyViewModel
    @Inject
    constructor(
        private val store: AppSettingsStore,
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
    }

@Composable
fun PrivacyRoute(
    onBack: () -> Unit,
    onDeleteLocalData: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PrivacyScreen(
        state,
        deviceSecure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true,
        onBack = onBack,
        onChange = viewModel::update,
        onDeleteLocalData = onDeleteLocalData,
    )
}

/** [derived] Privacy & security: app lock, lock delay, hide in Recents, delete local data (two taps). */
@Composable
fun PrivacyScreen(
    state: AppSettings,
    deviceSecure: Boolean,
    onBack: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onDeleteLocalData: () -> Unit,
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
    CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.you_back), onBack, Modifier.padding(top = 8.dp))
    LargeTitleHeader(stringResource(R.string.privacy_title))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupedList {
            GroupedSwitchRow(
                stringResource(R.string.privacy_app_lock),
                state.appLock && deviceSecure,
                { on -> if (deviceSecure) onChange { it.copy(appLock = on) } },
            )
        }
        Note(if (deviceSecure) R.string.privacy_app_lock_note else R.string.privacy_no_screen_lock)
        if (state.appLock && deviceSecure) {
            Note(R.string.privacy_lock_after)
            SegmentedControl(
                listOf(
                    stringResource(R.string.privacy_immediately),
                    stringResource(R.string.privacy_1_minute),
                    stringResource(R.string.privacy_5_minutes),
                ),
                LOCK_AFTER_MINUTES.indexOf(state.lockAfterMinutes).coerceAtLeast(0),
                { i -> onChange { it.copy(lockAfterMinutes = LOCK_AFTER_MINUTES[i]) } },
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupedList {
            GroupedSwitchRow(stringResource(R.string.privacy_hide_recents), state.hideInRecents, { on ->
                onChange {
                    it.copy(hideInRecents = on)
                }
            })
        }
        Note(
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                R.string.privacy_hide_note
            } else {
                R.string.privacy_hide_note_old
            },
        )
    }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupedList {
            GroupedRow(
                stringResource(
                    if (confirmWipe) R.string.you_delete_local_data_confirm else R.string.you_delete_local_data,
                ),
                onClick = { if (confirmWipe) onDeleteLocalData() else confirmWipe = true },
                showChevron = false,
                labelColor = Kavita.colors.destructiveText,
            )
        }
        Note(R.string.privacy_delete_note)
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
