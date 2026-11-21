package com.nl.kaviyy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState

    data object SignedOut : AppState

    data class SignedIn(
        val server: Server,
    ) : AppState
}

private const val STOP_TIMEOUT_MS = 5_000L

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        settingsStore: AppSettingsStore,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

        val state: StateFlow<AppState> =
            session.activeServer
                .map { server -> server?.let(AppState::SignedIn) ?: AppState.SignedOut }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppState.Loading)

        val servers: StateFlow<List<Server>> =
            session.servers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        fun signOut(server: Server) {
            viewModelScope.launch { session.signOut(server.id) }
        }

        fun switchTo(id: ServerId) {
            viewModelScope.launch { session.switchTo(id) }
        }

        fun remove(id: ServerId) {
            viewModelScope.launch { session.remove(id) }
        }

        fun deleteAllLocalData() {
            viewModelScope.launch { session.deleteAllLocalData() }
        }
    }
