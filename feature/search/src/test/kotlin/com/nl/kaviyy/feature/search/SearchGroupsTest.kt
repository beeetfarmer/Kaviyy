package com.nl.kaviyy.feature.search

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchGroupsTest {
    private val results = (previewResults.results as Results.Loaded).value

    @Test
    fun scopesPickTheirGroupsInCanvasOrder() {
        assertEquals(
            listOf(
                GroupKind.Series,
                GroupKind.Chapters,
                GroupKind.People,
                GroupKind.GenresTags,
                GroupKind.ReadingLists,
            ),
            groups(results, SearchScope.All).map { it.kind },
        )
        assertEquals(listOf(GroupKind.GenresTags), groups(results, SearchScope.Tags).map { it.kind })
        assertEquals(3, groups(results, SearchScope.Tags).single().rows.size) // genres + tags
        assertEquals(listOf(GroupKind.ReadingLists), groups(results, SearchScope.Lists).map { it.kind })
        assertEquals(emptyList(), groups(results.copy(series = emptyList()), SearchScope.Series))
    }

    @Test
    fun hitsOpenTheRightThingAndListsWaitForM13() {
        val all = groups(results, SearchScope.All).associate { it.kind to it.rows.first() }
        assertEquals(SearchHit.Series(7), all[GroupKind.Series]?.hit)
        assertEquals(SearchHit.Chapter(87), all[GroupKind.Chapters]?.hit)
        assertEquals(SearchHit.Person(5, "Emberly Moss"), all[GroupKind.People]?.hit)
        assertEquals(SearchHit.Genre(1, "Ember Saga"), all[GroupKind.GenresTags]?.hit)
        assertNull(all[GroupKind.ReadingLists]?.hit)
    }

    @Test
    fun initialsUseTheFirstTwoWords() {
        assertEquals("EM", initials("Emberly Moss"))
        assertEquals("JR", initials("J. R. Tolkien"))
        assertEquals("C", initials("Cher"))
        assertEquals("", initials(" "))
    }
}
