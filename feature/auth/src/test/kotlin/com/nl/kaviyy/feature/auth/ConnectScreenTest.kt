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

    private val kServer =
        com.nl.kaviyy.core.model.server.Server(
            com.nl.kaviyy.core.model.server
                .ServerId(0),
            "k",
            (
                com.nl.kaviyy.core.model.server.ServerUrl.parse(
                    "https://k.lan",
                ) as com.nl.kaviyy.core.model.server.ServerUrlResult.Valid
            ).url,
        )

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
                        ConnectUiState(sheet = ConnectSheet.AuthKey, signInError = AppError.Unauthorized),
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
    fun ssoSheetExplainsTheBrowserStep() {
        val found =
            Reachability.Found(
                kServer,
                true,
                com.nl.kaviyy.core.network
                    .SsoInfo("Authelia", passwordLoginDisabled = true),
            )
        compose.setContent {
            KavitaTheme {
                androidx.compose.foundation.layout.Column {
                    AuthKeySheetContent(
                        ConnectUiState(found, sheet = ConnectSheet.Sso),
                        TextFieldState(),
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("Open Kavita in Browser").assertExists()
        compose.onNodeWithText("Sign in with Authelia", substring = true).assertExists()
    }

    @Test
    fun passwordDisabledHidesAccountSection() {
        val found =
            Reachability.Found(
                kServer,
                true,
                com.nl.kaviyy.core.network
                    .SsoInfo(null, passwordLoginDisabled = true),
            )
        show(ConnectUiState(found))
        compose.onNodeWithText("Continue with SSO").assertExists()
        compose.onNodeWithText("Username").assertDoesNotExist()
        compose.onNodeWithText("Sign In").assertDoesNotExist()
    }

    @Test
    fun certificateSheetShowsFingerprintAndOnlyOffersTrustForMatchingNames() {
        val cert =
            com.nl.kaviyy.core.network.PresentedCertificate(
                com.nl.kaviyy.core.model.server.PinnedCertificate
                    .of(byteArrayOf(1)),
                "CN=kavita",
                "CN=kavita",
                java.util.Date(0),
                java.util.Date(0),
                matchesHost = false,
            )
        val state =
            ConnectUiState(Reachability.UntrustedCertificate(kServer.url, cert), sheet = ConnectSheet.Certificate)
        compose.setContent {
            KavitaTheme {
                androidx.compose.foundation.layout.Column {
                    TrustSheetContent(
                        state,
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText(cert.fingerprint).assertExists()
        compose.onNodeWithText("Trust").assertDoesNotExist()
    }

    @Test
    fun screenshotLight() =
        captureRoboImage("src/test/screenshots/connect_light.png") { ConnectPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = captureRoboImage("src/test/screenshots/connect_dark.png") { ConnectPreview(Appearance.Dark) }
}
