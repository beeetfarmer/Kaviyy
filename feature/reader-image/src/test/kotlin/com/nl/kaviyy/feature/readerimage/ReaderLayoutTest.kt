package com.nl.kaviyy.feature.readerimage

import com.nl.kaviyy.core.data.PageSize
import com.nl.kaviyy.core.network.ReadingProfileDto
import org.junit.Test
import kotlin.test.assertEquals

class ReaderLayoutTest {
    @Test
    fun coverAndWidePagesStandAloneOthersPair() {
        val wide = PageSize(1600, 1200, isWide = true)
        val sizes = listOf(null, null, null, wide, null, null, null)
        val s = spreads(7, sizes)
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4, 5), listOf(6)), s)
        assertEquals(3, s.indexOfPage(5))
        assertEquals(0, s.indexOfPage(99))
    }

    @Test
    fun layoutMapsOntoKavitaModesAndKeepsReversedDouble() {
        assertEquals(ReaderLayout.Webtoon, ReadingProfileDto(readerMode = 2, layoutMode = 2).layout)
        assertEquals(ReaderLayout.Double, ReadingProfileDto(layoutMode = 3).layout)
        assertEquals(ReaderLayout.Single, ReadingProfileDto(layoutMode = 1).layout)
        val reversed = ReadingProfileDto(layoutMode = 3)
        assertEquals(3, reversed.withLayout(ReaderLayout.Double).layoutMode)
        assertEquals(2, reversed.withLayout(ReaderLayout.Webtoon).readerMode)
        reversed.withLayout(ReaderLayout.Single).let { assertEquals(0 to 1, it.readerMode to it.layoutMode) }
    }
}
