package com.nl.kaviyy.feature.readerepub

import com.nl.kaviyy.core.data.ResumePosition
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

internal fun link(
    href: String,
    title: String? = null,
    children: List<Link> = emptyList(),
) = Link(checkNotNull(Url(href)), mediaType = MediaType.XHTML, title = title, children = children)

internal val testBook =
    Publication(
        Manifest(
            metadata = Metadata(),
            readingOrder = listOf(link("ch1.xhtml"), link("ch2.xhtml"), link("ch3.xhtml")),
            tableOfContents =
                listOf(
                    link("ch1.xhtml", "One", listOf(link("ch1.xhtml#a", "One A"))),
                    link("ch3.xhtml", "Three"),
                ),
        ),
    )

internal fun at(
    href: String,
    progression: Double,
) = Locator(checkNotNull(Url(href)), MediaType.XHTML, locations = Locator.Locations(progression = progression))

@RunWith(RobolectricTestRunner::class)
class BookProgressTest {
    @Test
    fun pageIsTheSpineIndexAndTheLastPageWaitsForTheEnd() {
        assertEquals(1, testBook.pageOf(at("ch2.xhtml", 0.5)))
        assertEquals(1, testBook.kavitaPage(at("ch2.xhtml", 0.5), pages = 3))
        assertEquals(1, testBook.kavitaPage(at("ch3.xhtml", 0.5), pages = 3)) // last item, not finished: not read yet
        assertEquals(2, testBook.kavitaPage(at("ch3.xhtml", 0.99), pages = 3)) // finished: pages - 1 = read
    }

    @Test
    fun resumeUsesOurLocatorElseTheStartOfThePage() {
        val exact = at("ch2.xhtml", 0.4)
        assertEquals(exact, testBook.startLocator(ResumePosition(1, exact.toJSON().toString())))
        assertEquals("ch3.xhtml", testBook.startLocator(ResumePosition(2))?.href.toString())
        assertEquals("ch1.xhtml", testBook.startLocator(ResumePosition(0, "not json"))?.href.toString())
        assertEquals(null, testBook.startLocator(ResumePosition(9)))
    }

    @Test
    fun tocIsFlattenedWithDepth() {
        assertEquals(listOf("One" to 0, "One A" to 1, "Three" to 0), testBook.toc().map { it.title to it.depth })
    }
}
