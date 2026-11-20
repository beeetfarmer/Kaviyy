package com.nl.kaviyy.feature.series

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.SeriesEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SeriesFormatTest {
    private fun series(read: Int) = SeriesEntity(1, 7, 1, "S", "s", format = 0, pages = 100, pagesRead = read)

    @Test
    fun summaryIsPlainTextWithoutMarkupOrScripts() {
        val html =
            "<p>Fire &amp; <b>ash</b></p><script>alert(1)</script><img src=\"x\" onerror=\"y\">" +
                "<p><a href=\"javascript:z\">road</a></p>"
        val text = plainSummary(html)
        assertEquals(false, text.contains('<'))
        assertEquals(false, text.contains("alert"))
        assertEquals(true, text.startsWith("Fire & ash"))
        assertEquals(true, text.contains("road"))
        assertEquals("", plainSummary(null))
    }

    @Test
    fun readActionFollowsProgressAndContinuePoint() {
        val ch = ChapterEntity(1, 49, 5, 7, range = "49", pages = 30, pagesRead = 4, format = 0, volumeTitle = "5")
        assertEquals(ReadAction.Start, readAction(series(0), ch))
        assertEquals(ReadAction.ReadAgain, readAction(series(100), ch))
        assertEquals(ReadAction.Continue("49", "5"), readAction(series(40), ch))
        val volumeOnly = ch.copy(range = "-100000")
        assertEquals(ReadAction.Continue(null, "5"), readAction(series(40), volumeOnly))
        assertEquals(ReadAction.Continue(null, null), readAction(series(40), null))
    }

    @Test
    fun labelsAndHours() {
        assertNull(ageRatingLabel(0))
        assertNull(ageRatingLabel(-1))
        assertEquals(R.string.age_rating_teen, ageRatingLabel(8))
        assertEquals(R.string.series_status_completed, publicationStatusLabel(2))
        assertEquals(38, hoursLeft(37.6f))
        assertEquals(0, hoursLeft(0.3f))
        assertNull(hoursLeft(0f))
    }
}
