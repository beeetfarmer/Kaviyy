package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ServerProber
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
    ) : Reachability

    data class Failed(
        val error: AppError,
    ) : Reachability
}

data class ConnectUiState(
    val reachability: Reachability = Reachability.Idle,
    val staySignedIn: Boolean = true,
    val credentialsEntered: Boolean = false,
) {
    val canSignIn: Boolean get() = reachability is Reachability.Found && credentialsEntered
}

internal const val ADDRESS_DEBOUNCE_MS = 600L

@OptIn(FlowPreview::class)
@HiltViewModel
class ConnectViewModel
    @Inject
    constructor(
        private val prober: ServerProber,
    ) : ViewModel() {
        val address = TextFieldState()
        val username = TextFieldState()
        val password = TextFieldState()

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
                    Reachability.Found(url, result.value.version?.toString(), result.value.isSupported)
                }

                is AppResult.Failure -> {
                    Reachability.Failed(result.error)
                }
            }

        private fun setReachability(value: Reachability) = _state.update { it.copy(reachability = value) }
    }
