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

/** The settings sheet: Kavita profile fields plus this device's display options. */
data class ReaderSettings(
    /** Kavita `ScalingOption`: 0 fit height, 1 fit width, 2 original, 3 automatic. */
    val scaling: Int = SCALING_AUTOMATIC,
    /** Hex, Kavita `backgroundColor`. */
    val background: String = "#000000",
    val swipeToTurn: Boolean = true,
    val autoCloseMenu: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** 0..1; null follows the system. */
    val brightness: Float? = null,
)

const val SCALING_FIT_HEIGHT = 0
const val SCALING_FIT_WIDTH = 1
const val SCALING_ORIGINAL = 2
const val SCALING_AUTOMATIC = 3

private const val PREFETCH_PAGES = 3

/** (chapterId, page) to warm the image cache with: the next pages, spilling into the next chapter. */
internal fun ImageReaderUiState.prefetch(): List<Pair<Int, Int>> {
    val chapter = chapter ?: return emptyList()
    val after = spreads.getOrNull(spreads.indexOfPage(page))?.last() ?: page
    val here = (after + 1 until pages).take(PREFETCH_PAGES).map { chapter.id to it }
    val spill = nextChapterId?.let { next -> List(PREFETCH_PAGES - here.size) { next to it } }.orEmpty()
    return here + spill
}

/** Index of the spread that shows [page]. */
internal fun List<List<Int>>.indexOfPage(page: Int): Int = indexOfFirst { page in it }.coerceAtLeast(0)
