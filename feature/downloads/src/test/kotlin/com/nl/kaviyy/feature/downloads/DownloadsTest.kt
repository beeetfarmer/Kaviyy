package com.nl.kaviyy.feature.downloads

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.download.DownloadControls
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class DownloadsTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun finishedDownloadsAreGroupedBySeriesAndTheQueueIsOrdered() {
        val state = previewItems.toUiState(previewServer, 0)
        assertEquals(listOf("Ember Road" to 2, "Salt & Signal" to 1), state.downloaded.map { it.name to it.chapters })
        assertEquals(272L * 1024 * 1024, state.downloaded.first().bytes)
        assertEquals(
            listOf(DownloadState.RUNNING, DownloadState.QUEUED, DownloadState.PAUSED, DownloadState.FAILED),
            state.queue.map { it.state },
        )
    }

    @Test
    fun viewModelFollowsTheActiveServerAndSegment() =
        runTest {
            val downloads = MutableStateFlow(previewItems)
            val vm = DownloadsViewModel(session(previewServer), controls(downloads))
            vm.state.test {
                runCurrent()
                assertEquals(4, expectMostRecentItem().queue.size)
                vm.select(1)
                downloads.value = previewItems.take(3)
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals(1 to 0, st.segment to st.queue.size)
            }
        }

    @Test
    fun emptyStateOffersTheLibrary() {
        var browsed = 0
        compose.setContent {
            KavitaTheme {
                DownloadsScreen(
                    emptyList<DownloadItem>().toUiState(previewServer, 0),
                    DownloadsActions(onBrowse = { browsed++ }),
                )
            }
        }
        compose.onNodeWithText("Nothing downloaded yet").assertExists()
        compose.onNodeWithText("Browse Library").performClick()
        assertEquals(1, browsed)
    }

    @Test
    fun queueRowsSayWhatIsHappening() {
        compose.setContent { DownloadsPreview(Appearance.Light, segment = 1) }
        compose.onNodeWithText("Ember Road · Ch. 51").assertExists()
        compose.onNodeWithText("12 of 28 pages").assertExists()
        compose.onNodeWithText("Paused · 9 of 30 pages").assertExists()
        compose.onNodeWithText("Failed · 2 of 30 pages").assertExists()
    }

    @Test
    fun screenshotDownloadedLight() = shot("downloads_light") { DownloadsPreview(Appearance.Light) }

    @Test
    fun screenshotDownloadedDark() = shot("downloads_dark") { DownloadsPreview(Appearance.Dark) }

    @Test
    fun screenshotQueueLight() = shot("downloads_queue_light") { DownloadsPreview(Appearance.Light, segment = 1) }

    @Test
    fun screenshotQueueDark() = shot("downloads_queue_dark") { DownloadsPreview(Appearance.Dark, segment = 1) }

    @Test
    fun screenshotEmptyLight() =
        shot("downloads_empty_light") { DownloadsPreview(Appearance.Light, items = emptyList()) }

    @Test
    fun screenshotEmptyDark() = shot("downloads_empty_dark") { DownloadsPreview(Appearance.Dark, items = emptyList()) }

    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

/** An [AuthSession] whose active server is [server]; nothing else is used here. */
private fun session(server: Server): AuthSession =
    Proxy.newProxyInstance(AuthSession::class.java.classLoader, arrayOf(AuthSession::class.java)) { _, method, _ ->
        when (method.name) {
            "getActiveServer" -> flowOf(server)
            else -> error("unused: ${method.name}")
        }
    } as AuthSession

private fun controls(items: MutableStateFlow<List<DownloadItem>>) =
    object : DownloadControls {
        override fun downloads(server: Server) = items

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
    }
