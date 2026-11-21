package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.Account
import com.nl.kaviyy.core.data.AccountRepository
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.EmailChange
import com.nl.kaviyy.core.data.PASSWORD_MAX
import com.nl.kaviyy.core.data.PASSWORD_MIN
import com.nl.kaviyy.core.data.ProfileRepository
import com.nl.kaviyy.core.data.isDeviceEmail
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountSheet { Password, Email }

/** Why the sheet can't save, or what the server said. */
enum class AccountProblem { PasswordLength, PasswordMismatch, InvalidEmail, Failed }

/** Shown on the Account screen after a sheet closes. */
enum class AccountNotice { PasswordChanged, EmailSent, EmailNotSent }

data class AccountUiState(
    val server: Server? = null,
    val account: Account? = null,
    val sheet: AccountSheet? = null,
    val busy: Boolean = false,
    val problem: AccountProblem? = null,
    val notice: AccountNotice? = null,
)

/** The sheet's text fields, owned by the ViewModel. */
data class AccountFields(
    val current: TextFieldState,
    val new: TextFieldState,
    val confirm: TextFieldState,
    val email: TextFieldState,
)

data class AccountActions(
    val onBack: () -> Unit = {},
    val onSheet: (AccountSheet?) -> Unit = {},
    val onSavePassword: () -> Unit = {},
    val onSaveEmail: () -> Unit = {},
    val onAuthKeys: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

/** You › Account (M14.4). Password fields are cleared as soon as a sheet closes. */
@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val profile: ProfileRepository,
        private val accounts: AccountRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(AccountUiState())
        val state: StateFlow<AccountUiState> = mutable.asStateFlow()
        val current = TextFieldState()
        val new = TextFieldState()
        val confirm = TextFieldState()
        val email = TextFieldState()

        init {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                mutable.update { it.copy(server = server) }
                val account = profile.account(server).getOrNull()
                mutable.update { it.copy(account = account) }
            }
        }

        fun showSheet(sheet: AccountSheet?) {
            listOf(current, new, confirm, email).forEach { it.clearText() }
            mutable.update { it.copy(sheet = sheet, problem = null, notice = if (sheet != null) null else it.notice) }
        }

        fun savePassword() {
            val n = new.text.toString()
            when {
                n.length !in PASSWORD_MIN..PASSWORD_MAX -> {
                    problem(AccountProblem.PasswordLength)
                }

                n != confirm.text.toString() -> {
                    problem(AccountProblem.PasswordMismatch)
                }

                else -> {
                    submit { server, username ->
                        accounts.changePassword(server, username, current.text.toString(), n).let { r ->
                            if (r is AppResult.Success) AccountNotice.PasswordChanged else null
                        }
                    }
                }
            }
        }

        fun saveEmail() {
            val e = email.text.toString().trim()
            if (!isDeviceEmail(e)) {
                problem(AccountProblem.InvalidEmail)
                return
            }
            submit { server, _ ->
                when ((accounts.changeEmail(server, e, current.text.toString()) as? AppResult.Success)?.value) {
                    EmailChange.Sent -> {
                        AccountNotice.EmailSent
                    }

                    EmailChange.NotSent -> {
                        AccountNotice.EmailNotSent
                    }

                    EmailChange.Invalid -> {
                        mutable.update { it.copy(busy = false, problem = AccountProblem.InvalidEmail) }
                        return@submit null
                    }

                    null -> {
                        null
                    }
                }
            }
        }

        private fun problem(p: AccountProblem) = mutable.update { it.copy(problem = p) }

        /** Runs [block]; a notice closes the sheet, null keeps it open with a failure. */
        private fun submit(block: suspend (Server, String) -> AccountNotice?) {
            val s = mutable.value
            val server = s.server
            val username = s.account?.username ?: server?.username
            if (s.busy || server == null || username == null) return
            mutable.update { it.copy(busy = true, problem = null) }
            viewModelScope.launch {
                val notice = block(server, username)
                if (notice != null) {
                    showSheet(null)
                    mutable.update { it.copy(busy = false, notice = notice) }
                } else {
                    mutable.update { it.copy(busy = false, problem = it.problem ?: AccountProblem.Failed) }
                }
            }
        }
    }
