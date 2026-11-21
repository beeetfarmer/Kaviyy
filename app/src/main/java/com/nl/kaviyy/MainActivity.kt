package com.nl.kaviyy

import android.os.Bundle
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.ServerImages
import com.nl.kaviyy.core.datastore.ThemeMode
import com.nl.kaviyy.core.designsystem.component.LocalCoverImageLoader
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.ServerPill
import com.nl.kaviyy.core.designsystem.theme.AccentPreset
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.sync.Sync
import com.nl.kaviyy.feature.auth.ConnectRoute
import com.nl.kaviyy.feature.readerepub.installBookReaderRestoreFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var images: ServerImages

    @Inject
    lateinit var sync: Sync

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        installBookReaderRestoreFactory() // before super: the FragmentManager restores fragments there
        super.onCreate(savedInstanceState)
        setContent { KaviyyApp(images) }
    }

    override fun onStart() {
        super.onStart()
        sync.requestNow() // app foreground: push anything queued while away
    }
}

@Composable
private fun KaviyyApp(
    images: ServerImages,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    HideInRecents(settings.hideInRecents)
    var switcher by rememberSaveable { mutableStateOf(false) }
    // ponytail: "Add Server" reuses the activity-scoped ConnectViewModel; a NavHost entry gives it a fresh one (M4).
    var adding by rememberSaveable { mutableStateOf(false) }
    // ponytail: tabs are local state; Navigation Compose arrives with the first pushed screen (Series, M8).
    var catalogue by rememberSaveable { mutableStateOf(false) }
    if (catalogue) {
        BackHandler { catalogue = false }
        DevHome()
        return
    }
    KavitaTheme(
        appearance = settings.theme.appearance(),
        accent = AccentPreset.entries.firstOrNull { it.name == settings.accent } ?: AccentPreset.KavitaGreen,
        dynamicAccent = settings.dynamicColor,
    ) {
        if (locked != false) {
            if (locked ==
                true
            ) {
                LockScreen(viewModel::unlocked)
            } else {
                Box(Modifier.fillMaxSize().background(Kavita.colors.background))
            }
            return@KavitaTheme
        }
        when (val s = state) {
            AppState.Loading -> {
                Box(Modifier.fillMaxSize().background(Kavita.colors.background))
            }

            AppState.SignedOut -> {
                ConnectRoute(
                    // Debug builds: long-press the logo for the component catalogue.
                    onLogoLongPress = if (BuildConfig.DEBUG) ({ catalogue = true }) else null,
                )
            }

            is AppState.SignedIn if adding -> {
                BackHandler { adding = false }
                ConnectRoute(onSignedIn = { adding = false })
            }

            is AppState.SignedIn -> {
                CompositionLocalProvider(LocalCoverImageLoader provides images.loader(s.server)) {
                    MainScreen(
                        onServerPill = { switcher = true },
                        onSignOut = { viewModel.signOut(s.server) },
                        onDeleteLocalData = viewModel::deleteAllLocalData,
                    )
                }
                if (switcher) {
                    ServerSwitcherSheet(
                        servers = servers,
                        active = s.server.id,
                        onSwitch = {
                            viewModel.switchTo(it)
                            switcher = false
                        },
                        onAdd = {
                            switcher = false
                            adding = true
                        },
                        onRemove = {
                            viewModel.remove(it)
                            switcher = false
                        },
                        onDismiss = { switcher = false },
                    )
                }
            }
        }
    }
}

private fun ThemeMode.appearance() =
    when (this) {
        ThemeMode.System -> Appearance.System
        ThemeMode.Light -> Appearance.Light
        ThemeMode.Dark -> Appearance.Dark
    }
