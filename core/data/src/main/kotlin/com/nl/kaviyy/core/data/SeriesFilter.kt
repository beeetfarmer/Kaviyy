package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.network.SeriesFilterStatementDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.SeriesSortOptionDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Sort menu: Kavita `SeriesSortField` + direction. Default matches the canvas ("Recently Added"). */
@Serializable
enum class SeriesSort(
    val field: Int,
    val ascending: Boolean,
) {
    RecentlyAdded(2, false),
    RecentlyUpdated(4, false),
    Title(1, true),
    ReleaseYear(6, false),
    ReadProgress(7, false),
    UserRating(10, false),
}

/** Kavita `FilterComparison`. */
@Serializable
enum class Comparison(
    val api: Int,
) {
    Equal(0),
    GreaterThan(1),
    GreaterThanEqual(2),
    LessThan(3),
    LessThanEqual(4),
    Contains(5),
    MustContains(6),
    Matches(7),
    NotContains(8),
    NotEqual(9),
    BeginsWith(10),
    EndsWith(11),
    IsBefore(12),
    IsAfter(13),
    IsInLast(14),
    IsNotInLast(15),
}

private val NUMERIC =
    listOf(
        Comparison.Equal,
        Comparison.NotEqual,
        Comparison.GreaterThan,
        Comparison.GreaterThanEqual,
        Comparison.LessThan,
        Comparison.LessThanEqual,
    )

/** How a field's value is entered, and which comparisons Kavita accepts for it. */
enum class FieldKind(
    val comparisons: List<Comparison>,
) {
    Text(listOf(Comparison.Matches, Comparison.Equal, Comparison.NotEqual, Comparison.BeginsWith, Comparison.EndsWith)),
    Number(NUMERIC),
    Percent(NUMERIC),
    Year(NUMERIC),

    /** IsBefore/IsAfter take a yyyy-MM-dd date; IsInLast/IsNotInLast take a number of days. */
    Date(listOf(Comparison.IsAfter, Comparison.IsBefore, Comparison.IsInLast, Comparison.IsNotInLast)),
    Boolean(listOf(Comparison.Equal)),
    MultiOptions(listOf(Comparison.Contains, Comparison.MustContains, Comparison.NotContains)),
    SingleOptions(listOf(Comparison.Contains, Comparison.NotContains)),

    /** Age rating: also "at most / at least" one value. */
    OrderedOptions(
        listOf(Comparison.Contains, Comparison.NotContains, Comparison.LessThanEqual, Comparison.GreaterThanEqual),
    ),
}

/** Where an options field's values come from. [role] = Kavita `PersonRole` for people. */
enum class OptionSource(
    val role: Int = 0,
) {
    Genres,
    Tags,
    Languages,
    AgeRatings,
    PublicationStatus,
    Formats,
    Collections,
    Writer(3),
    Penciller(4),
    Inker(5),
    Colorist(6),
    Letterer(7),
    CoverArtist(8),
    Editor(9),
    Publisher(10),
    Character(11),
    Translator(12),
    Imprint(13),
    Team(14),
    Location(15),
}

