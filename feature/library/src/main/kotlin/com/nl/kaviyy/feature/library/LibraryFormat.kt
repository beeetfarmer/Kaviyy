package com.nl.kaviyy.feature.library

import com.nl.kaviyy.core.database.SeriesEntity
import kotlin.math.roundToInt

/** Kavita `MangaFormat`. */
private const val FORMAT_EPUB = 3
private const val FORMAT_PDF = 4
private const val PERCENT = 100

internal sealed interface Progress {
    data object Unread : Progress

    data object Read : Progress

    data class Partial(
        val percent: Int,
    ) : Progress
}

internal fun SeriesEntity.readProgress(): Progress =
    when {
        pagesRead <= 0 -> Progress.Unread
        pages in 1..pagesRead -> Progress.Read
        else -> Progress.Partial((pagesRead * PERCENT.toFloat() / pages).roundToInt().coerceIn(1, PERCENT - 1))
    }

/** 0..1 for the cover's progress bar, only while partly read. */
internal fun SeriesEntity.progressBar(): Float? =
    (readProgress() as? Progress.Partial)?.let { pagesRead.toFloat() / pages }

/** "EPUB" / "PDF" for book formats (canvas: "EPUB · 42%"); null for image formats. */
internal fun SeriesEntity.bookFormat(): String? =
    when (format) {
        FORMAT_EPUB -> "EPUB"
        FORMAT_PDF -> "PDF"
        else -> null
    }
