package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.network.SeriesFilterStatementDto
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LibraryQueryTest {
    @Test
    fun buildsKavitaFilterStatementsAndSort() {
        val q =
            LibraryQuery(3, SeriesSort.Title, unreadOnly = true, FilterOption(7, "Drama"), FilterOption(2, "Completed"))
        val f = q.toFilter()
        assertEquals(
            listOf(
                SeriesFilterStatementDto(0, 19, "3"),
                SeriesFilterStatementDto(3, 20, "100"),
                SeriesFilterStatementDto(5, 18, "7"),
                SeriesFilterStatementDto(5, 2, "2"),
            ),
            f.statements,
        )
        assertEquals(1, f.combination) // And
        assertEquals(1 to true, f.sortOptions?.let { it.sortField to it.isAscending })
        assertEquals(3, q.activeFilters)
    }

    @Test
    fun defaultIsRecentlyAddedNewestFirstAndKeysDiffer() {
        val q = LibraryQuery(3)
        assertEquals(2 to false, q.toFilter().sortOptions?.let { it.sortField to it.isAscending })
        assertEquals(1, q.toFilter().statements.size)
        assertEquals(0, q.activeFilters)
        assertNotEquals(q.key, q.copy(unreadOnly = true).key)
        assertNotEquals(q.key, q.copy(libraryId = 4).key)
        assertEquals(q.key, q.copy(genre = null).key)
    }
}
