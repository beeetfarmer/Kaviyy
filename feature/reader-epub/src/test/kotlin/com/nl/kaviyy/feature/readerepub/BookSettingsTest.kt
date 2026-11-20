package com.nl.kaviyy.feature.readerepub

import com.nl.kaviyy.core.data.ReaderDisplay
import com.nl.kaviyy.core.network.ReadingProfileDto
import org.junit.Test
import kotlin.test.assertEquals

class BookSettingsTest {
    @Test
    fun kavitaProfileMapsOntoTheSheet() {
        val s =
            bookSettings(
                ReadingProfileDto(bookReaderThemeName = "Black", bookReaderFontSize = 999, bookReaderLayoutMode = 0),
                ReaderDisplay(bookFont = "inter"),
            )
        assertEquals(BookTheme.Night, s.theme)
        assertEquals(FONT_SIZE_MAX, s.fontSize)
        assertEquals(true, s.scroll)
        assertEquals(BookFont.Inter, s.font)
        assertEquals(BookTheme.Paper, BookTheme.fromKavita("Something custom"))
        assertEquals(BookFont.Literata, BookFont.fromKey("comic sans"))
    }

    @Test
    fun writingBackKeepsTwoColumnsAndOnlyTouchesBookFields() {
        val web = ReadingProfileDto(bookReaderLayoutMode = 2, readingDirection = 1)
        val paged = web.with(BookSettings(theme = BookTheme.Night, scroll = false, margin = 20))
        assertEquals(2, paged.bookReaderLayoutMode)
        assertEquals("Dark", paged.bookReaderThemeName)
        assertEquals(20, paged.bookReaderMargin)
        assertEquals(1, paged.readingDirection) // image reader field untouched
        assertEquals(0, web.with(BookSettings(scroll = true)).bookReaderLayoutMode)
        assertEquals(
            1,
            ReadingProfileDto(bookReaderLayoutMode = 0).with(BookSettings(scroll = false)).bookReaderLayoutMode,
        )
    }

    @Test
    fun readiumPreferencesFollowTheSettings() {
        val p = BookSettings(fontSize = 120, lineSpacing = 100, margin = 30, scroll = true).toPreferences()
        assertEquals(1.2, p.fontSize)
        assertEquals(1.7, p.lineHeight)
        assertEquals(2.0, p.pageMargins)
        assertEquals(true, p.scroll)
        assertEquals(false, p.publisherStyles)
    }
}
