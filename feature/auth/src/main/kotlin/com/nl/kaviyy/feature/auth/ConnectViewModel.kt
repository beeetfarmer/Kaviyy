package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.CertificateFetcher
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.PresentedCertificate
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

    /** Plain HTTP to a private host: needs the user's explicit opt-in (insecure-HTTP sheet, N2). */
    data class InsecureHttp(
        val url: ServerUrl,
    ) : Reachability

    /** Plain HTTP to a public host: never allowed (N2). */
    data object HttpNotAllowed : Reachability

    /** Self-signed / private-CA certificate: the user may trust it on first use (trust sheet, N3). */
    data class UntrustedCertificate(
        val url: ServerUrl,
        val certificate: PresentedCertificate,
    ) : Reachability

    /** A saved server now presents a different certificate. Hard fail, no "continue anyway" (N3). */
    data class PinMismatch(
        val certificate: PresentedCertificate,
    ) : Reachability

    /** Reachable. [server] carries the trust decisions to save on sign-in. */
    data class Found(
        val server: Server,
        val supported: Boolean,
        val sso: SsoInfo? = null,
    ) : Reachability {
        val url: ServerUrl get() = server.url
        val version: String? get() = server.version
    }

    data class Failed(
        val error: AppError,
    ) : Reachability
}

enum class ConnectSheet { AuthKey, Sso, Certificate, InsecureHttp }

data class ConnectUiState(
    val reachability: Reachability = Reachability.Idle,
    val staySignedIn: Boolean = true,
    val credentialsEntered: Boolean = false,
    val signingIn: Boolean = false,
    val signInError: AppError? = null,
    val sheet: ConnectSheet? = null,
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
        private val certificates: CertificateFetcher,
        private val session: AuthSession,
    ) : ViewModel() {
        val address = TextFieldState()
        val username = TextFieldState()
        val password = TextFieldState()
        val authKey = TextFieldState()

        private val _state = MutableStateFlow(ConnectUiState())
        val state: StateFlow<ConnectUiState> = _state.asStateFlow()

        /** Trust decisions made on the sheets this session; saved only with a successful sign-in. */
        private var acceptedPin: Pair<ServerUrl, PinnedCertificate>? = null
        private var insecureAllowedFor: ServerUrl? = null

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
         * Opens (or with null closes) a sheet. Auth Key: paste only (QR deferred by the user). Sso: Kavita's OIDC flow
         * ends in a cookie for its own web UI that a native app can't receive (spike M3.4), so SSO users create an
         * Auth Key on the web and paste it. Certificate / InsecureHttp: only for the matching reachability state.
         */
        fun showSheet(sheet: ConnectSheet?) {
            val s = _state.value
            val allowed =
                when (sheet) {
                    null -> true
                    ConnectSheet.AuthKey, ConnectSheet.Sso -> s.canUseAuthKey
                    ConnectSheet.Certificate -> s.reachability is Reachability.UntrustedCertificate
                    ConnectSheet.InsecureHttp -> s.reachability is Reachability.InsecureHttp
                }
            if (!allowed) return
            if (sheet == null) authKey.clearText()
            _state.update { it.copy(sheet = sheet, signInError = null) }
        }

        /** The sheet's primary action: sign in with the key, trust the certificate, or allow HTTP. */
        fun confirmSheet() {
            val s = _state.value
            when (val r = s.reachability) {
                is Reachability.UntrustedCertificate if s.sheet == ConnectSheet.Certificate -> {
                    if (r.certificate.matchesHost) acceptedPin = r.url to r.certificate.pin
                    recheck()
                }

                is Reachability.InsecureHttp if s.sheet == ConnectSheet.InsecureHttp -> {
                    insecureAllowedFor = r.url
                    recheck()
                }

                else -> {
                    val key = authKey.text.toString().trim()
                    if (key.isNotEmpty() && s.canUseAuthKey) submit(LoginMethod.AuthKey(key)) { showSheet(null) }
                }
            }
        }

        private fun recheck() {
            _state.update { it.copy(sheet = null) }
            viewModelScope.launch { check(address.text.toString()) }
        }

        private fun submit(
            method: LoginMethod,
            onSuccess: () -> Unit,
        ) {
            val found = _state.value.reachability as? Reachability.Found ?: return
            val stay = _state.value.staySignedIn
            _state.update { it.copy(signingIn = true, signInError = null) }
            viewModelScope.launch {
                val result = session.signIn(found.server, method, stay)
                _state.update { it.copy(signingIn = false, signInError = (result as? AppResult.Failure)?.error) }
                if (result is AppResult.Success) onSuccess()
            }
        }

        private suspend fun check(input: String) {
            val parsed = ServerUrl.parse(input)
            if (parsed is ServerUrlResult.Invalid) {
                setReachability(
                    if (parsed.error ==
                        ServerUrlError.Empty
                    ) {
                        Reachability.Idle
                    } else {
                        Reachability.Invalid(parsed.error)
                    },
                )
                return
            }
            val url = (parsed as ServerUrlResult.Valid).url
            val known = session.knownServer(url)
            val allowInsecure = known?.allowInsecure == true || insecureAllowedFor == url
            val next =
                when {
                    url.isHttps -> {
                        setReachability(Reachability.Checking)
                        val pin = acceptedPin?.takeIf { it.first == url }?.second ?: known?.pinnedCertificate
                        probe(
                            Server(ServerId(0), url.host, url, pinnedCertificate = pin),
                            savedPin = known?.pinnedCertificate,
                        )
                    }

                    !url.isPrivateHost -> {
                        Reachability.HttpNotAllowed
                    }

                    allowInsecure -> {
                        setReachability(Reachability.Checking)
                        probe(Server(ServerId(0), url.host, url, allowInsecure = true), savedPin = null)
                    }

                    else -> {
                        Reachability.InsecureHttp(url)
                    }
                }
            setReachability(next)
        }

        private suspend fun probe(
            server: Server,
            savedPin: PinnedCertificate?,
        ): Reachability =
            when (val result = prober.probe(server)) {
                is AppResult.Success -> {
                    Reachability.Found(
                        server.copy(version = result.value.version?.toString()),
                        result.value.isSupported,
                        result.value.sso,
                    )
                }

                is AppResult.Failure if result.error == AppError.Tls -> {
                    certificateProblem(server, savedPin)
                }

                is AppResult.Failure -> {
                    Reachability.Failed(result.error)
                }
            }

        private suspend fun certificateProblem(
            server: Server,
            savedPin: PinnedCertificate?,
        ): Reachability {
            val presented = certificates.fetch(server.url)
            return when {
                presented == null -> Reachability.Failed(AppError.Tls)

                savedPin != null && presented.pin != savedPin -> Reachability.PinMismatch(presented)

                // Already trusted but still failing (name mismatch, expired): nothing the user can accept.
                presented.pin == server.pinnedCertificate -> Reachability.Failed(AppError.Tls)

                else -> Reachability.UntrustedCertificate(server.url, presented)
            }
        }

        private fun setReachability(value: Reachability) = _state.update { it.copy(reachability = value) }
    }
