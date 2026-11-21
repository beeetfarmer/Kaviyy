package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AccountRepository
import com.nl.kaviyy.core.data.AuthKey
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.NewAuthKey
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Expiry choices on the create sheet, in days; null = never. */
internal val KEY_EXPIRY_DAYS = listOf(null, 30L, 365L)

enum class KeyConfirm { Rotate, Delete }

data class AuthKeysUiState(
    val keys: List<AuthKey>? = null,
    val failed: Boolean = false,
    /** The create sheet is open. */
    val creating: Boolean = false,
    val expiry: Int = 0,
    /** The key whose sheet (rotate / delete) is open. */
    val selected: AuthKey? = null,
    val confirm: KeyConfirm? = null,
    /** A new secret to show once; cleared when its sheet closes. */
    val revealed: NewAuthKey? = null,
    val busy: Boolean = false,
    val changeFailed: Boolean = false,
)

data class AuthKeysActions(
    val onBack: () -> Unit = {},
    val onNew: () -> Unit = {},
    val onExpiry: (Int) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onSelect: (AuthKey) -> Unit = {},
    val onRotate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onCopy: (String) -> Unit = {},
)

/** Account › Auth Keys: list, create (name + expiry), rotate and delete (each asks first). */
@HiltViewModel
class AuthKeysViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val accounts: AccountRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(AuthKeysUiState())
        val state: StateFlow<AuthKeysUiState> = mutable.asStateFlow()
        val name = TextFieldState()

        init {
            reload()
        }

        fun openNew() {
            name.clearText()
            mutable.update { it.copy(creating = true, expiry = 0, changeFailed = false) }
        }

        fun setExpiry(index: Int) = mutable.update { it.copy(expiry = index) }

        fun select(key: AuthKey) {
            if (!key.system) mutable.update { it.copy(selected = key, confirm = null, changeFailed = false) }
        }

        /** Closes whichever sheet is open; a revealed secret is dropped. */
        fun close() =
            mutable.update {
                it.copy(creating = false, selected = null, confirm = null, revealed = null, changeFailed = false)
            }

        fun create() {
            val n = name.text.toString().trim()
            if (n.isEmpty()) return
            val expires = KEY_EXPIRY_DAYS[mutable.value.expiry]?.let { Instant.now(clock).plus(Duration.ofDays(it)) }
            change { server -> accounts.createAuthKey(server, n, expires) }
        }

        fun rotate() = confirmThen(KeyConfirm.Rotate) { server, key -> accounts.rotateAuthKey(server, key) }

        fun delete() = confirmThen(KeyConfirm.Delete) { server, key -> accounts.deleteAuthKey(server, key.id) }

        /** First tap asks; the second runs [block]. */
        private fun confirmThen(
            kind: KeyConfirm,
            block: suspend (Server, AuthKey) -> AppResult<*>,
        ) {
            val key = mutable.value.selected ?: return
            if (mutable.value.confirm != kind) {
                mutable.update { it.copy(confirm = kind) }
                return
            }
            change { server -> block(server, key) }
        }

        /** A successful [block] returning a [NewAuthKey] reveals its secret. */
        private fun change(block: suspend (Server) -> AppResult<*>) {
            if (mutable.value.busy) return
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                mutable.update { it.copy(busy = true, changeFailed = false) }
                val r = block(server)
                if (r is AppResult.Failure) {
                    mutable.update { it.copy(busy = false, changeFailed = true) }
                } else {
                    val secret = (r as? AppResult.Success)?.value as? NewAuthKey
                    mutable.update {
                        it.copy(busy = false, creating = false, selected = null, confirm = null, revealed = secret)
                    }
                    reload()
                }
            }
        }

        private fun reload() {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                when (val r = accounts.authKeys(server)) {
                    is AppResult.Success -> mutable.update { it.copy(keys = r.value, failed = false) }
                    is AppResult.Failure -> mutable.update { it.copy(failed = it.keys == null) }
                }
            }
        }
    }
