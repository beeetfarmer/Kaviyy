package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.ServerProber
import com.nl.kaviyy.core.network.SsoInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of the live address check under the Address row. */
sealed interface Reachability {
    data object Idle : Reachability

    data object Checking : Reachability

    data class Invalid(
        val error: ServerUrlError,
    ) : Reachability

    /** Plain HTTP: not probed until the user confirms on the insecure-HTTP sheet (M3.5). */
    data object InsecureHttp : Reachability

    /** @property version null when the server didn't say (e.g. needs sign-in first). */
    data class Found(
        val url: ServerUrl,
        val version: String?,
        val supported: Boolean,
        val sso: SsoInfo? = null,
    ) : Reachability

    data class Failed(
        val error: AppError,
    ) : Reachability
}

data class ConnectUiState(
    val reachability: Reachability = Reachability.Idle,
    val staySignedIn: Boolean = true,
    val credentialsEntered: Boolean = false,
    val signingIn: Boolean = false,
    val signInError: AppError? = null,
    val authKeySheetOpen: Boolean = false,
    /** The sheet was opened from "Continue with SSO": explain the browser step. */
    val authKeyViaSso: Boolean = false,
) {
    val sso: SsoInfo? get() = (reachability as? Reachability.Found)?.sso

    /** False when the server disabled password sign-in in favour of SSO. */
    val passwordLogin: Boolean get() = sso?.passwordLoginDisabled != true

    val canUseAuthKey: Boolean get() = reachability is Reachability.Found && !signingIn
    val canSignIn: Boolean get() = canUseAuthKey && credentialsEntered && passwordLogin
}

internal const val ADDRESS_DEBOUNCE_MS = 600L

@OptIn(FlowPreview::class)
@HiltViewModel
class ConnectViewModel
    @Inject
    constructor(
        private val prober: ServerProber,
        private val session: AuthSession,
    ) : ViewModel() {
        val address = TextFieldState()
        val username = TextFieldState()
        val password = TextFieldState()
        val authKey = TextFieldState()

        private val _state = MutableStateFlow(ConnectUiState())
        val state: StateFlow<ConnectUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                snapshotFlow { address.text.toString() }
                    .distinctUntilChanged()
                    .debounce(ADDRESS_DEBOUNCE_MS)
                    .collectLatest { check(it) }
            }
            viewModelScope.launch {
                combine(
                    snapshotFlow { username.text.isNotBlank() },
                    snapshotFlow { password.text.isNotEmpty() },
                ) { u, p ->
                    u && p
                }.collect { entered -> _state.update { it.copy(credentialsEntered = entered) } }
            }
        }

        fun setStaySignedIn(value: Boolean) = _state.update { it.copy(staySignedIn = value) }

        /** On success [AuthSession.activeServer] changes and the app leaves this screen. */
        fun signIn() {
            if (_state.value.canSignIn) {
                submit(
                    LoginMethod.Password(username.text.toString().trim(), password.text.toString()),
                ) { password.clearText() }
            }
        }

        /**
         * Auth Key sign-in, paste only (QR deferred by the user).
         * [viaSso]: opened from "Continue with SSO". Kavita's OIDC flow ends in a cookie for its own web UI, which a
         * native app can't receive (spike M3.4), so SSO users sign in on the web, create an Auth Key and paste it.
         */
        fun openAuthKey(viaSso: Boolean = false) {
            if (_state.value.canUseAuthKey) {
                _state.update { it.copy(authKeySheetOpen = true, authKeyViaSso = viaSso, signInError = null) }
            }
        }

        fun closeAuthKey() {
            authKey.clearText()
            _state.update { it.copy(authKeySheetOpen = false, authKeyViaSso = false, signInError = null) }
        }

        fun signInWithAuthKey() {
            val key = authKey.text.toString().trim()
            if (key.isNotEmpty() && _state.value.canUseAuthKey) submit(LoginMethod.AuthKey(key)) { closeAuthKey() }
        }

        private fun submit(
            method: LoginMethod,
            onSuccess: () -> Unit,
        ) {
            val found = _state.value.reachability as? Reachability.Found ?: return
            val stay = _state.value.staySignedIn
            _state.update { it.copy(signingIn = true, signInError = null) }
            viewModelScope.launch {
                val result = session.signIn(found.url, found.version, method, stay)
                _state.update { it.copy(signingIn = false, signInError = (result as? AppResult.Failure)?.error) }
                if (result is AppResult.Success) onSuccess()
            }
        }

        private suspend fun check(input: String) {
            val reachability =
                when (val parsed = ServerUrl.parse(input)) {
                    is ServerUrlResult.Invalid -> {
                        if (parsed.error ==
                            ServerUrlError.Empty
                        ) {
                            Reachability.Idle
                        } else {
                            Reachability.Invalid(parsed.error)
                        }
                    }

                    is ServerUrlResult.Valid if !parsed.url.isHttps -> {
                        Reachability.InsecureHttp
                    }

                    is ServerUrlResult.Valid -> {
                        setReachability(Reachability.Checking)
                        probe(parsed.url)
                    }
                }
            setReachability(reachability)
        }

        private suspend fun probe(url: ServerUrl): Reachability =
            // Not saved yet: a throwaway Server with the strict defaults (HTTPS, no pin).
            when (val result = prober.probe(Server(ServerId(0), url.host, url))) {
                is AppResult.Success -> {
                    Reachability.Found(
                        url,
                        result.value.version?.toString(),
                        result.value.isSupported,
                        result.value.sso,
                    )
                }

                is AppResult.Failure -> {
                    Reachability.Failed(result.error)
                }
            }

        private fun setReachability(value: Reachability) = _state.update { it.copy(reachability = value) }
    }
