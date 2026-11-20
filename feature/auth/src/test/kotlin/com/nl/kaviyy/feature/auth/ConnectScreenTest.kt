package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ConnectScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: ConnectUiState) =
        compose.setContent {
            KavitaTheme {
                ConnectScreen(state, TextFieldState("k.example"), TextFieldState(), TextFieldState(), {}, {}, {}, {})
            }
        }

    @Test
    fun foundServerShowsVersionAndEnablesSignIn() {
        compose.setContent { ConnectPreview(Appearance.Light) }
        compose.onNodeWithText("Server found · Kavita 0.9.0.2").assertExists()
        compose.onNodeWithContentDescription("Server reachable").assertExists()
        compose.onNodeWithText("Sign In").assertIsEnabled()
    }

    @Test
    fun failureShowsErrorAndDisablesSignIn() {
        show(ConnectUiState(reachability = Reachability.Failed(AppError.Network), credentialsEntered = true))
        compose.onNodeWithText("Can’t reach this server. Check the address and your connection.").assertExists()
        compose.onNodeWithText("Sign In").assertIsNotEnabled()
    }

    @Test
    fun authKeySheetShowsHelpAndWrongKeyError() {
        compose.setContent {
            KavitaTheme {
                androidx.compose.foundation.layout.Column {
                    AuthKeySheetContent(
                        ConnectUiState(authKeySheetOpen = true, signInError = AppError.Unauthorized),
                        TextFieldState(),
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("Paste your key").assertExists()
        compose.onNodeWithText("That key wasn’t accepted. Check it or create a new one.").assertExists()
    }

    @Test
    fun screenshotLight() =
        captureRoboImage("src/test/screenshots/connect_light.png") { ConnectPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = captureRoboImage("src/test/screenshots/connect_dark.png") { ConnectPreview(Appearance.Dark) }
}
