package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ChapterEntity
import kotlin.math.ceil

/** Kavita's "no number" marker for loose-leaf volumes / volume-only chapters. */
private const val NO_NUMBER = -100_000f
private const val SPECIAL_VOLUME = 100_000f
private const val SECONDS_PER_IMAGE_PAGE = 25.0
private const val WORDS_PER_MINUTE = 250.0
private const val SECONDS_PER_MINUTE = 60.0

/** "49" for a numbered chapter, else null. */
fun ChapterEntity.number(): String? =
    range?.takeIf { r -> r.isNotBlank() && r.toFloatOrNull()?.let { it != NO_NUMBER && it >= 0 } ?: true }

/** "5" for a real volume number, else null (loose leaf / specials). */
fun ChapterEntity.volumeNumber(): String? =
    volumeTitle?.takeIf { v -> v.toFloatOrNull()?.let { it > 0 && it < SPECIAL_VOLUME } == true }

fun ChapterEntity.displayTitle(): String? = titleName ?: title?.takeIf { it.isNotBlank() }

fun ChapterEntity.pagesLeft(): Int = (pages - pagesRead).coerceAtLeast(0)

/** Rough minutes left: words at 250 wpm for books, else ~25 s per image page. At least 1. */
fun ChapterEntity.minutesLeft(): Int {
    val left = pagesLeft()
    val minutes =
        if (wordCount > 0 && pages > 0) {
            wordCount * (left.toDouble() / pages) / WORDS_PER_MINUTE
        } else {
            left * SECONDS_PER_IMAGE_PAGE / SECONDS_PER_MINUTE
        }
    return ceil(minutes).toInt().coerceAtLeast(1)
}

fun ChapterEntity.progress(): Float = if (pages > 0) (pagesRead.toFloat() / pages).coerceIn(0f, 1f) else 0f
