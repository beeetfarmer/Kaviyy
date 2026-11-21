package com.nl.kaviyy.core.data

import org.junit.Test
import kotlin.test.assertEquals

class HomeLayoutTest {
    @Test
    fun emptyIsTheDefaultLayoutWithDownloadedHiddenAndNoItemRails() {
        val layout = parseHomeLayout("")
        assertEquals(HomeSection.entries.filterNot { it.perItem }, layout.map { it.section })
        assertEquals(listOf(HomeSection.Downloaded), layout.filterNot { it.visible }.map { it.section })
    }

    @Test
    fun savedOrderHiddenAndItemRailsSurviveARoundTripAndNewSectionsSlotInAfterTheirPredecessor() {
        val saved = "Collection:12,!OnDeck,Bogus,Library:x,Collections,!Library:3,ContinueReading"
        val layout = parseHomeLayout(saved)
        assertEquals(HomeSectionPref(HomeSection.Collection, true, 12), layout[0])
        assertEquals(
            HomeSectionPref(HomeSection.Library, false, 3),
            layout.single { it.section == HomeSection.Library },
        )
        assertEquals(false, layout.first { it.section == HomeSection.OnDeck }.visible)
        // RecentlyUpdated wasn't saved: it goes right after OnDeck, its default predecessor.
        val names = layout.map { it.section }
        assertEquals(names.indexOf(HomeSection.OnDeck) + 1, names.indexOf(HomeSection.RecentlyUpdated))
        // Old shelf names ("Collections") and a library without an id are dropped.
        assertEquals(HomeSection.entries.count { !it.perItem } + 2, layout.size)
        assertEquals(layout, parseHomeLayout(encodeHomeLayout(layout)))
    }

    @Test
    fun everyCollectionAndLibraryGetsARowNewOnesHiddenGoneOnesDropped() {
        val layout =
            parseHomeLayout(
                "Collection:1,Library:9",
            ).withItems(collections = listOf(1, 2), libraries = listOf(4))
        val items = layout.filter { it.section.perItem }
        assertEquals(
            listOf(
                HomeSectionPref(HomeSection.Collection, true, 1),
                HomeSectionPref(HomeSection.Collection, false, 2),
                HomeSectionPref(HomeSection.Library, false, 4),
            ),
            items,
        )
    }

    @Test
    fun movingAndTogglingTargetOneRowAndStayInBounds() {
        val layout = parseHomeLayout("").withItems(listOf(1, 2), emptyList())
        val first = layout.first()
        assertEquals(layout, layout.move(first, -1))
        val two = HomeSectionPref(HomeSection.Collection, false, 2)
        val moved = layout.move(two, -1)
        assertEquals(two, moved[moved.lastIndex - 1])
        val on = moved.toggle(two, true)
        assertEquals(
            listOf(true, false),
            on.filter { it.section.perItem }.map { it.visible }.let { listOf(it[0], it[1]) },
        )
    }

    @Test
    fun readingListsSortByChoice() {
        assertEquals(ListSort.NameAsc, ListSort.of("nonsense", ListSort.NameAsc))
    }
}
