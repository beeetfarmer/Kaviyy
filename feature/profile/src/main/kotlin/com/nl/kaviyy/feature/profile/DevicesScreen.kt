package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedInputRow
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing

@Composable
fun DevicesRoute(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DevicesScreen(
        state,
        viewModel.name,
        viewModel.email,
        DevicesActions(
            onBack = onBack,
            onAdd = viewModel::add,
            onEdit = viewModel::edit,
            onPlatform = viewModel::setPlatform,
            onClose = viewModel::close,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        ),
    )
}

/** [derived] Send to Device: a grouped list of devices (name, platform) and an add row; rows open the edit sheet. */
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    name: TextFieldState,
    email: TextFieldState,
    actions: DevicesActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Kavita.colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KavitaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
    ) {
        CircleIconButton(
            KavitaIcons.ChevronLeft,
            stringResource(R.string.you_back),
            actions.onBack,
            Modifier.padding(top = 8.dp),
        )
        LargeTitleHeader(stringResource(R.string.devices_title))
        Note(R.string.devices_intro)
        val devices = state.devices
        when {
            devices != null -> {
                if (devices.isNotEmpty()) {
                    GroupedList {
                        devices.forEachIndexed { i, d ->
                            GroupedRow(
                                d.name.orEmpty(),
                                value = stringResource(platformLabel(d.platform)),
                                onClick = { actions.onEdit(d) },
                                divider = i < devices.lastIndex,
                            )
                        }
                    }
                }
                GroupedList {
                    GroupedRow(
                        stringResource(R.string.devices_add),
                        onClick = actions.onAdd,
                        showChevron = false,
                        labelColor = Kavita.colors.accent,
                    )
                }
            }

            state.failed -> {
                Note(R.string.devices_offline)
            }

            else -> {
                Note(R.string.you_loading)
            }
        }
    }
    state.form?.let { DeviceSheet(it, state, name, email, actions) }
}

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(stringResource(text), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)

@Composable
private fun DeviceSheet(
    form: DeviceForm,
    state: DevicesUiState,
    name: TextFieldState,
    email: TextFieldState,
    actions: DevicesActions,
) = KavitaBottomSheet(onDismiss = actions.onClose) {
    SheetHeader(
        stringResource(if (form.id == 0) R.string.devices_new else R.string.devices_edit),
        stringResource(R.string.devices_cancel),
        actions.onClose,
        applyLabel = stringResource(R.string.devices_save),
        onApply = { if (!state.busy) actions.onSave() },
    )
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GroupedList {
            GroupedInputRow(stringResource(R.string.devices_name), name, divider = true)
            GroupedInputRow(
                stringResource(R.string.devices_email),
                email,
                placeholder = stringResource(R.string.devices_email_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        SegmentedControl(
            PLATFORMS.map { stringResource(platformLabel(it)) },
            PLATFORMS.indexOf(form.platform).coerceAtLeast(0),
            { actions.onPlatform(PLATFORMS[it]) },
        )
        if (form.invalidEmail) Note(R.string.devices_invalid_email)
        if (state.changeFailed) Note(R.string.devices_change_failed)
        if (form.id != 0) {
            GroupedList {
                GroupedRow(
                    stringResource(
                        if (form.confirmDelete) R.string.devices_delete_confirm else R.string.devices_delete,
                    ),
                    onClick = actions.onDelete.takeIf { !state.busy },
                    showChevron = false,
                    labelColor = Kavita.colors.destructive,
                )
            }
        }
    }
}

internal fun platformLabel(platform: Int) =
    when (platform) {
        PLATFORM_POCKETBOOK -> R.string.devices_pocketbook
        PLATFORM_KINDLE -> R.string.devices_kindle
        PLATFORM_KOBO -> R.string.devices_kobo
        else -> R.string.devices_other
    }
