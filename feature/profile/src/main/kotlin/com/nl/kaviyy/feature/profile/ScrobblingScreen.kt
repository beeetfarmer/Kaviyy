package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.LinkState
import com.nl.kaviyy.core.data.ScrobbleProvider
import com.nl.kaviyy.core.data.ScrobbleStatus
import com.nl.kaviyy.core.data.ScrobblingRepository
import com.nl.kaviyy.core.data.Tracker
import com.nl.kaviyy.core.data.TrackerAccounts
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.log.Log
import com.nl.kaviyy.core.model.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class ScrobblingUiState(
    val status: ScrobbleStatus? = null,
    val failed: Boolean = false,
    /** Directly connected trackers → account name. */
    val trackers: Map<Tracker, String> = emptyMap(),
    /** The tracker whose connect sheet is open. */
    val editing: Tracker? = null,
    val busy: Boolean = false,
    val connectFailed: Boolean = false,
)

/** Direct tracker accounts: open a sheet, open the tracker's approval page, paste back, connect or disconnect. */
class TrackerActions(
    val onEdit: (Tracker?) -> Unit = {},
    val authorizeUrl: (Tracker, String) -> String = { _, _ -> "" },
    val onConnect: (Tracker, String, String) -> Unit = { _, _, _ -> },
    val onDisconnect: (Tracker) -> Unit = {},
)

@HiltViewModel
class ScrobblingViewModel
    @Inject
    constructor(
        session: AuthSession,
        repository: ScrobblingRepository,
        private val trackers: TrackerAccounts,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(ScrobblingUiState())
        val state: StateFlow<ScrobblingUiState> = mutable.asStateFlow()

        /** MAL PKCE verifier: made when the browser opens, used when the code is pasted back. */
        private var malVerifier = ""

        val actions =
            TrackerActions(
                onEdit = { t -> mutable.update { it.copy(editing = t, connectFailed = false) } },
                authorizeUrl = ::authorizeUrl,
                onConnect = ::connect,
                onDisconnect = { t -> viewModelScope.launch { trackers.disconnect(t) } },
            )

        init {
            viewModelScope.launch {
                trackers.accounts.collect { m ->
                    mutable.update { it.copy(trackers = m.mapValues { (_, a) -> a.username }) }
                }
            }
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                val r = repository.status(server)
                mutable.update {
                    when (r) {
                        is AppResult.Success -> it.copy(status = r.value)
                        is AppResult.Failure -> it.copy(failed = true)
                    }
                }
            }
        }

        private fun authorizeUrl(
            tracker: Tracker,
            clientId: String,
        ): String =
            when (tracker) {
                Tracker.AniList -> trackers.aniListAuthorizeUrl(clientId)
                Tracker.Mal -> trackers.malAuthorizeUrl(clientId, trackers.newMalVerifier().also { malVerifier = it })
                Tracker.Kitsu -> ""
            }

        private fun connect(
            tracker: Tracker,
            first: String,
            second: String,
        ) {
            if (mutable.value.busy) return
            mutable.update { it.copy(busy = true, connectFailed = false) }
            viewModelScope.launch {
                val ok =
                    try {
                        when (tracker) {
                            Tracker.AniList -> trackers.connectAniList(first, second)
                            Tracker.Mal -> trackers.connectMal(first, second, malVerifier)
                            Tracker.Kitsu -> trackers.connectKitsu(first, second)
                        }
                        true
                    } catch (e: IOException) {
                        Log.w(TAG) { "connect failed: ${e.javaClass.simpleName}" }
                        false
                    } catch (e: IllegalArgumentException) {
                        // An unexpected answer (not the JSON the tracker documents).
                        Log.w(TAG) { "connect failed: ${e.javaClass.simpleName}" }
                        false
                    }
                mutable.update { it.copy(busy = false, connectFailed = !ok, editing = if (ok) null else it.editing) }
            }
        }

        private companion object {
            const val TAG = "Scrobbling"
        }
    }

@Composable
fun ScrobblingRoute(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    viewModel: ScrobblingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScrobblingScreen(state, onBack, onOpenSeries, viewModel.actions)
}

/**
 * [derived] Scrobbling: direct tracker accounts first (work without Kavita+), then the Kavita+ status (linked
 * accounts and paused series; linking those stays in the web UI).
 */
@Composable
fun ScrobblingScreen(
    state: ScrobblingUiState,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    trackers: TrackerActions = TrackerActions(),
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .fillMaxSize()
        .background(Kavita.colors.background)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = KavitaSpacing.screenHorizontal),
    verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
) {
    CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.you_back), onBack, Modifier.padding(top = 8.dp))
    LargeTitleHeader(stringResource(R.string.scrobbling_title))
    TrackersSection(state, trackers)
    state.editing?.let { TrackerSheet(it, state, trackers) }
    SectionHeader(stringResource(R.string.scrobbling_kavita_plus))
    val s = state.status
    when {
        s == null -> {
            Note(if (state.failed) R.string.scrobbling_offline else R.string.you_loading)
        }

        !s.licensed -> {
            Note(R.string.scrobbling_unlicensed)
        }

        else -> {
            Column {
                GroupedList {
                    ScrobbleProvider.entries.forEachIndexed { i, p ->
                        GroupedRow(
                            stringResource(
                                if (p ==
                                    ScrobbleProvider.AniList
                                ) {
                                    R.string.scrobbling_anilist
                                } else {
                                    R.string.scrobbling_mal
                                },
                            ),
                            value = stringResource(linkLabel(s.links[p] ?: LinkState.NotConnected)),
                            divider = i < ScrobbleProvider.entries.lastIndex,
                        )
                    }
                }
            }
            Note(R.string.scrobbling_web_hint)
            if (s.holds.isNotEmpty()) {
                Column {
                    SectionHeader(stringResource(R.string.scrobbling_holds))
                    GroupedList {
                        s.holds.forEachIndexed { i, h ->
                            GroupedRow(
                                h.seriesName.orEmpty(),
                                onClick = { onOpenSeries(h.seriesId) },
                                divider =
                                    i < s.holds.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(stringResource(text), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)

internal fun linkLabel(state: LinkState) =
    when (state) {
        LinkState.Connected -> R.string.scrobbling_connected
        LinkState.Expired -> R.string.scrobbling_expired
        LinkState.NotConnected -> R.string.scrobbling_not_connected
    }
