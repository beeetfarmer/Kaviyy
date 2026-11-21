package com.nl.kaviyy.feature.downloads

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.download.DownloadControls
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.emptyFlow
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
@Config(qualifiers = "w412dp-h1400dp-xhdpi")
class DownloadSettingsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val controls =
        object : DownloadControls {
            override fun downloads(server: Server) = emptyFlow<List<DownloadItem>>()

            override suspend fun pause(
                serverId: Long,
                chapterId: Int,
            ) = Unit

            override suspend fun resume(
                serverId: Long,
                chapterId: Int,
            ) = Unit

            override suspend fun retry(
                serverId: Long,
                chapterId: Int,
            ) = Unit

            override suspend fun cancel(
                serverId: Long,
                chapterId: Int,
            ) = Unit

            override suspend fun setWifiOnly(on: Boolean) {
                calls += "wifi $on"
            }

            override suspend fun clearAll() {
                calls += "clear"
            }
        }

    @Test
    fun viewModelSavesChoicesAndRoutesNetworkAndClear() =
        runTest {
            val prefs = InMemoryDataStore(AppSettings())
            val vm = DownloadSettingsViewModel(AppSettingsStore(prefs), controls)
            vm.update { it.copy(concurrentDownloads = CONCURRENT[3], storageLimitMb = STORAGE_LIMITS_GB[2] * 1024) }
            vm.setWifiOnly(false)
            vm.clearAll()
            runCurrent()
            assertEquals(4 to 2048, prefs.data.first().let { it.concurrentDownloads to it.storageLimitMb })
            assertEquals(listOf("wifi false", "clear"), calls)
        }

    @Test
    fun clearAllNeedsTwoTapsAndChoicesWriteSettings() {
        var settings = AppSettings()
        val actions =
            DownloadSettingsActions(onChange = { settings = it(settings) }, onClearAll = { calls += "clear" })
        compose.setContent { DownloadSettingsPreview(Appearance.Light, AppSettings(), actions) }
        compose.onNodeWithText("Clear All Downloads").performScrollTo().performClick()
        assertEquals(emptyList(), calls)
        compose.onNodeWithText("Tap again to clear all downloads").performClick()
        assertEquals(listOf("clear"), calls)
        compose.onNodeWithText("7 days").performScrollTo().performClick()
        assertEquals(7, settings.autoDeleteAfterDays)
        compose.onNodeWithText("Keep at least the last read chapters").assertDoesNotExist() // shown once saved
    }

    @Test
    fun screenshotLight() = shot("download_settings_light") { DownloadSettingsPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = shot("download_settings_dark") { DownloadSettingsPreview(Appearance.Dark) }

    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
