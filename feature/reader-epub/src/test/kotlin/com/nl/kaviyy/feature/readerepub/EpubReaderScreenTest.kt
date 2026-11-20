package com.nl.kaviyy.feature.readerepub

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.nl.kaviyy.core.model.result.AppError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class EpubReaderScreenTest {
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
    fun chromeShowsTitleProgressAndActions() {
        val calls = mutableListOf<String>()
        compose.setContent {
            BookPreview(
                actions =
                    EpubReaderActions(
                        onClose = { calls += "close" },
                        onNext = { calls += "next" },
                        onPrevious = { calls += "previous" },
                        onShowSettings = { calls += "settings $it" },
                    ),
            )
        }
        compose.onNodeWithText("The Lantern Keeper").assertExists()
        compose.onNodeWithText("42% · page 112 of 268").assertExists()
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithContentDescription("Previous page").performClick()
        compose.onNodeWithContentDescription("Text settings").performClick()
        compose.onNodeWithContentDescription("Close book").performClick()
        assertEquals(listOf("next", "previous", "settings true", "close"), calls)
    }

    @Test
    fun missingDownloadRoleExplainsWithoutRetry() {
        compose.setContent { BookPreview(EpubReaderUiState(error = AppError.Forbidden)) }
        compose.onNodeWithText("Download permission", substring = true).assertExists()
        compose.onNodeWithText("Try Again").assertDoesNotExist()
    }

    @Test
    fun aaSheetChangesThemeSizeAndFont() {
        val picked = mutableListOf<BookSettings>()
        compose.setContent {
            BookPreview(
                previewBook.copy(settingsOpen = true),
                EpubReaderActions(onSettings = {
                    picked +=
                        it
                }),
            )
        }
        compose.onNodeWithText("Night").performClick()
        compose.onNodeWithContentDescription("Increase Text size").performClick()
        compose.onNodeWithText("Inter").performClick()
        compose.onNodeWithText("Scroll").performClick()
        assertEquals(
            listOf(
                BookSettings(theme = BookTheme.Night),
                BookSettings(fontSize = 110),
                BookSettings(font = BookFont.Inter),
                BookSettings(scroll = true),
            ),
            picked,
        )
    }

    @Test
    fun screenshotPaper() = shot("epub_paper") { BookPreview() }

    @Test
    fun screenshotNight() =
        shot("epub_night") {
            BookPreview(previewBook.copy(settings = BookSettings(theme = BookTheme.Night)))
        }

    @Test
    fun screenshotSettings() = shot("epub_settings") { BookPreview(previewBook.copy(settingsOpen = true)) }
}
