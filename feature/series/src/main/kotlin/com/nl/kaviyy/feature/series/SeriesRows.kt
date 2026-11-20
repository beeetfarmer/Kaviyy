package com.nl.kaviyy.feature.series

import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.VolumeEntity

/** Kavita's number markers: loose-leaf ("no volume") and the specials volume. */
private const val NO_NUMBER = -100_000f
private const val SPECIAL_NUMBER = 100_000f
private const val PERCENT = 100

enum class SeriesTab { Volumes, Chapters, Specials, Related }

/** Real volumes only (loose-leaf and specials containers are not listed). */
internal fun SeriesDetail.realVolumes(): List<VolumeEntity> =
    volumes.filter {
        it.minNumber != NO_NUMBER &&
            it.minNumber < SPECIAL_NUMBER
    }

internal fun SeriesDetail.numberedChapters(): List<ChapterEntity> =
    chapters.filter {
        !it.isSpecial &&
            it.number() != null
    }

internal fun SeriesDetail.specials(): List<ChapterEntity> = chapters.filter { it.isSpecial }

/** Tabs with something in them; Related is always offered (it loads on open). */
internal fun SeriesDetail.tabs(): List<SeriesTab> =
    listOfNotNull(
        SeriesTab.Volumes.takeIf { realVolumes().isNotEmpty() },
        SeriesTab.Chapters.takeIf { numberedChapters().isNotEmpty() },
        SeriesTab.Specials.takeIf { specials().isNotEmpty() },
        SeriesTab.Related,
    )

/** Right-hand state on a row. */
internal sealed interface RowState {
    data object Read : RowState

    data object Unread : RowState

    data class Percent(
        val value: Int,
    ) : RowState

    data class PagesLeft(
        val value: Int,
    ) : RowState
}

internal fun rowState(
    pages: Int,
    pagesRead: Int,
    asPagesLeft: Boolean,
): RowState =
    when {
        pages > 0 && pagesRead >= pages -> RowState.Read
        pagesRead <= 0 -> RowState.Unread
        asPagesLeft -> RowState.PagesLeft(pages - pagesRead)
        else -> RowState.Percent((pagesRead * PERCENT / pages.coerceAtLeast(1)).coerceIn(1, PERCENT - 1))
    }

/** "41–50" (or "41") from the volume's numbered chapters; null when none are numbered. */
internal fun chapterSpan(chapters: List<ChapterEntity>): String? {
    val numbers = chapters.filterNot { it.isSpecial }.mapNotNull { it.number() }
    if (numbers.isEmpty()) return null
    val first = numbers.first()
    val last = numbers.last()
    return if (first == last) first else "$first–$last"
}

/** First chapter to read in [chapters]: the first not finished, else the first. */
internal fun nextIn(chapters: List<ChapterEntity>): ChapterEntity? =
    chapters.firstOrNull { it.pages == 0 || it.pagesRead < it.pages } ?: chapters.firstOrNull()
