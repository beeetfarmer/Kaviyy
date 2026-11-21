package com.nl.kaviyy.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.Account
import com.nl.kaviyy.core.data.AllStats
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ProfileRepository
import com.nl.kaviyy.core.data.WeekStats
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.datastore.ThemeMode
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.Server
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class YouActions(
    val onProfile: () -> Unit = {},
    val onAllStats: () -> Unit = {},
    val onTheme: () -> Unit = {},
    val onReaderPrefs: () -> Unit = {},
    val onServers: () -> Unit = {},
    val onDevices: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDeleteLocalData: () -> Unit = {},
)

data class YouUiState(
    val server: Server? = null,
    val account: Account? = null,
    val week: WeekStats? = null,
    /** Stats couldn't be loaded (offline, or an older server). */
    val statsFailed: Boolean = false,
    val theme: ThemeMode = ThemeMode.System,
)

/** Screen 10: profile card and This Week from the server. */
@HiltViewModel
class YouViewModel
    @Inject
    constructor(
        session: AuthSession,
        private val profile: ProfileRepository,
        private val clock: Clock,
        settings: AppSettingsStore,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(YouUiState())
        val state: StateFlow<YouUiState> =
            combine(mutable, settings.settings) { s, a -> s.copy(theme = a.theme) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, YouUiState())

        init {
            viewModelScope.launch {
                session.activeServer.distinctUntilChangedBy { it?.id }.collect { s ->
                    mutable.value = YouUiState(s)
                    if (s != null) load(s)
                }
            }
        }

        private suspend fun load(server: Server) {
            val account = profile.account(server).getOrNull()
            mutable.update { it.copy(account = account) }
            val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            val week = account?.let { profile.week(server, it.id, LocalDate.now(clock), firstDay, clock.zone) }
            mutable.update { it.copy(week = week?.getOrNull(), statsFailed = week !is AppResult.Success) }
        }
    }

data class AllStatsUiState(
    val stats: AllStats? = null,
    val failed: Boolean = false,
)

/** [derived] All Stats: lifetime totals, pages per year, weekdays, top genres. */
@HiltViewModel
class AllStatsViewModel
    @Inject
    constructor(
        session: AuthSession,
        private val profile: ProfileRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(AllStatsUiState())
        val state: StateFlow<AllStatsUiState> = mutable.asStateFlow()

        init {
            viewModelScope.launch {
                session.activeServer.distinctUntilChangedBy { it?.id }.collect { s ->
                    mutable.value = AllStatsUiState()
                    if (s == null) return@collect
                    val r =
                        when (val a = profile.account(s)) {
                            is AppResult.Success -> profile.allStats(s, a.value.id)
                            is AppResult.Failure -> a
                        }
                    mutable.value = AllStatsUiState(r.getOrNull(), r !is AppResult.Success)
                }
            }
        }
    }
