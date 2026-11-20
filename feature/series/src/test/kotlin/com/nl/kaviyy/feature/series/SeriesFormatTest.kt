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
    fun tabsListOnlyWhatTheSeriesHas() {
        val d = previewSeries.detail ?: error("preview")
        assertEquals(listOf(SeriesTab.Volumes, SeriesTab.Chapters, SeriesTab.Specials, SeriesTab.Related), d.tabs())
        val book =
            d.copy(
                volumes = d.volumes.map { it.copy(minNumber = -100_000f) },
                chapters = d.chapters.map { it.copy(range = "-100000", isSpecial = false) },
            )
        assertEquals(listOf(SeriesTab.Related), book.tabs())
        assertEquals(listOf(5, 4, 6), d.realVolumes().map { it.id })
        assertEquals(listOf(90), d.specials().map { it.id })
    }

    @Test
    fun rowStatesSpansAndNextChapter() {
        assertEquals(RowState.Read, rowState(30, 30, asPagesLeft = true))
        assertEquals(RowState.Unread, rowState(30, 0, asPagesLeft = true))
        assertEquals(RowState.PagesLeft(14), rowState(32, 18, asPagesLeft = true))
        assertEquals(RowState.Percent(61), rowState(212, 131, asPagesLeft = false))
        assertEquals(RowState.Percent(1), rowState(1000, 1, asPagesLeft = false)) // never shows 0% when started
        val chapters =
            previewSeries.detail
                ?.chapters
                .orEmpty()
                .filter { !it.isSpecial }
        assertEquals("48–50", chapterSpan(chapters))
        assertEquals("48", chapterSpan(chapters.take(1)))
        assertEquals(null, chapterSpan(emptyList()))
        assertEquals(49, nextIn(chapters)?.id)
        assertEquals(48, nextIn(chapters.take(1))?.id) // all read: start over
    }

    @Test
    fun onlyWebLinksLeaveTheApp() {
        assertEquals("https://anilist.co/manga/1", webUrl(" https://anilist.co/manga/1 "))
        assertEquals("HTTP://x.example", webUrl("HTTP://x.example"))
        assertNull(webUrl("javascript:alert(1)"))
        assertNull(webUrl("intent://x#Intent;end"))
        assertNull(webUrl("file:///data/data/x"))
        assertNull(webUrl(null))
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
