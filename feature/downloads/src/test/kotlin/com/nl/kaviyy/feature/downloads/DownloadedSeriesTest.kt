package com.nl.kaviyy.feature.downloads

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReadStateRepository
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.download.DownloadControls
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
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
class DownloadedSeriesTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val items = MutableStateFlow(previewItems)
    private val cancelled = mutableListOf<Int>()
    private val marked = mutableListOf<ReadTarget>()
    private val controls =
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
            ) {
                cancelled += chapterId
                items.update { list -> list.filterNot { it.row.chapterId == chapterId } }
            }
        }
    private val readState =
        object : ReadStateRepository {
            override suspend fun setRead(
                server: Server,
                target: ReadTarget,
                read: Boolean,
            ): AppResult<Unit> {
                marked += target
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

    private fun vm() =
        DownloadedSeriesViewModel(
            session(previewServer),
            controls,
            readState,
            SavedStateHandle(
                mapOf(DOWNLOADED_SERIES_ARG to 11),
            ),
        )

    @Test
    fun showsOnlyThisSeriesFinishedChaptersInOrder() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                val st = expectMostRecentItem()
                assertEquals("Ember Road", st.seriesName)
                assertEquals(listOf(1, 2, 9), st.chapters.map { it.row.chapterId }) // ch 48, 49, 50; 51/52 not finished
            }
        }

    @Test
    fun selectingMarksReadAndDeletes() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                vm.toggleSelecting()
                vm.toggle(2)
                vm.toggle(9)
                vm.toggle(9) // off again
                runCurrent()
                assertEquals(setOf(2), expectMostRecentItem().selected)
                vm.markRead(setOf(2))
                runCurrent()
                assertEquals(listOf<ReadTarget>(ReadTarget.Chapter(11, 2)), marked)
                assertEquals(false, expectMostRecentItem().selecting)
                vm.delete(setOf(2))
                runCurrent()
                assertEquals(listOf(2), cancelled)
                assertEquals(listOf(1, 9), expectMostRecentItem().chapters.map { it.row.chapterId })
            }
        }

    @Test
    fun deleteAllAsksFirst() =
        runTest {
            val vm = vm()
            vm.state.test {
                runCurrent()
                vm.deleteAll()
                runCurrent()
                assertEquals(true to emptyList<Int>(), expectMostRecentItem().confirmDeleteAll to cancelled)
                vm.deleteAll()
                runCurrent()
                assertEquals(listOf(1, 2, 9), cancelled)
                assertEquals(emptyList<DownloadItem>(), expectMostRecentItem().chapters)
            }
        }

    @Test
    fun continueResumesTheFirstUnfinishedChapterAndRowsOpenTheirChapter() {
        val opened = mutableListOf<Int>()
        compose.setContent {
            DownloadedSeriesPreview(Appearance.Light, actions = DownloadedSeriesActions(onRead = { opened += it }))
        }
        compose.onNodeWithText("Continue Reading").performClick() // ch 48 is read, so ch 49 (unread) is next
        compose.onNodeWithText("Chapter 48").performClick()
        assertEquals(listOf(2, 1), opened)
    }

    @Test
    fun rowsShowSizeAndReadState() {
        compose.setContent { DownloadedSeriesPreview(Appearance.Light) }
        compose.onNodeWithText("Chapter 48").assertExists()
        compose.onNodeWithText("28 pages · 147 MB").assertExists()
        compose.onNodeWithText("Read").assertExists()
        compose.onNodeWithText("25%").assertExists()
        compose.onNodeWithText("Select").performClick() // no-op actions in the preview; just reachable
    }

    @Test
    fun screenshotLight() = shot("downloaded_series_light") { DownloadedSeriesPreview(Appearance.Light) }

    @Test
    fun screenshotDark() = shot("downloaded_series_dark") { DownloadedSeriesPreview(Appearance.Dark) }

    @Test
    fun screenshotSelectingLight() =
        shot("downloaded_series_select_light") {
            DownloadedSeriesPreview(Appearance.Light, selecting = true)
        }

    private fun shot(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

private fun session(server: Server): AuthSession =
    Proxy.newProxyInstance(AuthSession::class.java.classLoader, arrayOf(AuthSession::class.java)) { _, method, _ ->
        when (method.name) {
            "getActiveServer" -> flowOf(server)
            else -> error("unused: ${method.name}")
        }
    } as AuthSession
