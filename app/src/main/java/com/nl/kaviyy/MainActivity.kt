package com.nl.kaviyy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.feature.auth.ConnectRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // ponytail: two screens, no NavHost yet; Navigation Compose arrives with the tabbed Main screen (M4).
            var catalogue by rememberSaveable { mutableStateOf(false) }
            if (catalogue) {
                BackHandler { catalogue = false }
                DevHome()
            } else {
                KavitaTheme {
                    ConnectRoute(
                        onSignIn = {}, // TODO(M3.2): login
                        onSso = {}, // TODO(M3.4): SSO spike
                        onAuthKey = {}, // TODO(M3.3): Auth Key paste
                        // Debug builds: long-press the logo for the component catalogue.
                        onLogoLongPress = if (BuildConfig.DEBUG) ({ catalogue = true }) else null,
                    )
                }
            }
        }
    }
}
