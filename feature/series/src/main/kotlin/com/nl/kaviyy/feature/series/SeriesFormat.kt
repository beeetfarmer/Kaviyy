package com.nl.kaviyy.feature.series

import androidx.core.text.HtmlCompat
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.data.volumeNumber
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.SeriesEntity
import kotlin.math.roundToInt

/** Indexed by Kavita `PublicationStatus`. */
private val STATUS_LABELS =
    listOf(
        R.string.series_status_ongoing,
        R.string.series_status_hiatus,
        R.string.series_status_completed,
        R.string.series_status_cancelled,
        R.string.series_status_ended,
    )

/** Indexed by Kavita `AgeRating`; 0 (Unknown) shows nothing. -1 (Not Applicable) falls outside. */
private val AGE_RATING_LABELS =
    listOf(
        null,
        R.string.age_rating_pending,
        R.string.age_rating_early_childhood,
        R.string.age_rating_everyone,
        R.string.age_rating_g,
        R.string.age_rating_everyone_10,
        R.string.age_rating_pg,
        R.string.age_rating_kids_to_adults,
        R.string.age_rating_teen,
        R.string.age_rating_ma15,
        R.string.age_rating_mature_17,
        R.string.age_rating_mature,
        R.string.age_rating_r18,
        R.string.age_rating_adults_only,
        R.string.age_rating_x18,
    )

internal fun publicationStatusLabel(status: Int): Int = STATUS_LABELS.getOrElse(status) { STATUS_LABELS.first() }

internal fun ageRatingLabel(rating: Int): Int? = AGE_RATING_LABELS.getOrNull(rating)

/**
 * Server summaries are HTML written by anyone with edit rights: render as plain text only (tags dropped, entities
 * decoded, no links or images), collapsing runs of blank lines.
 */
internal fun plainSummary(html: String?): String =
    if (html.isNullOrBlank()) {
        ""
    } else {
        HtmlCompat
            // fromHtml keeps the text of <script>/<style> blocks; drop them first.
            .fromHtml(html.orEmpty().replace(HIDDEN_BLOCKS, ""), HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace(OBJECT_CHAR, "") // images become U+FFFC
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

private val HIDDEN_BLOCKS =
    Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private const val OBJECT_CHAR = "\uFFFC"

/** What the primary button does. */
internal sealed interface ReadAction {
    data object Start : ReadAction

    data object ReadAgain : ReadAction

    /** [chapter] = "49", [volume] = "5"; both null for a book/special. */
    data class Continue(
        val chapter: String?,
        val volume: String?,
    ) : ReadAction
}

internal fun readAction(
    series: SeriesEntity,
    next: ChapterEntity?,
): ReadAction =
    when {
        series.pagesRead <= 0 -> ReadAction.Start
        series.pages in 1..series.pagesRead -> ReadAction.ReadAgain
        else -> ReadAction.Continue(next?.number(), next?.volumeNumber())
    }

/** Whole hours, rounded; "<1" handled by the caller via 0. Null when nothing is left. */
internal fun hoursLeft(hours: Float): Int? = if (hours > 0f) hours.roundToInt() else null
