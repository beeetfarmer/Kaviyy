package com.nl.kaviyy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.feature.auth.ConnectRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { KaviyyApp() }
    }
}

@Composable
private fun KaviyyApp(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ponytail: no NavHost yet; Navigation Compose arrives with the tabbed Main screen (M4).
    var catalogue by rememberSaveable { mutableStateOf(false) }
    if (catalogue) {
        BackHandler { catalogue = false }
        DevHome()
        return
    }
    KavitaTheme {
        when (val s = state) {
            AppState.Loading -> {
                Box(Modifier.fillMaxSize().background(Kavita.colors.background))
            }

            AppState.SignedOut -> {
                ConnectRoute(
                    onSso = {}, // TODO(M3.4): SSO spike
                    // Debug builds: long-press the logo for the component catalogue.
                    onLogoLongPress = if (BuildConfig.DEBUG) ({ catalogue = true }) else null,
                )
            }

            is AppState.SignedIn -> {
                SignedInPlaceholder(s.server) { viewModel.signOut(s.server) }
            }
        }
    }
}

/** Stand-in until Home (M4). */
@Composable
private fun SignedInPlaceholder(
    server: Server,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Kavita.colors.background)
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.signed_in_title), style = Kavita.type.largeTitle, color = Kavita.colors.label)
        Text(
            stringResource(R.string.signed_in_body, server.name),
            style = Kavita.type.bodyRegular,
            color = Kavita.colors.labelSecondary,
        )
        PrimaryButton(stringResource(R.string.sign_out), onSignOut, Modifier.fillMaxWidth())
    }
}
