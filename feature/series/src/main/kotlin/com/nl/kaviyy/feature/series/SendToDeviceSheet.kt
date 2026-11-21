package com.nl.kaviyy.feature.series

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.DeviceRepository
import com.nl.kaviyy.core.data.SendTarget
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.network.EmailDeviceDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendSheetState(
    val devices: List<EmailDeviceDto>? = null,
    val failed: Boolean = false,
    val sending: Int? = null,
    /** Device ids the server accepted a send for. */
    val sent: Set<Int> = emptySet(),
    val sendFailed: Boolean = false,
)

@HiltViewModel
class SendToDeviceViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val repository: DeviceRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(SendSheetState())
        val state: StateFlow<SendSheetState> = mutable.asStateFlow()

        fun open() {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                mutable.value =
                    when (val r = repository.devices(server)) {
                        is AppResult.Success -> SendSheetState(r.value)
                        is AppResult.Failure -> SendSheetState(failed = true)
                    }
            }
        }

        fun send(
            deviceId: Int,
            target: SendTarget,
        ) {
            if (mutable.value.sending != null) return
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                mutable.update { it.copy(sending = deviceId, sendFailed = false) }
                val ok = repository.send(server, deviceId, target) is AppResult.Success
                mutable.update {
                    it.copy(sending = null, sent = if (ok) it.sent + deviceId else it.sent, sendFailed = !ok)
                }
            }
        }
    }

@Composable
internal fun SendToDeviceSheet(
    target: SendTarget,
    onDismiss: () -> Unit,
    viewModel: SendToDeviceViewModel = hiltViewModel(),
) {
    LaunchedEffect(target) { viewModel.open() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SendToDeviceContent(state, onDismiss) { viewModel.send(it, target) }
}

/** [derived] Pick a device; the server e-mails the files. A check marks devices it was sent to. */
@Composable
internal fun SendToDeviceContent(
    state: SendSheetState,
    onDismiss: () -> Unit,
    onSend: (Int) -> Unit,
) = KavitaBottomSheet(onDismiss = onDismiss) {
    SheetHeader(stringResource(R.string.series_send_title), stringResource(R.string.series_done), onDismiss)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
        val devices = state.devices
        when {
            devices.isNullOrEmpty() -> {
                Note(
                    when {
                        devices != null -> R.string.series_send_no_devices
                        state.failed -> R.string.series_send_offline
                        else -> R.string.series_send_loading
                    },
                )
            }

            else -> {
                GroupedList {
                    devices.forEachIndexed { i, d ->
                        GroupedRow(
                            d.name.orEmpty(),
                            value = stringResource(R.string.series_sending).takeIf { state.sending == d.id },
                            onClick = { onSend(d.id) }.takeIf { state.sending == null },
                            showChevron = false,
                            divider = i < devices.lastIndex,
                        ) {
                            if (d.id in state.sent) {
                                Icon(
                                    KavitaIcons.Check,
                                    stringResource(R.string.series_sent),
                                    Modifier.size(20.dp),
                                    tint = Kavita.colors.accent,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.sendFailed) Note(R.string.series_send_failed, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Note(
    text: Int,
    modifier: Modifier = Modifier,
) = Text(stringResource(text), modifier, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
