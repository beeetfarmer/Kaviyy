package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.database.ReadingListRow

/** Home sections the user can show, hide and reorder (You → Home Screen). Declaration order = default order. */
enum class HomeSection(
    val defaultVisible: Boolean = true,
) {
    ContinueReading,
    OnDeck,
    RecentlyUpdated,
    RecentlyAdded,
    SmartFilters,
    WantToRead,
    Collections,
    ReadingLists,
    Libraries,

    /** Offline, downloads always come first; this shows them online too. */
    Downloaded(defaultVisible = false),
}

data class HomeSectionPref(
    val section: HomeSection,
    val visible: Boolean,
)

/**
 * Reads `AppSettings.homeLayout` ("OnDeck,!Collections,…"). Unknown names are dropped; sections the string doesn't
 * mention (new in a later version) keep their default visibility and are inserted after their default predecessor.
 */
fun parseHomeLayout(encoded: String): List<HomeSectionPref> {
    val saved =
        encoded
            .split(',')
            .mapNotNull { part ->
                val name = part.trim().removePrefix("!")
                HomeSection.entries.firstOrNull { it.name == name }?.let {
                    HomeSectionPref(
                        it,
                        !part.trim().startsWith("!"),
                    )
                }
            }.distinctBy { it.section }
            .toMutableList()
    HomeSection.entries.forEach { section ->
        if (saved.none { it.section == section }) {
            val before = HomeSection.entries.getOrNull(section.ordinal - 1)
            val at = saved.indexOfFirst { it.section == before } + 1
            saved.add(at, HomeSectionPref(section, section.defaultVisible))
        }
    }
    return saved
}

fun encodeHomeLayout(layout: List<HomeSectionPref>): String =
    layout.joinToString(",") { (if (it.visible) "" else "!") + it.section.name }

/** Moves [section] one place up ([by] = -1) or down (+1); no-op at the ends. */
fun List<HomeSectionPref>.move(
    section: HomeSection,
    by: Int,
): List<HomeSectionPref> {
    val from = indexOfFirst { it.section == section }
    val to = from + by
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

fun List<HomeSectionPref>.toggle(
    section: HomeSection,
    visible: Boolean,
) = map { if (it.section == section) it.copy(visible = visible) else it }

/** Order of the Collections / Reading Lists / Libraries rails. */
enum class ListSort {
    /** Server order: most recently changed first (libraries: most recently scanned). */
    Recent,
    NameAsc,
    NameDesc,

    /** Most series / items first (libraries: same as [NameAsc], they carry no count). */
    Largest,
    ;

    companion object {
        fun of(
            name: String,
            default: ListSort = Recent,
        ) = entries.firstOrNull { it.name == name } ?: default
    }
}

private fun <T> List<T>.sorted(
    sort: ListSort,
    name: (T) -> String,
    recent: Comparator<T>,
    size: Comparator<T>,
): List<T> =
    when (sort) {
        ListSort.Recent -> sortedWith(recent)
        ListSort.NameAsc -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, name))
        ListSort.NameDesc -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, name))
        ListSort.Largest -> sortedWith(size.then(compareBy(String.CASE_INSENSITIVE_ORDER, name)))
    }

fun List<CollectionEntity>.sortedCollections(sort: ListSort) =
    sorted(sort, { it.title }, compareBy { it.position }, compareByDescending { it.itemCount })

fun List<ReadingListRow>.sortedReadingLists(sort: ListSort) =
    sorted(sort, { it.list.title }, compareBy { it.list.position }, compareByDescending { it.list.itemCount })

fun List<LibraryEntity>.sortedLibraries(sort: ListSort) =
    sorted(
        sort,
        { it.name },
        compareByDescending { it.lastScanned },
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.name },
    )
