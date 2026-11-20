package com.nl.kaviyy.feature.filter

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nl.kaviyy.core.data.Comparison
import com.nl.kaviyy.core.data.FieldKind
import com.nl.kaviyy.core.data.FilterField
import com.nl.kaviyy.core.data.FilterRule
import com.nl.kaviyy.core.data.RuleValue
import com.nl.kaviyy.core.data.SeriesSort

@StringRes
internal fun FilterField.label(): Int =
    when (this) {
        FilterField.SeriesName -> R.string.filter_field_series_name
        FilterField.Summary -> R.string.filter_field_summary
        FilterField.Genres -> R.string.filter_field_genres
        FilterField.Tags -> R.string.filter_field_tags
        FilterField.PublicationStatus -> R.string.filter_field_publication_status
        FilterField.AgeRating -> R.string.filter_field_age_rating
        FilterField.Languages -> R.string.filter_field_languages
        FilterField.Formats -> R.string.filter_field_formats
        FilterField.CollectionTags -> R.string.filter_field_collection_tags
        FilterField.ReadProgress -> R.string.filter_field_read_progress
        FilterField.WantToRead -> R.string.filter_field_want_to_read
        FilterField.UserRating -> R.string.filter_field_user_rating
        FilterField.AverageRating -> R.string.filter_field_average_rating
        FilterField.ReleaseYear -> R.string.filter_field_release_year
        FilterField.ReadTime -> R.string.filter_field_read_time
        FilterField.ReadingDate -> R.string.filter_field_reading_date
        FilterField.ReadLast -> R.string.filter_field_read_last
        FilterField.FileSize -> R.string.filter_field_file_size
        FilterField.Writers -> R.string.filter_field_writers
        FilterField.Penciller -> R.string.filter_field_penciller
        FilterField.Inker -> R.string.filter_field_inker
        FilterField.Colorist -> R.string.filter_field_colorist
        FilterField.Letterer -> R.string.filter_field_letterer
        FilterField.CoverArtist -> R.string.filter_field_cover_artist
        FilterField.Editor -> R.string.filter_field_editor
        FilterField.Publisher -> R.string.filter_field_publisher
        FilterField.Characters -> R.string.filter_field_characters
        FilterField.Translators -> R.string.filter_field_translators
        FilterField.Imprint -> R.string.filter_field_imprint
        FilterField.Team -> R.string.filter_field_team
        FilterField.Location -> R.string.filter_field_location
        FilterField.Path -> R.string.filter_field_path
        FilterField.FilePath -> R.string.filter_field_file_path
    }

/** Canvas wording ("is any of", "less than", "after", "at most"), per kind. */
@StringRes
internal fun Comparison.label(kind: FieldKind): Int {
    val year = kind == FieldKind.Year
    return when (this) {
        Comparison.Matches -> R.string.filter_op_contains_text
        Comparison.Equal -> R.string.filter_op_is
        Comparison.NotEqual -> R.string.filter_op_is_not
        Comparison.BeginsWith -> R.string.filter_op_starts_with
        Comparison.EndsWith -> R.string.filter_op_ends_with
        Comparison.GreaterThan -> if (year) R.string.filter_op_after else R.string.filter_op_more_than
        Comparison.GreaterThanEqual -> if (year) R.string.filter_op_in_or_after else R.string.filter_op_at_least
        Comparison.LessThan -> if (year) R.string.filter_op_before else R.string.filter_op_less_than
        Comparison.LessThanEqual -> if (year) R.string.filter_op_in_or_before else R.string.filter_op_at_most
        Comparison.IsAfter -> R.string.filter_op_after
        Comparison.IsBefore -> R.string.filter_op_before
        Comparison.IsInLast -> R.string.filter_op_in_last
        Comparison.IsNotInLast -> R.string.filter_op_not_in_last
        Comparison.Contains -> R.string.filter_op_any_of
        Comparison.MustContains -> R.string.filter_op_all_of
        Comparison.NotContains -> R.string.filter_op_none_of
    }
}

@StringRes
internal fun SeriesSort.label(): Int =
    when (this) {
        SeriesSort.RecentlyAdded -> R.string.filter_sort_recently_added
        SeriesSort.RecentlyUpdated -> R.string.filter_sort_recently_updated
        SeriesSort.Title -> R.string.filter_sort_title
        SeriesSort.ReleaseYear -> R.string.filter_sort_release_year
        SeriesSort.ReadProgress -> R.string.filter_sort_read_progress
        SeriesSort.UserRating -> R.string.filter_sort_user_rating
    }

/** Kavita `MangaFormat` choices (Unknown omitted). */
@Composable
internal fun formatOptions(): List<RuleValue> =
    listOf(
        RuleValue("0", stringResource(R.string.filter_format_images)),
        RuleValue("1", stringResource(R.string.filter_format_archive)),
        RuleValue("3", stringResource(R.string.filter_format_epub)),
        RuleValue("4", stringResource(R.string.filter_format_pdf)),
    )

@Composable
internal fun booleanOptions(): List<RuleValue> =
    listOf(
        RuleValue("true", stringResource(R.string.filter_yes)),
        RuleValue("false", stringResource(R.string.filter_no)),
    )

/** The rule's value line: "Fantasy, Adventure", "100%", "30 days", "2015". */
@Composable
internal fun FilterRule.valueLabel(): String {
    val joined = values.joinToString { it.label }
    val inLast = comparison == Comparison.IsInLast || comparison == Comparison.IsNotInLast
    return when {
        field.kind == FieldKind.Percent -> stringResource(R.string.filter_value_percent, joined)
        field.kind == FieldKind.Date && inLast -> stringResource(R.string.filter_value_days, joined)
        else -> joined
    }
}

@StringRes
internal fun DraftError.label(): Int =
    when (this) {
        DraftError.Empty -> R.string.filter_error_empty
        DraftError.NotANumber -> R.string.filter_error_number
        DraftError.OutOfRange -> R.string.filter_error_range
        DraftError.NotADate -> R.string.filter_error_date
    }
