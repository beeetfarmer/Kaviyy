package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.network.SeriesFilterStatementDto
import com.nl.kaviyy.core.network.SeriesFilterV2Dto
import com.nl.kaviyy.core.network.SeriesSortOptionDto

/** Library sort menu: Kavita `SeriesSortField` + direction. Default matches the canvas ("Recently Added"). */
enum class SeriesSort(
    internal val field: Int,
    internal val ascending: Boolean,
) {
    RecentlyAdded(2, false),
    RecentlyUpdated(4, false),
    Title(1, true),
    ReleaseYear(6, false),
    ReadProgress(7, false),
    UserRating(10, false),
}

/** A server value picked from a quick-filter sheet (genre id or publication status). */
data class FilterOption(
    val id: Int,
    val title: String,
)

/** One library grid query: sort + quick chips. [key] names its cached rows. */
data class LibraryQuery(
    val libraryId: Int,
    val sort: SeriesSort = SeriesSort.RecentlyAdded,
    val unreadOnly: Boolean = false,
    val genre: FilterOption? = null,
    val status: FilterOption? = null,
) {
    val key: String
        get() = "lib=$libraryId;sort=${sort.name};unread=$unreadOnly;genre=${genre?.id};status=${status?.id}"

    /** Filters on beyond the library itself (for "Filter · n"). */
    val activeFilters: Int get() = listOf(unreadOnly, genre != null, status != null).count { it }

    fun toFilter(): SeriesFilterV2Dto {
        val statements =
            listOfNotNull(
                SeriesFilterStatementDto(EQUAL, FIELD_LIBRARIES, libraryId.toString()),
                SeriesFilterStatementDto(LESS_THAN, FIELD_READ_PROGRESS, "100").takeIf { unreadOnly },
                genre?.let { SeriesFilterStatementDto(CONTAINS, FIELD_GENRES, it.id.toString()) },
                status?.let { SeriesFilterStatementDto(CONTAINS, FIELD_PUBLICATION_STATUS, it.id.toString()) },
            )
        return SeriesFilterV2Dto(statements, sortOptions = SeriesSortOptionDto(sort.field, sort.ascending))
    }

    private companion object {
        // Kavita FilterComparison / SeriesFilterField values (api/openapi.json).
        const val EQUAL = 0
        const val LESS_THAN = 3
        const val CONTAINS = 5
        const val FIELD_PUBLICATION_STATUS = 2
        const val FIELD_GENRES = 18
        const val FIELD_LIBRARIES = 19
        const val FIELD_READ_PROGRESS = 20
    }
}
