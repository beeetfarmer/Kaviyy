package com.nl.kaviyy.feature.readerepub

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
    fun tocSheetJumpsToAnEntry() {
        val went = mutableListOf<String>()
        compose.setContent {
            BookPreview(
                previewBook.copy(
                    publication = testBook,
                    toc = testBook.toc(),
                    tocOpen = true,
                    locator = at("ch3.xhtml", 0.1),
                ),
                EpubReaderActions(onGo = { went += it.title }),
            )
        }
        compose.onNodeWithText("Three").assertIsSelected()
        compose.onNodeWithText("One A").performClick()
        assertEquals(listOf("One A"), went)
    }

    @Test
    fun toolbarActsOnTheSelection() {
        val calls = mutableListOf<String>()
        compose.setContent {
            BookPreview(
                previewBook.copy(highlightTarget = HighlightTarget("The lantern", top = 420f, bottom = 480f)),
                EpubReaderActions(onPickColor = { calls += "color $it" }, onNote = { calls += "note" }),
            )
        }
        compose.onNodeWithContentDescription("Blue").performClick()
        compose.onNodeWithText("Note").performClick()
        assertEquals(listOf("color 2", "note"), calls)
    }

    @Test
    fun highlightsTabListsAndJumps() {
        val went = mutableListOf<String>()
        compose.setContent {
            BookPreview(
                previewBook.copy(
                    publication = testBook,
                    toc = testBook.toc(),
                    tocOpen = true,
                    highlights = previewHighlights,
                ),
                EpubReaderActions(onGoHighlight = { went += it.uuid }),
            )
        }
        compose.onNodeWithText("Highlights").performClick()
        compose.onNodeWithText("The theme of the whole book.").assertExists()
        compose.onNodeWithText("The lantern never went out.").performClick()
        assertEquals(listOf("a"), went)
    }

    @Test
    fun noteSheetSavesTheText() {
        val saved = mutableListOf<String>()
        compose.setContent {
            BookPreview(
                previewBook.copy(noteEditor = NoteEditor("old")),
                EpubReaderActions(onSaveNote = { saved += it }),
            )
        }
        compose.onNodeWithContentDescription("Note").performTextReplacement("new note")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("new note"), saved)
    }

    @Test
    fun screenshotHighlightToolbar() =
        shot("epub_highlight_toolbar") {
            BookPreview(
                previewBook.copy(
                    chromeVisible = false,
                    highlightTarget = HighlightTarget("The lantern", top = 420f, bottom = 480f, uuid = "a", color = 0),
                ),
            )
        }

    @Test
    fun screenshotHighlightToolbarNight() =
        shot("epub_highlight_toolbar_night") {
            BookPreview(
                previewBook.copy(
                    settings = BookSettings(theme = BookTheme.Night),
                    chromeVisible = false,
                    highlightTarget = HighlightTarget("The lantern", top = 420f, bottom = 480f),
                ),
            )
        }

    @Test
    fun screenshotNote() = shot("epub_note") { BookPreview(previewBook.copy(noteEditor = NoteEditor("Mara again."))) }

    @Test
    fun screenshotToc() =
        shot("epub_toc") {
            BookPreview(
                previewBook.copy(
                    publication = testBook,
                    toc = testBook.toc(),
                    tocOpen = true,
                    locator = at("ch3.xhtml", 0.1),
                ),
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
