package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

/** Placeholder cover colors from the design canvas. */
val CoverPalette =
    listOf(
        Color(0xFFB4441B),
        Color(0xFF1F3A68),
        Color(0xFF0E5E58),
        Color(0xFF5B3A8C),
        Color(0xFF7A2E3A),
        Color(0xFF2F5D3A),
        Color(0xFF8A5A12),
        Color(0xFF334155),
        Color(0xFF1E4F7A),
        Color(0xFF6B2F5A),
    )

/** Stable placeholder color for a series (by ID, so it never changes with the title). */
fun coverColorFor(seriesId: Long): Color = CoverPalette[Math.floorMod(seriesId, CoverPalette.size.toLong()).toInt()]

private const val MAX_BADGE = 99

internal fun badgeText(count: Int): String = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString()

/**
 * 2:3 cover. Shows placeholder art (series color, Literata title bottom-left, optional "VOL. n" overline).
 * TODO(M2): draw the server cover via Coil on top, keeping this placeholder while loading / on failure.
 */
@Composable
fun CoverImage(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = KavitaShapes.coverGrid,
    overline: String? = null,
    titleSize: TextUnit = 16.sp,
    titleBottomInset: Dp = 12.dp,
    sideInset: Dp = 10.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.clip(shape).background(color)) {
        if (overline != null) {
            Text(
                overline.uppercase(),
                style = Kavita.type.overline.copy(fontSize = (titleSize.value * 0.6f).sp, letterSpacing = 0.08.em),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.TopStart).padding(start = sideInset - 1.dp, top = sideInset - 1.dp),
            )
        }
        Text(
            title,
            style = Kavita.type.coverTitle.copy(fontSize = titleSize),
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sideInset, end = sideInset, bottom = titleBottomInset),
        )
        overlay()
    }
}

/** Rail (120×180, Home) or Grid (fills a column, Library) variant, with the canvas dimensions of each. */
enum class CoverCardSize(
    internal val shape: Shape,
    internal val gap: Dp,
    internal val coverTitle: TextUnit,
    internal val label: TextUnit,
    internal val inset: Dp,
    internal val badge: Dp,
    internal val badgePadding: Dp,
    internal val badgeText: TextUnit,
    internal val shadowY: Dp,
    internal val shadowBlur: Dp,
) {
    Rail(KavitaShapes.coverLarge, 8.dp, 16.sp, 14.sp, 10.dp, 22.dp, 7.dp, 12.sp, 4.dp, 12.dp),
    Grid(KavitaShapes.coverGrid, 6.dp, 13.sp, 13.sp, 8.dp, 20.dp, 6.dp, 11.sp, 3.dp, 10.dp),
}

@Composable
private fun BoxScope.UnreadBadge(
    count: Int,
    size: CoverCardSize,
) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(size.inset - 2.dp)
            .sizeIn(minWidth = size.badge)
            .height(size.badge)
            .clip(KavitaShapes.pill)
            .background(Color.White)
            .padding(horizontal = size.badgePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            badgeText(count),
            style = Kavita.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = size.badgeText),
            color = Kavita.colors.accent,
        )
    }
}

@Composable
private fun BoxScope.CoverProgress(progress: Float) {
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.3f)),
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
    }
}

/** [derived] 20dp accent circle with a white check, bottom-right. */
@Composable
private fun BoxScope.DownloadedBadge(
    description: String,
    aboveProgress: Boolean,
) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = if (aboveProgress) 10.dp else 6.dp)
            .size(20.dp)
            .clip(KavitaShapes.pill)
            .background(Kavita.colors.accent)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(KavitaIcons.Check, null, Modifier.size(14.dp), tint = Kavita.colors.onAccent)
    }
}

@Composable
fun CoverCard(
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: CoverCardSize = CoverCardSize.Rail,
    subtitleAccent: Boolean = false,
    progress: Float? = null,
    unreadCount: Int? = null,
    downloadedDescription: String? = null,
) {
    val colors = Kavita.colors
    val coverSize =
        when (size) {
            CoverCardSize.Rail -> Modifier.size(120.dp, 180.dp)
            CoverCardSize.Grid -> Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        }
    // Rail titles sit higher when the progress bar is shown (canvas: bottom 16 vs 12); grid is always 9.
    val titleBottom =
        when {
            size == CoverCardSize.Grid -> 9.dp
            progress != null -> 16.dp
            else -> 12.dp
        }
    Column(
        modifier =
            modifier
                .then(if (size == CoverCardSize.Rail) Modifier.width(120.dp) else Modifier)
                .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(size.gap),
    ) {
        CoverImage(
            title = title,
            color = color,
            shape = size.shape,
            titleSize = size.coverTitle,
            titleBottomInset = titleBottom,
            sideInset = size.inset,
            modifier = coverSize.cssShadow(size.shape, size.shadowY, size.shadowBlur, 0.14f),
        ) {
            if (unreadCount != null && unreadCount > 0) UnreadBadge(unreadCount, size)
            if (progress != null) CoverProgress(progress)
            if (downloadedDescription != null) DownloadedBadge(downloadedDescription, aboveProgress = progress != null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = Kavita.type.subhead.copy(fontWeight = FontWeight.SemiBold, fontSize = size.label),
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style =
                    Kavita.type.caption.copy(
                        fontWeight = if (subtitleAccent) FontWeight.Medium else FontWeight.Normal,
                    ),
                color = if (subtitleAccent) colors.accent else colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
