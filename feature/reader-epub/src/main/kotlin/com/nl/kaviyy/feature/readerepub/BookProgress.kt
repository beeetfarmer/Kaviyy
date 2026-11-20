package com.nl.kaviyy.feature.readerepub

import com.nl.kaviyy.core.data.ResumePosition
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref

/*
 * EPUB progress mapping (M11.2 spike):
 * - Kavita's EPUB "page" is the reading-order (spine) index; `api/Book/{id}/chapters` maps xhtml keys to it.
 *   Readium's readingOrder is the same spine, so page = index of the locator's href in it.
 * - Kavita's `bookScrollId` is an XPath into its own rewritten page DOM; positions inside Readium's original XHTML
 *   can't be translated reliably, so none is sent (Kavita web opens that page at the top). Our exact Readium locator
 *   is kept locally and used to resume when the server agrees on the page.
 * - The chapter counts as read once the last spine item is (almost) finished: page = pages - 1 is recorded then.
 */

private const val END_PROGRESSION = 0.98

/** One TOC row: nested entries are flattened with their depth. */
data class TocEntry(
    val title: String,
    val depth: Int,
    val link: Link,
)

internal fun Publication.toc(): List<TocEntry> {
    fun walk(
        links: List<Link>,
        depth: Int,
    ): List<TocEntry> = links.flatMap { listOf(TocEntry(it.title.orEmpty(), depth, it)) + walk(it.children, depth + 1) }
    return walk(tableOfContents, 0)
}

/** Reading-order index of [locator] (Kavita's page). */
internal fun Publication.pageOf(locator: Locator): Int =
    readingOrder.indexOfFirstWithHref(locator.href)?.coerceAtLeast(0) ?: 0

/**
 * The page to report: the real index, except that the last page (which marks the chapter read) is only reported once
 * the last spine item is finished.
 */
internal fun Publication.kavitaPage(
    locator: Locator,
    pages: Int,
): Int {
    val page = pageOf(locator)
    val last = (if (pages > 0) pages else readingOrder.size) - 1
    val finished = page >= readingOrder.lastIndex && (locator.locations.progression ?: 0.0) >= END_PROGRESSION
    return if (finished) last else page.coerceAtMost(last - 1).coerceAtLeast(0)
}

/** Our exact locator when we have one for the page, else the start of that spine item. */
internal fun Publication.startLocator(resume: ResumePosition): Locator? =
    resume.position?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        ?: readingOrder.getOrNull(resume.page)?.let { locatorFromLink(it) }
