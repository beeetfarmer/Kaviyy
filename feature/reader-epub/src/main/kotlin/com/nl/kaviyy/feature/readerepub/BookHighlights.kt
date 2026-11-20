package com.nl.kaviyy.feature.readerepub

import androidx.compose.ui.graphics.toArgb
import com.nl.kaviyy.core.data.AnnotationDraft
import com.nl.kaviyy.core.data.Highlight
import com.nl.kaviyy.core.designsystem.theme.HighlightColors
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

internal const val HIGHLIGHT_GROUP = "highlights"

// TODO(api): Kavita anchors annotations by an XPath into its own rewritten page DOM, which we can't compute from
// Readium's original XHTML. Send the body so the request validates; Kavita web shows the text and note in its list.
private const val FALLBACK_XPATH = "//body"

/**
 * What the highlight toolbar acts on: a fresh selection ([uuid] null, [locator] set) or an existing highlight.
 * [top]/[bottom] are the target's bounds in navigator pixels, for placing the toolbar.
 */
data class HighlightTarget(
    val text: String,
    val top: Float,
    val bottom: Float,
    val uuid: String? = null,
    val color: Int? = null,
    val note: String? = null,
    val locator: Locator? = null,
)

/** The note editor: for an existing highlight ([uuid]) or a new one from a selection ([draft]). */
data class NoteEditor(
    val initial: String,
    val uuid: String? = null,
    val color: Int = 0,
    val draft: AnnotationDraft? = null,
)

/** A selection becomes a draft: page = spine index, the selected text anchors it. */
internal fun Publication.draft(
    locator: Locator,
    color: Int,
    note: String? = null,
) = AnnotationDraft(
    pageOf(locator),
    locator.text.highlight
        .orEmpty()
        .trim(),
    FALLBACK_XPATH,
    color,
    note,
)

/**
 * Where a stored highlight is: its spine item, found by its text. ponytail: a phrase repeated in the same spine item
 * highlights its first occurrence; store Readium's text before/after if that matters.
 */
internal fun Publication.locatorOf(highlight: Highlight): Locator? =
    readingOrder
        .getOrNull(highlight.page)
        ?.let { locatorFromLink(it) }
        ?.copy(text = Locator.Text(highlight = highlight.text))

internal fun Publication.decorations(highlights: List<Highlight>): List<Decoration> =
    highlights.mapNotNull { h ->
        locatorOf(h)?.let { locator ->
            val tint = HighlightColors.all.getOrElse(h.color) { HighlightColors.yellow }.toArgb()
            Decoration(h.uuid, locator, Decoration.Style.Highlight(tint, isActive = h.note != null))
        }
    }
