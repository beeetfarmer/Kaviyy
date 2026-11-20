package com.nl.kaviyy.feature.readerepub

import androidx.compose.ui.graphics.toArgb
import com.nl.kaviyy.core.data.AnnotationDraft
import com.nl.kaviyy.core.data.Highlight
import com.nl.kaviyy.core.designsystem.theme.HighlightColors
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class BookHighlightsTest {
    @Test
    fun aSelectionBecomesADraftOnItsSpinePage() {
        val selection = at("ch2.xhtml", 0.3).copy(text = Locator.Text(highlight = "  the lantern \n"))
        assertEquals(AnnotationDraft(1, "the lantern", "//body", 2, "n"), testBook.draft(selection, 2, "n"))
    }

    @Test
    fun highlightsAreDrawnByPageAndTextInTheirColour() {
        val marks =
            listOf(
                Highlight("a", 2, "Three", 1, null),
                Highlight("b", 0, "One", 3, "note"),
                Highlight("gone", 9, "Nowhere", 0, null), // page no longer in the book: skipped
            )
        val decorations = testBook.decorations(marks)
        assertEquals(listOf("a", "b"), decorations.map { it.id })
        val first = decorations[0]
        assertEquals("ch3.xhtml" to "Three", first.locator.href.toString() to first.locator.text.highlight)
        assertEquals(
            listOf(HighlightColors.green.toArgb() to false, HighlightColors.pink.toArgb() to true),
            decorations.map { (it.style as Decoration.Style.Highlight).let { s -> s.tint to s.isActive } },
        )
    }
}
