package com.nl.kaviyy.feature.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.download.DownloadControls
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.sync.SyncStatus
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
    fun everyControlHasASpokenLabel0() {
        compose.setContent { DownloadsPreview(Appearance.Light) }
        // TalkBack: nothing tappable may be announced without a name.
        compose
            .onAllNodes(
                hasClickAction() and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
            ).assertCountEquals(0)
    }

    @Test
    fun everyControlHasASpokenLabel1() {
        compose.setContent { DownloadsPreview(Appearance.Light, segment = 1) }
        // TalkBack: nothing tappable may be announced without a name.
        compose
            .onAllNodes(
                hasClickAction() and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
            ).assertCountEquals(0)
    }

    @Test
    fun finishedDownloadsAreGroupedBySeriesAndTheQueueIsOrdered() {
        val state = previewItems.toUiState(previewServer, 0)
        assertEquals(
            listOf("Ember Road" to 3, "Salt & Signal" to 1, "The Quiet Archive" to 1),
            state.downloaded.map { it.name to it.chapters },
        )
        assertEquals(317L * 1024 * 1024, state.downloaded.first().bytes)
        assertEquals(
            listOf(DownloadState.RUNNING, DownloadState.QUEUED, DownloadState.PAUSED, DownloadState.FAILED),
            state.queue.map { it.state },
        )
    }

    @Test
    fun storageIsBrokenDownByLibraryKind() {
        val mb = 1024L * 1024
        val state = previewItems.toUiState(previewServer, 0)
        assertEquals((140 + 45 + 132 + 30 + 61 + 9) * mb, state.usedBytes)
        assertEquals(
            listOf(StorageKind.Manga to 347 * mb, StorageKind.Comics to 61 * mb, StorageKind.Books to 9 * mb),
            state.storage,
        )
        assertEquals(StorageKind.Other, storageKind(null))
    }

    @Test
    fun viewModelFollowsTheActiveServerAndSegment() =
        runTest {
            val downloads = MutableStateFlow(previewItems)
            val vm = DownloadsViewModel(session(previewServer), controls(downloads), FakeSync(), FakeReadState())
            vm.state.test {
                runCurrent()
                assertEquals(4 to 3, expectMostRecentItem().let { it.queue.size to it.pendingChanges })
                vm.select(1)
                downloads.value = previewItems.filter { it.state == DownloadState.COMPLETED }
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals(1 to 0, st.segment to st.queue.size)
            }
        }

    @Test
    fun selectedSeriesAreMarkedReadOrDeletedTogether() =
        runTest {
            val calls = mutableListOf<String>()
            val read = FakeReadState()
            val vm =
                DownloadsViewModel(
                    session(previewServer),
                    controls(MutableStateFlow(previewItems), calls),
                    FakeSync(),
                    read,
                )
            vm.state.test {
                runCurrent()
                vm.toggleSelecting()
                vm.toggleSeries(12)
                vm.toggleSeries(11)
                runCurrent()
                assertEquals(setOf(11, 12), expectMostRecentItem().selected)
                vm.markSelectedRead()
                runCurrent()
                // Only finished downloads of the chosen series; the queued Ember Road chapters aren't touched.
                assertEquals(setOf(1, 9, 2, 3), read.marked.map { it.chapterId }.toSet())
                assertEquals(false, expectMostRecentItem().selecting)
                vm.toggleSelecting()
                vm.selectAll()
                runCurrent()
                assertEquals(true, expectMostRecentItem().allSelected)
                vm.selectAll() // all → none
                vm.toggleSeries(14)
                vm.deleteSelected() // asks
                runCurrent()
                assertEquals(true to emptyList<String>(), expectMostRecentItem().confirmDelete to calls)
                vm.deleteSelected()
                runCurrent()
                assertEquals(listOf("cancel 8"), calls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun selectModeTogglesRowsInsteadOfOpeningThem() {
        var opened = 0
        var toggled = -1
        compose.setContent {
            KavitaTheme {
                DownloadsScreen(
                    previewItems.toUiState(previewServer, 0).copy(selecting = true, selected = setOf(11)),
                    DownloadsActions(onOpenSeries = { opened++ }, onToggleSeries = { toggled = it }),
                )
            }
        }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Delete 1 series").assertExists()
        compose.onNodeWithText("Salt & Signal").performClick()
        assertEquals(0 to 12, opened to toggled)
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
    fun queueControlsAndPauseAllReachTheRightChapters() =
        runTest {
            val calls = mutableListOf<String>()
            val vm =
                DownloadsViewModel(
                    session(previewServer),
                    controls(MutableStateFlow(previewItems), calls),
                    FakeSync(),
                    FakeReadState(),
                )
            vm.state.test {
                runCurrent()
                vm.pause(4)
                vm.resume(6)
                vm.retry(7)
                vm.cancel(5)
                runCurrent()
                assertEquals(listOf("pause 4", "resume 6", "retry 7", "cancel 5"), calls)
                calls.clear()
                vm.pauseAll() // running 4 + waiting 5
                vm.resumeAll() // paused 6 only, not failed 7
                vm.removeRead()
                runCurrent()
                assertEquals(listOf("pause 4", "pause 5", "resume 6", "removeRead 1"), calls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun queueRowButtonsCallTheirActions() {
        val calls = mutableListOf<String>()
        compose.setContent {
            KavitaTheme(appearance = Appearance.Light) {
                DownloadsScreen(
                    previewItems.toUiState(previewServer, 1),
                    DownloadsActions(onRetry = { calls += "retry $it" }, onPauseAll = { calls += "all" }),
                )
            }
        }
        compose.onNodeWithText("Failed · Retry").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Pause All").performClick()
        assertEquals(listOf("retry 7", "all"), calls)
    }

    @Test
    fun queueRowsSayWhatIsHappening() {
        compose.setContent { DownloadsPreview(Appearance.Light, segment = 1) }
        compose.onNodeWithText("Ember Road · Ch. 51").assertExists()
        compose.onNodeWithText("12 of 28 pages").assertExists()
        compose.onNodeWithText("Paused · 9 of 30 pages").assertExists()
        compose.onNodeWithText("Failed · Retry").assertExists()
    }

    @Test
    fun syncLineOffersSyncNowOnlyWithChangesWaiting() {
        var synced = 0
        var pending by androidx.compose.runtime.mutableIntStateOf(3)
        compose.setContent { KavitaTheme { SyncLine(pending) { synced++ } } }
        compose.onNodeWithText("3 changes waiting to sync · Sync now").performClick()
        assertEquals(1, synced)
        pending = 0
        compose.onNodeWithText("All progress synced").assertExists()
        compose.onNodeWithText("Sync now", substring = true).assertDoesNotExist()
    }

    @Test
    fun screenshotDownloadedLight() = shot("downloads_light") { DownloadsPreview(Appearance.Light) }

    @Test
    fun screenshotDownloadedDark() = shot("downloads_dark") { DownloadsPreview(Appearance.Dark) }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi", fontScale = 2f)
    fun screenshotQueueFontScale200() =
        shot("downloads_queue_font200") { DownloadsPreview(Appearance.Light, segment = 1) }

    @Test
    fun screenshotQueueLight() = shot("downloads_queue_light") { DownloadsPreview(Appearance.Light, segment = 1) }

    @Test
    fun screenshotQueueDark() = shot("downloads_queue_dark") { DownloadsPreview(Appearance.Dark, segment = 1) }

    @Test
    fun screenshotEmptyLight() =
        shot("downloads_empty_light") { DownloadsPreview(Appearance.Light, items = emptyList()) }

    @Test
    fun screenshotEmptyDark() = shot("downloads_empty_dark") { DownloadsPreview(Appearance.Dark, items = emptyList()) }

    @Test
    fun screenshotSelectingLight() = shot("downloads_selecting_light") { SelectingShot(Appearance.Light) }

    @Test
    fun screenshotSelectingDark() = shot("downloads_selecting_dark") { SelectingShot(Appearance.Dark) }

    @androidx.compose.runtime.Composable
    private fun SelectingShot(appearance: Appearance) =
        KavitaTheme(appearance = appearance) {
            DownloadsScreen(
                previewItems.toUiState(previewServer, 0).copy(selecting = true, selected = setOf(11, 14)),
                DownloadsActions(),
            )
        }

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

private fun controls(
    items: MutableStateFlow<List<DownloadItem>>,
    calls: MutableList<String> = mutableListOf(),
) = object : DownloadControls {
    override fun downloads(server: Server) = items

    override suspend fun pause(
        serverId: Long,
        chapterId: Int,
    ) {
        calls += "pause $chapterId"
    }

    override suspend fun resume(
        serverId: Long,
        chapterId: Int,
    ) {
        calls += "resume $chapterId"
    }

    override suspend fun retry(
        serverId: Long,
        chapterId: Int,
    ) {
        calls += "retry $chapterId"
    }

    override suspend fun cancel(
        serverId: Long,
        chapterId: Int,
    ) {
        calls += "cancel $chapterId"
    }

    override suspend fun removeRead(serverId: Long) {
        calls += "removeRead $serverId"
    }
}

private class FakeReadState : ReadStateRepository {
    val marked = mutableListOf<ReadTarget.Chapter>()

    override suspend fun setRead(
        server: Server,
        target: ReadTarget,
        read: Boolean,
    ): AppResult<Unit> {
        if (target is ReadTarget.Chapter) marked += target
        return AppResult.Success(Unit)
    }

    override suspend fun setWantToRead(
        server: Server,
        seriesId: Int,
        on: Boolean,
    ) = AppResult.Success(Unit)

    override suspend fun setRating(
        server: Server,
        seriesId: Int,
        rating: Float,
    ) = AppResult.Success(Unit)
}

private class FakeSync : SyncStatus {
    override fun pending(server: Server) = flowOf(3)

    override fun syncNow() = Unit
}
