@file:Suppress("MagicNumber")

package com.nl.kaviyy.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Stroke icons from the design canvas (DESIGN.md §5 Icons): 24 viewBox, round caps/joins.
 * Stroke widths match the canvas usage (tab bar 1.8, general 2.0–2.6). Tint with `Icon(tint = …)`.
 * The two-tone clear/add/remove circles are composables in `component`, since their fill is a theme color.
 */
object KavitaIcons {
    // Tab bar (1.8)
    val Home = stroke("home", 1.8f, "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Library =
        stroke("library", 1.8f, rect(3f, 4f, 5f, 16f, 1f), rect(10f, 4f, 5f, 16f, 1f), "m17 5.5 3-.8 2.5 15-3 .8z")
    val Bookmark = stroke("bookmark", 1.8f, BOOKMARK)
    val BookmarkFilled = fill("bookmark-filled", BOOKMARK)
    val User = stroke("user", 1.8f, circle(12f, 8f, 4f), "M4 21c1.6-3.8 4.6-6 8-6s6.4 2.2 8 6")
    val Search = stroke("search", 2f, circle(11f, 11f, 7f), "m20 20-3.5-3.5")
    val Download = stroke("download", 2f, "M12 4v11M7 10.5l5 5 5-5M5 20h14")

    // General
    val Import = stroke("import", 2f, "M12 15V4M7.5 8.5 12 4l4.5 4.5M5 20h14")
    val Share = stroke("share", 2.1f, "M12 3v12M7.5 7.5 12 3l4.5 4.5", "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7")
    val More = fill("more", circle(5f, 12f, 1.8f), circle(12f, 12f, 1.8f), circle(19f, 12f, 1.8f))
    val Grid =
        stroke(
            "grid",
            2f,
            rect(4f, 4f, 6.5f, 6.5f, 1.5f),
            rect(13.5f, 4f, 6.5f, 6.5f, 1.5f),
            rect(4f, 13.5f, 6.5f, 6.5f, 1.5f),
            rect(13.5f, 13.5f, 6.5f, 6.5f, 1.5f),
        )
    val Filter = stroke("filter", 2.2f, "M4 6h16M7 12h10M10 18h4")
    val Plus = stroke("plus", 2.2f, "M12 5v14M5 12h14")
    val Close = stroke("close", 2.2f, "M6 6l12 12M18 6 6 18")
    val ChevronDown = stroke("chevron-down", 2.4f, "m6 9 6 6 6-6")
    val ChevronLeft = stroke("chevron-left", 2.4f, "m15 5-7 7 7 7")
    val ChevronRight = stroke("chevron-right", 2.6f, "m9 5 7 7-7 7")
    val Play = fill("play", "M7 4.5v15l13-7.5z")
    val Toc =
        ImageVector
            .Builder("toc", 24.dp, 24.dp, 24f, 24f)
            .strokePath("M9 6h11M9 12h11M9 18h11", 2f)
            .fillPath(circle(4.5f, 6f, 1f) + circle(4.5f, 12f, 1f) + circle(4.5f, 18f, 1f))
            .build()
    val ListIcon = stroke("list", 2f, "M9 6h11M9 12h11M9 18h11M4 6h1M4 12h1M4 18h1")
    val Sliders = stroke("sliders", 2f, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Star = fill("star", "m12 3 2.8 5.8 6.2.9-4.5 4.4 1 6.2L12 17.4l-5.5 2.9 1-6.2L3 9.7l6.2-.9z")
    val SkipPrevious = stroke("skip-prev", 2f, "M6 5v14M18 6l-8 6 8 6z")
    val SkipNext = stroke("skip-next", 2f, "M18 5v14M6 6l8 6-8 6z")
    val ArrowRtl = stroke("arrow-rtl", 2f, "M20 12H4M10 6l-6 6 6 6")
    val ArrowLtr = stroke("arrow-ltr", 2f, "M4 12h16M14 6l6 6-6 6")
    val Book =
        stroke(
            "book",
            1.7f,
            "M12 6.5C10.3 5 7.8 4.5 4 4.5v13c3.8 0 6.3.5 8 2 1.7-1.5 4.2-2 8-2v-13c-3.8 0-6.3.5-8 2z",
            "M12 6.5v13",
        )
    val Lock = stroke("lock", 2f, rect(4f, 10f, 16f, 11f, 2f), "M8 10V7a4 4 0 0 1 8 0v3")
    val Check = stroke("check", 2.4f, "m8 12 3 3 5-6")
    val CheckCircle = stroke("check-circle", 2.2f, circle(12f, 12f, 9f), "m8 12 3 3 5-6")
    val Moon = stroke("moon", 2f, "M12 3a9 9 0 1 0 9 9 7 7 0 0 1-9-9z")
    val Server = stroke("server", 2f, "M4 4h16v7H4zM4 13h16v7H4zM8 7.5h.01M8 16.5h.01")
    val Device = stroke("device", 2f, "M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM10 18h4")
    val Activity = stroke("activity", 2f, "M4 12h3l2-6 4 12 2-6h5")

    /** Every icon, for the catalogue and tests. */
    val all: List<ImageVector> by lazy {
        listOf(
            Home,
            Library,
            Bookmark,
            BookmarkFilled,
            User,
            Search,
            Download,
            Import,
            Share,
            More,
            Grid,
            Filter,
            Plus,
            Close,
            ChevronDown,
            ChevronLeft,
            ChevronRight,
            Play,
            Toc,
            ListIcon,
            Sliders,
            Star,
            SkipPrevious,
            SkipNext,
            ArrowRtl,
            ArrowLtr,
            Book,
            Lock,
            Check,
            CheckCircle,
            Moon,
            Server,
            Device,
            Activity,
        )
    }
}

private const val BOOKMARK = "M6 3h12a1 1 0 0 1 1 1v17l-7-4.5L5 21V4a1 1 0 0 1 1-1z"

internal fun circle(
    cx: Float,
    cy: Float,
    r: Float,
) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

internal fun rect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    rx: Float,
) = "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx ${rx}v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} $rx" +
    "h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

private fun ImageVector.Builder.strokePath(
    d: String,
    width: Float,
) = addPath(
    pathData = addPathNodes(d),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
)

private fun ImageVector.Builder.fillPath(d: String) =
    addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))

private fun stroke(
    name: String,
    width: Float,
    vararg paths: String,
): ImageVector =
    ImageVector
        .Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply { paths.forEach { strokePath(it, width) } }
        .build()

private fun fill(
    name: String,
    vararg paths: String,
): ImageVector =
    ImageVector
        .Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply { paths.forEach { fillPath(it) } }
        .build()
