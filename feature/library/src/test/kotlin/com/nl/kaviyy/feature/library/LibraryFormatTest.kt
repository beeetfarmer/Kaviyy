package com.nl.kaviyy.feature.library

import com.nl.kaviyy.core.database.SeriesEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryFormatTest {
    private fun s(
        read: Int,
        pages: Int = 100,
        format: Int = 0,
    ) = SeriesEntity(1, 1, 1, "a", "a", format = format, pages = pages, pagesRead = read)

    @Test
    fun progress() {
        assertEquals(Progress.Unread, s(0).readProgress())
        assertEquals(Progress.Read, s(100).readProgress())
        assertEquals(Progress.Partial(42), s(42).readProgress())
        assertEquals(Progress.Partial(1), s(1, pages = 1000).readProgress()) // never shows 0% when started
        assertEquals(Progress.Partial(99), s(999, pages = 1000).readProgress()) // nor 100% when not finished
        assertEquals(0.42f, s(42).progressBar())
        assertNull(s(0).progressBar())
        assertNull(s(100).progressBar())
    }

    @Test
    fun formats() {
        assertEquals("EPUB", s(0, format = 3).bookFormat())
        assertEquals("PDF", s(0, format = 4).bookFormat())
        assertNull(s(0, format = 1).bookFormat())
    }
}