/** Kavita `SeriesFilterField`: every field the API filters on (api/openapi.json). */
@Serializable
enum class FilterField(
    val api: Int,
    val kind: FieldKind,
    val source: OptionSource? = null,
) {
    SeriesName(1, FieldKind.Text),
    Summary(0, FieldKind.Text),
    Genres(18, FieldKind.MultiOptions, OptionSource.Genres),
    Tags(6, FieldKind.MultiOptions, OptionSource.Tags),
    PublicationStatus(2, FieldKind.SingleOptions, OptionSource.PublicationStatus),
    AgeRating(4, FieldKind.OrderedOptions, OptionSource.AgeRatings),
    Languages(3, FieldKind.SingleOptions, OptionSource.Languages),
    Formats(21, FieldKind.SingleOptions, OptionSource.Formats),
    CollectionTags(7, FieldKind.MultiOptions, OptionSource.Collections),
    ReadProgress(20, FieldKind.Percent),
    WantToRead(26, FieldKind.Boolean),
    UserRating(5, FieldKind.Number),
    AverageRating(28, FieldKind.Number),
    ReleaseYear(22, FieldKind.Year),
    ReadTime(23, FieldKind.Number),
    ReadingDate(27, FieldKind.Date),
    ReadLast(32, FieldKind.Number),
    FileSize(33, FieldKind.Number),
    Writers(17, FieldKind.MultiOptions, OptionSource.Writer),
    Penciller(16, FieldKind.MultiOptions, OptionSource.Penciller),
    Inker(15, FieldKind.MultiOptions, OptionSource.Inker),
    Colorist(14, FieldKind.MultiOptions, OptionSource.Colorist),
    Letterer(13, FieldKind.MultiOptions, OptionSource.Letterer),
    CoverArtist(12, FieldKind.MultiOptions, OptionSource.CoverArtist),
    Editor(11, FieldKind.MultiOptions, OptionSource.Editor),
    Publisher(10, FieldKind.MultiOptions, OptionSource.Publisher),
    Characters(9, FieldKind.MultiOptions, OptionSource.Character),
    Translators(8, FieldKind.MultiOptions, OptionSource.Translator),
    Imprint(29, FieldKind.MultiOptions, OptionSource.Imprint),
    Team(30, FieldKind.MultiOptions, OptionSource.Team),
    Location(31, FieldKind.MultiOptions, OptionSource.Location),
    Path(24, FieldKind.Text),
    FilePath(25, FieldKind.Text),
    ;

    /** Library scope is its own row, not a rule. */
    companion object {
        const val LIBRARIES_API = 19
    }
}

/** One value: [code] is sent to the server (id, iso code, number, text), [label] is shown. */
@Serializable
data class RuleValue(
    val code: String,
    val label: String = code,
)

@Serializable
data class FilterRule(
    val field: FilterField,
    val comparison: Comparison,
    val values: List<RuleValue>,
) {
    val isComplete: Boolean get() = values.any { it.code.isNotBlank() } && comparison in this.field.kind.comparisons

    fun toStatement() = SeriesFilterStatementDto(comparison.api, field.api, values.joinToString(",") { it.code.trim() })
}

/** The Library grid's filter (screen 09). The chips are shortcuts for common [rules]. */
@Serializable
data class SeriesFilter(
    val matchAll: Boolean = true,
    val rules: List<FilterRule> = emptyList(),
    val sort: SeriesSort = SeriesSort.RecentlyAdded,
    /** Scope; empty = the library picked in the segmented control. */
    val libraryIds: List<Int> = emptyList(),
) {
    val activeRules: Int get() = rules.count { it.isComplete }

    /**
     * Kavita has no separate scope: libraries are a statement like any other, so with "Any rule" they widen the
     * match exactly as on Kavita's web UI.
     */
    fun toDto(scope: List<Int>): SeriesFilterV2Dto {
        val libraries = libraryIds.ifEmpty { scope }
        val statements =
            listOfNotNull(
                libraries.takeIf { it.isNotEmpty() }?.let {
                    SeriesFilterStatementDto(Comparison.Contains.api, FilterField.LIBRARIES_API, it.joinToString(","))
                },
            ) + rules.filter { it.isComplete }.map { it.toStatement() }
        return SeriesFilterV2Dto(
            statements = statements,
            combination = if (matchAll) AND else OR,
            sortOptions = SeriesSortOptionDto(sort.field, sort.ascending),
        )
    }

    fun encode(): String = json.encodeToString(this)

    companion object {
        private const val AND = 1
        private const val OR = 0
        private val json = Json { ignoreUnknownKeys = true }

        /** Null when [value] isn't a filter this version understands. */
        fun decode(value: String): SeriesFilter? =
            runCatching { json.decodeFromString<SeriesFilter>(value) }.getOrNull()
    }
}

/** One library grid query. [key] names its cached rows. */
data class LibraryQuery(
    val libraryId: Int,
    val filter: SeriesFilter = SeriesFilter(),
) {
    val key: String get() = "lib=$libraryId;${filter.encode()}"

    fun toFilter(): SeriesFilterV2Dto = filter.toDto(listOf(libraryId))
}
