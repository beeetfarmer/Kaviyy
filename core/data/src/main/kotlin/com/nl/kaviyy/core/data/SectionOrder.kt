package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.ReadingListRow
import com.nl.kaviyy.core.datastore.AppSettings
import kotlin.random.Random

/** How a Home section is ordered (You → Home Screen › Order). [Default] is the section's own order. */
enum class SectionOrder { Default, NameAsc, NameDesc, Largest, Random }

/** The orders [this] section offers; empty = it isn't orderable (a single card). */
val HomeSection.orders: List<SectionOrder>
    get() =
        when (this) {
            HomeSection.ContinueReading -> {
                emptyList()
            }

            HomeSection.OnDeck, HomeSection.RecentlyUpdated, HomeSection.RecentlyAdded, HomeSection.Downloaded -> {
                listOf(SectionOrder.Default, SectionOrder.NameAsc, SectionOrder.NameDesc)
            }

            HomeSection.SmartFilters, HomeSection.WantToRead, HomeSection.Collection, HomeSection.Library -> {
                listOf(SectionOrder.Default, SectionOrder.NameAsc, SectionOrder.NameDesc, SectionOrder.Random)
            }

            HomeSection.ReadingLists -> {
                SectionOrder.entries
            }
        }

/** Ordered by the server, which then also picks which series show; a change needs a refresh. */
val HomeSection.serverOrdered: Boolean
    get() = this in setOf(HomeSection.SmartFilters, HomeSection.WantToRead, HomeSection.Collection, HomeSection.Library)

/** "OnDeck", or "Collection:3" for a per-item section. */
val HomeSectionPref.orderKey: String get() = if (section.perItem) "${section.name}:$id" else section.name

/** Every section's chosen order from [settings] (the old Reading Lists setting is the fallback for that rail). */
fun homeOrders(settings: AppSettings): Map<String, SectionOrder> {
    val saved =
        settings.homeOrder
            .split(',')
            .mapNotNull { part ->
                val key = part.substringBefore('=').trim()
                SectionOrder.entries.firstOrNull { it.name == part.substringAfter('=', "") }?.let { key to it }
            }.toMap()
    val legacy =
        SectionOrder.entries
            .firstOrNull { it.name == settings.readingListSort }
            ?.let { mapOf(HomeSection.ReadingLists.name to it) }
            .orEmpty()
    return legacy + saved
}

fun encodeHomeOrders(orders: Map<String, SectionOrder>): String =
    orders.filterValues { it != SectionOrder.Default }.entries.joinToString(",") { "${it.key}=${it.value.name}" }

/** The order of [pref], if it's one its section offers. */
fun Map<String, SectionOrder>.orderOf(pref: HomeSectionPref): SectionOrder =
    this[pref.orderKey]?.takeIf { it in pref.section.orders } ?: SectionOrder.Default

/** A–Z / Z–A of what's already loaded; other orders keep it as is. */
fun <T> List<T>.ordered(
    order: SectionOrder,
    name: (T) -> String,
): List<T> =
    when (order) {
        SectionOrder.NameAsc -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, name))
        SectionOrder.NameDesc -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, name))
        else -> this
    }

/** Reading lists in [order]; Random shuffles with [seed] (a new one each Home refresh). */
fun List<ReadingListRow>.sortedReadingLists(
    order: SectionOrder,
    seed: Long = 0,
): List<ReadingListRow> =
    when (order) {
        SectionOrder.Default -> {
            sortedBy { it.list.position }
        }

        SectionOrder.Largest -> {
            sortedWith(
                compareByDescending<ReadingListRow> {
                    it.list.itemCount
                }.thenBy { it.list.title.lowercase() },
            )
        }

        SectionOrder.Random -> {
            sortedBy { it.list.position }.shuffled(Random(seed))
        }

        else -> {
            ordered(order) { it.list.title }
        }
    }
