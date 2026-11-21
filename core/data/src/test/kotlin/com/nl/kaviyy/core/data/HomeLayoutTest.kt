package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.LibraryEntity
import org.junit.Test
import kotlin.test.assertEquals

class HomeLayoutTest {
    @Test
    fun emptyIsTheDefaultLayoutWithDownloadedHidden() {
        val layout = parseHomeLayout("")
        assertEquals(HomeSection.entries, layout.map { it.section })
        assertEquals(listOf(HomeSection.Downloaded), layout.filterNot { it.visible }.map { it.section })
    }

    @Test
    fun savedOrderAndHiddenSurviveARoundTripAndNewSectionsSlotInAfterTheirPredecessor() {
        val saved = "Libraries,!OnDeck,Bogus,ContinueReading"
        val layout = parseHomeLayout(saved)
        assertEquals(HomeSection.Libraries, layout[0].section)
        assertEquals(false, layout.first { it.section == HomeSection.OnDeck }.visible)
        // RecentlyUpdated wasn't saved: it goes right after OnDeck, its default predecessor.
        val names = layout.map { it.section }
        assertEquals(names.indexOf(HomeSection.OnDeck) + 1, names.indexOf(HomeSection.RecentlyUpdated))
        assertEquals(HomeSection.entries.size, layout.size)
        assertEquals(layout, parseHomeLayout(encodeHomeLayout(layout)))
    }

    @Test
    fun movingAndTogglingStayInBounds() {
        val layout = parseHomeLayout("")
        assertEquals(layout, layout.move(HomeSection.ContinueReading, -1))
        val moved = layout.move(HomeSection.OnDeck, -1)
        assertEquals(listOf(HomeSection.OnDeck, HomeSection.ContinueReading), moved.take(2).map { it.section })
        assertEquals(false, moved.toggle(HomeSection.OnDeck, false)[0].visible)
    }

    @Test
    fun shelvesSortByChoice() {
        val c =
            listOf(
                CollectionEntity(1, 1, "beta", itemCount = 2, position = 1),
                CollectionEntity(1, 2, "Alpha", itemCount = 9, position = 0),
            )
        assertEquals(listOf("Alpha", "beta"), c.sortedCollections(ListSort.Recent).map { it.title })
        assertEquals(listOf("beta", "Alpha"), c.sortedCollections(ListSort.NameDesc).map { it.title })
        assertEquals(listOf("Alpha", "beta"), c.sortedCollections(ListSort.Largest).map { it.title })
        val l =
            listOf(LibraryEntity(1, 1, "Manga", 0, lastScanned = 5), LibraryEntity(1, 2, "Books", 2, lastScanned = 9))
        assertEquals(listOf("Books", "Manga"), l.sortedLibraries(ListSort.Recent).map { it.name })
        assertEquals(listOf("Books", "Manga"), l.sortedLibraries(ListSort.NameAsc).map { it.name })
        assertEquals(ListSort.NameAsc, ListSort.of("nonsense", ListSort.NameAsc))
    }
}
