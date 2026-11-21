package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.DeviceRepository
import com.nl.kaviyy.core.data.isDeviceEmail
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

/** Kavita's EmailDevicePlatform values, in the order the form shows them. */
internal const val PLATFORM_CUSTOM = 0
internal const val PLATFORM_POCKETBOOK = 1
internal const val PLATFORM_KINDLE = 2
internal const val PLATFORM_KOBO = 3
internal val PLATFORMS = listOf(PLATFORM_KINDLE, PLATFORM_KOBO, PLATFORM_POCKETBOOK, PLATFORM_CUSTOM)

/** The add/edit sheet; [id] 0 = new device. */
data class DeviceForm(
    val id: Int = 0,
    val platform: Int = PLATFORMS.first(),
    val invalidEmail: Boolean = false,
    val confirmDelete: Boolean = false,
)

data class DevicesUiState(
    val devices: List<EmailDeviceDto>? = null,
    /** Couldn't load (offline). */
    val failed: Boolean = false,
    val form: DeviceForm? = null,
    val busy: Boolean = false,
    /** The last save/delete failed. */
    val changeFailed: Boolean = false,
)

data class DevicesActions(
    val onBack: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (EmailDeviceDto) -> Unit = {},
    val onPlatform: (Int) -> Unit = {},
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** You › Send to Device: the user's e-mail devices. */
@HiltViewModel
class DevicesViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val repository: DeviceRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(DevicesUiState())
        val state: StateFlow<DevicesUiState> = mutable.asStateFlow()
        val name = TextFieldState()
        val email = TextFieldState()

        init {
            reload()
        }

        fun add() = open(DeviceForm(), "", "")

        fun edit(device: EmailDeviceDto) =
            open(DeviceForm(device.id, device.platform), device.name.orEmpty(), device.emailAddress.orEmpty())

        fun setPlatform(platform: Int) = mutable.update { it.copy(form = it.form?.copy(platform = platform)) }

        fun close() = mutable.update { it.copy(form = null, changeFailed = false) }

        fun save() {
            val form = mutable.value.form ?: return
            val n = name.text.toString().trim()
            val e = email.text.toString().trim()
            when {
                n.isEmpty() -> Unit
                !isDeviceEmail(e) -> mutable.update { it.copy(form = form.copy(invalidEmail = true)) }
                else -> change { server -> repository.save(server, EmailDeviceDto(form.id, n, e, form.platform)) }
            }
        }

        /** First tap asks, the second deletes. */
        fun delete() {
            val form = mutable.value.form ?: return
            if (!form.confirmDelete) {
                mutable.update { it.copy(form = form.copy(confirmDelete = true)) }
                return
            }
            change { server -> repository.delete(server, form.id) }
        }

        private fun open(
            form: DeviceForm,
            n: String,
            e: String,
        ) {
            name.setTextAndPlaceCursorAtEnd(n)
            email.setTextAndPlaceCursorAtEnd(e)
            mutable.update { it.copy(form = form, changeFailed = false) }
        }

        private fun change(block: suspend (com.nl.kaviyy.core.model.server.Server) -> AppResult<*>) {
            if (mutable.value.busy) return
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                mutable.update { it.copy(busy = true, changeFailed = false) }
                val ok = block(server) is AppResult.Success
                mutable.update { it.copy(busy = false, changeFailed = !ok, form = if (ok) null else it.form) }
                if (ok) reload()
            }
        }

        private fun reload() {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                when (val r = repository.devices(server)) {
                    is AppResult.Success -> mutable.update { it.copy(devices = r.value, failed = false) }
                    is AppResult.Failure -> mutable.update { it.copy(failed = it.devices == null) }
                }
            }
        }
    }
