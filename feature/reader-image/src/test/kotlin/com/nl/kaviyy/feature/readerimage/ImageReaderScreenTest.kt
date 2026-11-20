package com.nl.kaviyy.feature.readerimage

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class ImageReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun chromeShowsTitlePageCountAndActions() {
        val calls = mutableListOf<String>()
        compose.setContent {
            ReaderPreview(
                actions =
                    ImageReaderActions(
                        onClose = { calls += "close" },
                        onToggleDirection = { calls += "direction" },
                        onNextChapter = { calls += "next" },
                        onPreviousChapter = { calls += "previous" },
                        onPage = { calls += "page $it" },
                    ),
            )
        }
        compose.onNodeWithText("Ember Road").assertExists()
        compose.onNodeWithText("Vol. 5 · Chapter 49").assertExists()
        compose.onNodeWithText("Page 14 of 32").assertExists()
        compose.onNodeWithText("18 left").assertExists()
        compose.onNodeWithText("Right to left").performClick()
        compose.onNodeWithContentDescription("Next chapter").performClick()
        compose.onNodeWithContentDescription("Previous chapter").performClick()
        compose.onNodeWithContentDescription("Page").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        compose.onNodeWithContentDescription("Close reader").performClick()
        assertEquals(listOf("direction", "next", "previous", "page 20", "close"), calls)
    }

    @Test
    fun missingNeighbourDisablesItsButton() {
        compose.setContent { ReaderPreview(previewReader.copy(rightToLeft = false, nextChapterId = null)) }
        compose.onNodeWithContentDescription("Next chapter").assertIsNotEnabled()
    }

    @Test
    fun booksAreNotRenderedHere() {
        compose.setContent { ReaderPreview(previewReader.copy(chapter = previewReader.chapter?.copy(format = 3))) }
        compose.onNodeWithText("This book opens in the EPUB/PDF reader, which isn't available yet.").assertExists()
    }

    @Test
    fun failedLoadOffersRetry() {
        var retries = 0
        compose.setContent {
            ReaderPreview(
                ImageReaderUiState(
                    error = com.nl.kaviyy.core.model.result.AppError.Network,
                ),
                ImageReaderActions(onRetry = {
                    retries++
                }),
            )
        }
        compose.onNodeWithText("Try Again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun tapZonesFollowTheReadingDirection() {
        val calls = mutableListOf<String>()
        val actions =
            ImageReaderActions(onNextPage = {
                calls += "next"
            }, onPreviousPage = { calls += "prev" }, onToggleChrome = {
                calls +=
                    "chrome"
            })
        TapZones(rightToLeft = false, actions).run {
            onTap(10f, 300f)
            onTap(150f, 300f)
            onTap(290f, 300f)
        }
        TapZones(rightToLeft = true, actions).run {
            onTap(10f, 300f)
            onTap(290f, 300f)
        }
        assertEquals(listOf("prev", "chrome", "next", "next", "prev"), calls)
    }

    @Test
    fun screenshotRtl() = shot("reader_rtl") { ReaderPreview() }

    @Test
    fun screenshotLtr() =
        shot("reader_ltr") {
            ReaderPreview(previewReader.copy(rightToLeft = false, nextChapterId = null))
        }

    @Test
    fun screenshotChromeHidden() = shot("reader_hidden") { ReaderPreview(previewReader.copy(chromeVisible = false)) }
}
