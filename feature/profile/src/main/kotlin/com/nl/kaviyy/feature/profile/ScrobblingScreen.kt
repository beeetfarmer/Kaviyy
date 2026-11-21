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
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScrobblingUiState(
    val status: ScrobbleStatus? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class ScrobblingViewModel
    @Inject
    constructor(
        session: AuthSession,
        repository: ScrobblingRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(ScrobblingUiState())
        val state: StateFlow<ScrobblingUiState> = mutable.asStateFlow()

        init {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                mutable.value =
                    when (val r = repository.status(server)) {
                        is AppResult.Success -> ScrobblingUiState(r.value)
                        is AppResult.Failure -> ScrobblingUiState(failed = true)
                    }
            }
        }
    }

@Composable
fun ScrobblingRoute(
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    viewModel: ScrobblingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScrobblingScreen(state, onBack, onOpenSeries)
}

/** [derived] Kavita+ scrobbling status: linked accounts and paused series. Linking stays in the web UI. */
@Composable
fun ScrobblingScreen(
    state: ScrobblingUiState,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
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
                SectionHeader(stringResource(R.string.scrobbling_accounts))
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
