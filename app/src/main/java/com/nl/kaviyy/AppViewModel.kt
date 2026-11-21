package com.nl.kaviyy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState

    data object SignedOut : AppState

    data class SignedIn(
        val server: Server,
    ) : AppState
}

private const val STOP_TIMEOUT_MS = 5_000L
private const val MINUTE_MS = 60_000L

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        settingsStore: AppSettingsStore,
        private val clock: Clock,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

        private val lock = MutableStateFlow<Boolean?>(null)

        /** App lock: null until settings load (show nothing), then whether the content is hidden. */
        val locked: StateFlow<Boolean?> = lock.asStateFlow()
        private var backgroundedAt: Long? = null

        init {
            // A cold start locks when app lock is on; this ViewModel survives rotation, not process death.
            viewModelScope.launch { lock.value = settingsStore.settings.first().appLock }
        }

        /** The app left the screen. Ignored while locked (the unlock prompt itself covers the app). */
        fun onBackground() {
            if (lock.value == false) backgroundedAt = clock.millis()
        }

        fun onForeground() {
            val at = backgroundedAt ?: return
            backgroundedAt = null
            val s = settings.value
            if (s.appLock && clock.millis() - at >= s.lockAfterMinutes * MINUTE_MS) lock.value = true
        }

        fun unlocked() {
            lock.value = false
        }

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
