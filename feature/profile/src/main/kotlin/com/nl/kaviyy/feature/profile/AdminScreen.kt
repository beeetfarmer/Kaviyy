package com.nl.kaviyy.feature.profile

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AdminRepository
import com.nl.kaviyy.core.data.AdminServerInfo
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CatalogRepository
import com.nl.kaviyy.core.data.ServerJob
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.getOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class AdminUiState(
    val info: AdminServerInfo? = null,
    val libraries: List<LibraryEntity> = emptyList(),
    val jobs: List<ServerJob> = emptyList(),
    val recent: List<Pair<String, Long>> = emptyList(),
    /** Something couldn't be loaded (offline, or not an admin any more). */
    val failed: Boolean = false,
    /** Outcome of the last scan request: true started, false refused. */
    val scanStarted: Boolean? = null,
)

data class AdminActions(
    val onBack: () -> Unit = {},
    /** Null scans every library. */
    val onScan: (libraryId: Int?) -> Unit = {},
)

/** Admin overview; the server enforces the role on every call. */
@HiltViewModel
class AdminViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val admin: AdminRepository,
        catalog: CatalogRepository,
    ) : ViewModel() {
        private val loaded = MutableStateFlow(AdminUiState())
        val state: StateFlow<AdminUiState> =
            combine(loaded, session.activeServer.filterNotNull().flatMapLatest { catalog.libraries(it) }) { s, libs ->
                s.copy(libraries = libs)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, AdminUiState())

        init {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                val info = async { admin.serverInfo(server) }
                val jobs = async { admin.jobs(server) }
                val recent = async { admin.recentlyAdded(server) }
                val results = listOf(info.await(), jobs.await(), recent.await())
                loaded.update {
                    it.copy(
                        info = info.await().getOrNull(),
                        jobs = jobs.await().getOrNull().orEmpty(),
                        recent = recent.await().getOrNull().orEmpty(),
                        failed = results.any { r -> r is AppResult.Failure },
                    )
                }
            }
        }

        fun scan(libraryId: Int?) {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                val ok = admin.scanLibrary(server, libraryId) is AppResult.Success
                loaded.update { it.copy(scanStarted = ok) }
            }
        }
    }

@Composable
fun AdminRoute(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AdminScreen(state, AdminActions(onBack, viewModel::scan))
}

/**
 * [derived] Server Admin, built like About: SERVER facts, LIBRARIES with Scan (and Scan All), SCHEDULED TASKS with
 * their last run, RECENTLY ADDED. User management, folders and email/OIDC stay on the web.
 */
@Composable
fun AdminScreen(
    state: AdminUiState,
    actions: AdminActions,
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
    CircleIconButton(
        KavitaIcons.ChevronLeft,
        stringResource(R.string.you_back),
        actions.onBack,
        Modifier.padding(top = 8.dp),
    )
    LargeTitleHeader(stringResource(R.string.admin_title))
    if (state.failed) Note(stringResource(R.string.admin_load_failed))
    ServerSection(state.info)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section(R.string.admin_libraries) {
            state.libraries.forEach { lib ->
                GroupedRow(
                    lib.name,
                    value = stringResource(R.string.admin_scan),
                    onClick = { actions.onScan(lib.id) },
                    divider = true,
                )
            }
            GroupedRow(
                stringResource(R.string.admin_scan_all),
                onClick = { actions.onScan(null) },
                showChevron = false,
                labelColor = Kavita.colors.accent,
            )
        }
        state.scanStarted?.let { ok ->
            Note(stringResource(if (ok) R.string.admin_scan_started else R.string.admin_scan_failed))
        }
    }
    if (state.jobs.isNotEmpty()) {
        Section(R.string.admin_tasks) {
            state.jobs.forEachIndexed { i, job ->
                val last = if (job.lastRun > 0) date(job.lastRun) else stringResource(R.string.admin_never)
                GroupedRow(job.title, value = last, divider = i < state.jobs.lastIndex)
            }
        }
    }
    if (state.recent.isNotEmpty()) {
        Section(R.string.admin_recent) {
            state.recent.forEachIndexed { i, (name, added) ->
                GroupedRow(
                    name,
                    value = added.takeIf { it > 0 }?.let { date(it) },
                    divider = i < state.recent.lastIndex,
                )
            }
        }
    }
    Note(stringResource(R.string.admin_web_note))
}

@Composable
private fun ServerSection(info: AdminServerInfo?) =
    Section(R.string.admin_server) {
        GroupedRow(stringResource(R.string.admin_version), value = info?.version ?: "–", divider = true)
        GroupedRow(
            stringResource(R.string.admin_docker),
            value = info?.let { stringResource(if (it.docker) R.string.admin_yes else R.string.admin_no) } ?: "–",
            divider = true,
        )
        GroupedRow(
            stringResource(R.string.admin_installed),
            value =
                info?.installedAt?.takeIf { it > 0 }?.let { date(it) } ?: "–",
        )
    }

@Composable
private fun Section(
    title: Int,
    rows: @Composable () -> Unit,
) = Column {
    SectionHeader(stringResource(title))
    GroupedList { rows() }
}

@Composable
private fun Note(text: String) =
    Text(text, Modifier.padding(horizontal = 16.dp), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)

@Composable
private fun date(millis: Long): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalConfiguration.current.locales[0])
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
