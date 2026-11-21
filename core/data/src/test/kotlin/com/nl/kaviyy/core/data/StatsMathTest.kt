package com.nl.kaviyy.core.data

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

class StatsMathTest {
    @Test
    fun currentStreakToleratesNothingReadYetToday() {
        assertEquals(Streaks(current = 2, longest = 3), streaks(listOf(1, 1, 1, 0, 2, 5, 0)))
        assertEquals(Streaks(current = 3, longest = 3), streaks(listOf(0, 1, 1, 1)))
        assertEquals(Streaks(current = 0, longest = 1), streaks(listOf(1, 0, 0)))
        assertEquals(Streaks(0, 0), streaks(emptyList()))
    }

    @Test
    fun dailyAndMonthlyTotalsFillGapsOldestFirst() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals(listOf(0L, 4L, 2L), dailyTotals(mapOf(today to 2L, today.minusDays(1) to 4L), today, 3))
        val nov = YearMonth.of(2026, 11)
        assertEquals(
            listOf(YearMonth.of(2026, 9) to 0L, YearMonth.of(2026, 10) to 5L, nov to 1L),
            monthlyTotals(mapOf(nov to 1L, YearMonth.of(2026, 10) to 5L, YearMonth.of(2025, 1) to 9L), nov, 3),
        )
    }

    @Test
    fun mangaFormatsGroupIntoImagesEpubPdf() {
        assertEquals(
            listOf(ReadFormat.Images, ReadFormat.Images, ReadFormat.Images, ReadFormat.Epub, ReadFormat.Pdf),
            (0..4).map(::readFormat),
        )
    }
}
