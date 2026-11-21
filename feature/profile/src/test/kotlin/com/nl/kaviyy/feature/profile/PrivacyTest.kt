package com.nl.kaviyy.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class PrivacyTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun togglesSaveAndDeleteAsksFirst() {
        var settings = AppSettings()
        var wiped = 0
        compose.setContent {
            KavitaTheme {
                PrivacyScreen(settings, deviceSecure = true, onBack = {
                }, onChange = { settings = it(settings) }, onDeleteLocalData = { wiped++ })
            }
        }
        compose.onNodeWithContentDescription("App Lock").performClick()
        compose.onNodeWithContentDescription("Hide in Recents").performClick()
        compose.onNodeWithText("Delete Local Data").performClick()
        assertEquals(0, wiped)
        compose.onNodeWithText("Tap again to delete all local data").performClick()
        assertEquals(1, wiped)
        assertEquals(true to true, settings.appLock to settings.hideInRecents)
    }

    @Test
    fun withoutAScreenLockAppLockStaysOff() {
        var settings = AppSettings()
        compose.setContent {
            KavitaTheme {
                PrivacyScreen(settings, deviceSecure = false, onBack = {}, onChange = {
                    settings =
                        it(settings)
                }, onDeleteLocalData = {})
            }
        }
        compose.onNodeWithContentDescription("App Lock").performClick()
        compose.onNodeWithText("Set a screen lock in Android settings to use App Lock.").assertExists()
        assertEquals(false, settings.appLock)
    }

    @Test
    fun viewModelPersists() =
        runTest {
            val store = AppSettingsStore(InMemoryDataStore(AppSettings()))
            val vm = PrivacyViewModel(store)
            vm.update { it.copy(appLock = true, lockAfterMinutes = 5) }
            runCurrent()
            assertEquals(true to 5, store.settings.first().let { it.appLock to it.lockAfterMinutes })
        }

    @Test
    fun screenshotPrivacyLight() = shot("privacy_light", Appearance.Light)

    @Test
    fun screenshotPrivacyDark() = shot("privacy_dark", Appearance.Dark)

    private fun shot(
        name: String,
        appearance: Appearance,
    ) {
        compose.setContent {
            KavitaTheme(appearance = appearance) {
                PrivacyScreen(
                    AppSettings(appLock = true),
                    deviceSecure = true,
                    onBack = {},
                    onChange = {},
                    onDeleteLocalData = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
