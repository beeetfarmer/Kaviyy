package com.nl.kaviyy.feature.home

import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.model.result.AppError
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeFormatTest {
    private fun ch(
        range: String? = "49",
        volume: String? = "5",
        pages: Int = 20,
        read: Int = 6,
        words: Long = 0,
    ) = ChapterEntity(
        1,
        1,
        1,
        1,
        range = range,
        pages = pages,
        pagesRead = read,
        format = 0,
        wordCount = words,
        volumeTitle = volume,
    )

    @Test
    fun numbersHideKavitaPlaceholders() {
        assertEquals("49", ch().number())
        assertEquals("10.5", ch(range = "10.5").number())
        assertNull(ch(range = "-100000").number())
        assertNull(ch(range = "").number())
        assertEquals("5", ch().volumeNumber())
        assertNull(ch(volume = "-100000").volumeNumber())
        assertNull(ch(volume = "100000").volumeNumber())
        assertNull(ch(volume = "Specials").volumeNumber())
    }

    @Test
    fun timeLeft() {
        assertEquals(14, ch().pagesLeft())
        assertEquals(6, ch().minutesLeft()) // 14 image pages × 25 s
        assertEquals(10, ch(pages = 10, read = 5, words = 5000).minutesLeft()) // 2500 words at 250 wpm
        assertEquals(1, ch(read = 20).minutesLeft())
        assertEquals(0.3f, ch().progress())
        assertEquals(62, percent(0.62f))
    }

    @Test
    fun avatarInitials() {
        assertEquals("AM", initials("Aarav Mehta"))
        assertEquals("AA", initials("aarav"))
        assertEquals("JD", initials("john.doe"))
        assertEquals("", initials(null))
    }

    @Test
    fun offlineStateDerivations() {
        assertEquals(listOf(1, 7), previewOffline.downloadedRail.map { it.seriesId })
        assertEquals(false, previewOffline.isDimmed(1))
        assertEquals(true, previewOffline.isDimmed(2))
        assertEquals(emptyList(), previewState().downloadedRail)
        assertEquals(false, previewState().isDimmed(2))
        assertEquals(false, previewOffline.copy(error = AppError.Network).showStaleNotice)
        assertEquals(true, previewState().copy(error = AppError.Network).showStaleNotice)
    }
}
