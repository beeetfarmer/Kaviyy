package com.nl.kaviyy.feature.readerimage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
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

    @Test
    fun prefetchWarmsNextPagesThenSpillsIntoNextChapter() {
        val mid = previewReader.copy(page = 10)
        assertEquals(listOf(49 to 11, 49 to 12, 49 to 13), mid.prefetch())
        val end = previewReader.copy(page = 30)
        assertEquals(listOf(49 to 31, 50 to 0, 50 to 1), end.prefetch())
        assertEquals(listOf(49 to 31), end.copy(nextChapterId = null).prefetch())
        // Double page: counts from the end of the visible spread.
        assertEquals(49 to 3, previewReader.copy(page = 1, layout = ReaderLayout.Double).prefetch().first())
    }

    @Test
    fun panIsClampedToTheZoomedContent() {
        val size = IntSize(400, 800)
        assertEquals(Offset.Zero, clampPan(Offset(50f, 50f), 1f, size))
        assertEquals(Offset(200f, -400f), clampPan(Offset(999f, -999f), 2f, size))
    }
}
