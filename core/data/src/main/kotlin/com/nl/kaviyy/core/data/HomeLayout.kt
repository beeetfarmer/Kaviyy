package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ReadingListRow

/** Home sections the user can show, hide and reorder (You → Home Screen). Declaration order = default order. */
enum class HomeSection(
    val defaultVisible: Boolean = true,
    /** One rail per chosen item ([HomeSectionPref.id]); never added by default. */
    val perItem: Boolean = false,
) {
    ContinueReading,
    OnDeck,
    RecentlyUpdated,
    RecentlyAdded,
    SmartFilters,
    WantToRead,
    ReadingLists,

    /** Offline, downloads always come first; this shows them online too. */
    Downloaded(defaultVisible = false),

    /** The series of one collection. */
    Collection(defaultVisible = false, perItem = true),

    /** The series of one library. */
    Library(defaultVisible = false, perItem = true),
}

data class HomeSectionPref(
    val section: HomeSection,
    val visible: Boolean,
    /** Collection or library id for a [HomeSection.perItem] section, else 0. */
    val id: Int = 0,
) {
    fun sameRow(other: HomeSectionPref) = section == other.section && id == other.id
}

/**
 * Reads `AppSettings.homeLayout` ("OnDeck,!WantToRead,Collection:12,…"). Unknown names are dropped; fixed sections
 * the string doesn't mention (new in a later version) keep their default visibility after their default predecessor.
 */
fun parseHomeLayout(encoded: String): List<HomeSectionPref> {
    val saved =
        encoded
            .split(',')
            .mapNotNull { part -> parsePref(part.trim()) }
            .distinctBy { it.section to it.id }
            .toMutableList()
    HomeSection.entries.filterNot { it.perItem }.forEach { section ->
        if (saved.none { it.section == section }) {
            val before = HomeSection.entries.getOrNull(section.ordinal - 1)
            val at = saved.indexOfFirst { it.section == before } + 1
            saved.add(at, HomeSectionPref(section, section.defaultVisible))
        }
    }
    return saved
}

private fun parsePref(part: String): HomeSectionPref? {
    val name = part.removePrefix("!").substringBefore(':')
    val section = HomeSection.entries.firstOrNull { it.name == name }
    val id = part.substringAfter(':', "").toIntOrNull()
    return when {
        section == null -> null
        section.perItem && id == null -> null
        else -> HomeSectionPref(section, !part.startsWith("!"), id ?: 0)
    }
}

fun encodeHomeLayout(layout: List<HomeSectionPref>): String =
    layout.joinToString(",") {
        (if (it.visible) "" else "!") + it.section.name + (if (it.section.perItem) ":${it.id}" else "")
    }

/**
 * The layout with a row for every [collections] and [libraries] id: new ones are added hidden at the end, and rows
 * for ones that no longer exist are dropped.
 */
fun List<HomeSectionPref>.withItems(
    collections: List<Int>,
    libraries: List<Int>,
): List<HomeSectionPref> {
    val exists = collections.map { HomeSection.Collection to it } + libraries.map { HomeSection.Library to it }
    val kept = filter { !it.section.perItem || (it.section to it.id) in exists }
    val added =
        exists
            .filter { (section, id) -> kept.none { it.section == section && it.id == id } }
            .map { (section, id) -> HomeSectionPref(section, visible = false, id = id) }
    return kept + added
}

/** Moves [row] one place up ([by] = -1) or down (+1); no-op at the ends. */
fun List<HomeSectionPref>.move(
    row: HomeSectionPref,
    by: Int,
): List<HomeSectionPref> {
    val from = indexOfFirst { it.sameRow(row) }
    val to = from + by
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

fun List<HomeSectionPref>.toggle(
    row: HomeSectionPref,
    visible: Boolean,
) = map { if (it.sameRow(row)) it.copy(visible = visible) else it }

/** Order of the Reading Lists rail. */
enum class ListSort {
    /** Server order: most recently changed first. */
    Recent,
    NameAsc,
    NameDesc,

    /** Most items first. */
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

fun List<ReadingListRow>.sortedReadingLists(sort: ListSort) =
    sorted(sort, { it.list.title }, compareBy { it.list.position }, compareByDescending { it.list.itemCount })
