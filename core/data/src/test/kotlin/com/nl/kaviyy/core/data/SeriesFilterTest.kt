package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.network.SeriesFilterStatementDto
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesFilterTest {
    private val genre =
        FilterRule(
            FilterField.Genres,
            Comparison.Contains,
            listOf(RuleValue("7", "Fantasy"), RuleValue("9", "Adventure")),
        )
    private val unread = FilterRule(FilterField.ReadProgress, Comparison.LessThan, listOf(RuleValue("100")))

    @Test
    fun buildsStatementsWithScopeFirstAndSkipsIncompleteRules() {
        val blank = FilterRule(FilterField.SeriesName, Comparison.Matches, listOf(RuleValue(" ")))
        val f = SeriesFilter(rules = listOf(genre, unread, blank), sort = SeriesSort.Title)
        val dto = f.toDto(listOf(3))
        assertEquals(
            listOf(
                SeriesFilterStatementDto(5, 19, "3"),
                SeriesFilterStatementDto(5, 18, "7,9"),
                SeriesFilterStatementDto(3, 20, "100"),
            ),
            dto.statements,
        )
        assertEquals(1, dto.combination)
        assertEquals(1 to true, dto.sortOptions?.let { it.sortField to it.isAscending })
        assertEquals(2, f.activeRules)
    }

    @Test
    fun anyRuleUsesOrAndExplicitLibrariesOverrideTheScope() {
        val dto = SeriesFilter(matchAll = false, rules = listOf(genre), libraryIds = listOf(1, 2)).toDto(listOf(3))
        assertEquals(0, dto.combination)
        assertEquals("1,2", dto.statements.first().value)
    }

    @Test
    fun comparisonsMustSuitTheField() {
        assertTrue(unread.isComplete)
        assertEquals(false, unread.copy(comparison = Comparison.Contains).isComplete)
    }

    @Test
    fun encodesAndDecodes() {
        val f = SeriesFilter(rules = listOf(genre), sort = SeriesSort.ReleaseYear, libraryIds = listOf(4))
        assertEquals(f, SeriesFilter.decode(f.encode()))
        assertNull(SeriesFilter.decode("{not json"))
        assertNotEquals(LibraryQuery(3, f).key, LibraryQuery(3).key)
    }

    @Test
    fun everyKavitaFieldIsCoveredOnce() {
        val apis = FilterField.entries.map { it.api } + FilterField.LIBRARIES_API
        assertEquals((0..33).toList(), apis.sorted()) // SeriesFilterField 0..33 in api/openapi.json
    }
}
