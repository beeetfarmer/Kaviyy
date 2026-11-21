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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ProfileRepository
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.result.getOrNull
import com.nl.kaviyy.core.model.server.KavitaVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Open-source software the app ships, with its license (kept by hand: update when a runtime dependency changes). */
internal val LICENSES =
    listOf(
        "AndroidX & Jetpack Compose" to "Apache 2.0",
        "Kotlin, Coroutines, Serialization" to "Apache 2.0",
        "Dagger & Hilt" to "Apache 2.0",
        "OkHttp & Retrofit" to "Apache 2.0",
        "Coil" to "Apache 2.0",
        "Google Tink" to "Apache 2.0",
        "Readium Kotlin Toolkit" to "BSD 3-Clause",
        "JDK desugaring" to "GPL 2.0 + CPE",
        "Inter, Literata (fonts)" to "SIL OFL 1.1",
    )

data class AboutUiState(
    val serverName: String? = null,
    val serverVersion: String? = null,
    val supported: Boolean = true,
)

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        session: AuthSession,
        profile: ProfileRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(AboutUiState())
        val state: StateFlow<AboutUiState> = mutable.asStateFlow()

        init {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                // The live version (the stored one is from the last connect).
                val version = profile.account(server).getOrNull()?.kavitaVersion ?: server.version
                mutable.value =
                    AboutUiState(server.name, version, KavitaVersion.parse(version)?.isSupported ?: true)
            }
        }
    }

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appVersion =
        runCatching {
            context.packageManager
                .getPackageInfo(
                    context.packageName,
                    0,
                ).versionName
        }.getOrNull()
    AboutScreen(state, appVersion.orEmpty(), onBack)
}

/** [derived] About: app and server versions, then open-source licenses. */
@Composable
fun AboutScreen(
    state: AboutUiState,
    appVersion: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .fillMaxSize()
        .background(Kavita.colors.background)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = KavitaSpacing.screenHorizontal)
        .padding(bottom = KavitaSpacing.contentBottomPadding),
    verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
) {
    CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.you_back), onBack, Modifier.padding(top = 8.dp))
    LargeTitleHeader(stringResource(R.string.about_title))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupedList {
            GroupedRow(stringResource(R.string.about_app_version), value = appVersion, divider = true)
            GroupedRow(
                state.serverName ?: stringResource(R.string.about_server),
                value = state.serverVersion?.let { stringResource(R.string.about_kavita_version, it) } ?: "–",
            )
        }
        if (!state.supported) Note(R.string.about_unsupported)
    }
    Column {
        SectionHeader(stringResource(R.string.about_licenses))
        GroupedList {
            LICENSES.forEachIndexed { i, (name, license) ->
                GroupedRow(name, value = license, divider = i < LICENSES.lastIndex)
            }
        }
    }
    Note(R.string.about_kavita_note)
}

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(
    stringResource(text),
    Modifier.padding(horizontal = 16.dp),
    style = Kavita.type.footnote,
    color = Kavita.colors.labelSecondary,
)
