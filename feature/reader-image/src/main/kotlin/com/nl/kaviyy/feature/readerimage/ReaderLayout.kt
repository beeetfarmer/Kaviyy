package com.nl.kaviyy.feature.readerimage

import com.nl.kaviyy.core.data.PageSize
import com.nl.kaviyy.core.network.ReadingProfileDto

/** The canvas radiogroup. Maps onto Kavita's `readerMode` (2 = Webtoon) and `layoutMode` (1 single, 2/3 double). */
enum class ReaderLayout { Single, Double, Webtoon }

private const val MODE_PAGED = 0
private const val MODE_WEBTOON = 2
private const val LAYOUT_SINGLE = 1
private const val LAYOUT_DOUBLE = 2
private const val LAYOUT_DOUBLE_REVERSED = 3

internal val ReadingProfileDto.layout: ReaderLayout
    get() =
        when {
            readerMode == MODE_WEBTOON -> ReaderLayout.Webtoon
            layoutMode == LAYOUT_DOUBLE || layoutMode == LAYOUT_DOUBLE_REVERSED -> ReaderLayout.Double
            else -> ReaderLayout.Single
        }

/** Kavita's "Double (Manga)": the two pages of a pair swap sides. */
internal val ReadingProfileDto.reversedPairs: Boolean get() = layoutMode == LAYOUT_DOUBLE_REVERSED

internal fun ReadingProfileDto.withLayout(layout: ReaderLayout): ReadingProfileDto =
    when (layout) {
        ReaderLayout.Webtoon -> {
            copy(readerMode = MODE_WEBTOON)
        }

        // Keep the reversed variant if the user picked it on the web.
        ReaderLayout.Double -> {
            copy(
                readerMode = MODE_PAGED,
                layoutMode = if (reversedPairs) LAYOUT_DOUBLE_REVERSED else LAYOUT_DOUBLE,
            )
        }

        ReaderLayout.Single -> {
            copy(readerMode = MODE_PAGED, layoutMode = LAYOUT_SINGLE)
        }
    }

/** Double page spreads: the cover alone, wide pages (already spreads) alone, everything else in pairs. */
internal fun spreads(
    pages: Int,
    sizes: List<PageSize?>,
): List<List<Int>> {
    fun wide(i: Int) = sizes.getOrNull(i)?.isWide == true
    val out = mutableListOf<List<Int>>()
    var i = 0
    while (i < pages) {
        val pair = i > 0 && !wide(i) && i + 1 < pages && !wide(i + 1)
        out += if (pair) listOf(i, i + 1) else listOf(i)
        i += if (pair) 2 else 1
    }
    return out
}

/** Index of the spread that shows [page]. */
internal fun List<List<Int>>.indexOfPage(page: Int): Int = indexOfFirst { page in it }.coerceAtLeast(0)
