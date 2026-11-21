package com.nl.kaviyy.core.data

import java.time.LocalDate
import java.time.YearMonth

/** What a reading event was read as: Kavita's image formats vs books. */
enum class ReadFormat { Images, Epub, Pdf }

/** Kavita `MangaFormat`: 3 Epub, 4 Pdf; Image/Archive/Unknown are image chapters. */
fun readFormat(mangaFormat: Int): ReadFormat =
    when (mangaFormat) {
        MANGA_FORMAT_EPUB -> ReadFormat.Epub
        MANGA_FORMAT_PDF -> ReadFormat.Pdf
        else -> ReadFormat.Images
    }

/** Reading-day streaks over [days] (oldest first, last = today). */
data class Streaks(
    val current: Int,
    val longest: Int,
)

/**
 * Current streak counts back from today, or from yesterday when nothing was read yet today (the day isn't over).
 * Longest is the longest run of reading days in the window.
 */
fun streaks(days: List<Long>): Streaks {
    var longest = 0
    var run = 0
    days.forEach { count ->
        run = if (count > 0) run + 1 else 0
        longest = maxOf(longest, run)
    }
    val fromEnd = if (days.lastOrNull() == 0L) days.dropLast(1) else days
    val current = fromEnd.asReversed().takeWhile { it > 0 }.size
    return Streaks(current, longest)
}

/** Per-day totals for the [size] days ending on [today], oldest first; days with no entry are 0. */
fun dailyTotals(
    counts: Map<LocalDate, Long>,
    today: LocalDate,
    size: Int,
): List<Long> = (size - 1 downTo 0).map { counts[today.minusDays(it.toLong())] ?: 0L }

/** The [size] months ending with [current], oldest first, missing months as 0. */
fun monthlyTotals(
    counts: Map<YearMonth, Long>,
    current: YearMonth,
    size: Int,
): List<Pair<YearMonth, Long>> =
    (size - 1 downTo 0).map { current.minusMonths(it.toLong()) }.map { it to (counts[it] ?: 0L) }

private const val MANGA_FORMAT_EPUB = 3
private const val MANGA_FORMAT_PDF = 4
