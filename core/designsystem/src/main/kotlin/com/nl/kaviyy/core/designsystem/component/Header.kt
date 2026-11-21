package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.fixedScale

/**
 * Optional uppercase overline (e.g. the date) above a 34sp title, actions bottom-aligned on the right.
 * TODO(design): collapse to a 17sp centred title on scroll [derived] when the first scrolling screen lands.
 */
@Composable
fun LargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (overline != null) {
                Text(overline.uppercase(), style = Kavita.type.dateOverline, color = Kavita.colors.labelSecondary)
            }
            Text(
                title,
                style = Kavita.type.largeTitle,
                color = Kavita.colors.label,
                // Titles are often user content (series, list names): wrap rather than cut after a few letters.
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(
            modifier = Modifier.padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

private const val TITLE_MAX_LINES = 3

/** 36 high pill: status dot + server name + chevron. Opens the server switcher. */
@Composable
fun ServerPill(
    name: String,
    online: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Kavita.colors
    Row(
        modifier =
            modifier
                .height(36.dp)
                .clip(KavitaShapes.pill)
                .background(colors.surface)
                .hairline()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 10.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(KavitaShapes.pill).background(if (online) colors.online else colors.labelTertiary))
        Text(
            name,
            style = Kavita.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.labelStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(KavitaIcons.ChevronDown, null, Modifier.size(12.dp), tint = colors.labelStrong)
    }
}

/** Accent circle with white initials: 36 (header) or 56 (profile). */
@Composable
fun Avatar(
    initials: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
) {
    val colors = Kavita.colors
    Box(
        modifier =
            modifier
                .size(size)
                .clip(KavitaShapes.pill)
                .background(colors.accent)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials.take(2).uppercase(),
            style =
                Kavita.type.footnote.copy(
                    fontWeight = FontWeight.Bold,
                    // Initials sit in a fixed circle; the name is always shown beside it.
                    fontSize = (if (size > 36.dp) 20.sp else 13.sp).fixedScale(),
                ),
            color = colors.onAccent,
        )
    }
}

/** 13sp uppercase secondary label above a [GroupedList]. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(),
        style = Kavita.type.sectionHeader,
        color = Kavita.colors.labelSecondary,
        modifier = modifier.padding(start = 16.dp).semantics { heading() },
    )
}

/** Home rail title (22/700) with an accent "See All"-style action (15/500), baseline-aligned. */
@Composable
fun RailHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = Kavita.type.title2,
            color = Kavita.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null) {
            Text(
                actionLabel,
                style = Kavita.type.callout.copy(fontWeight = FontWeight.Medium),
                color = Kavita.colors.accent,
                // 48dp touch target around the 15sp label.
                modifier =
                    Modifier
                        .clip(KavitaShapes.pill)
                        .clickable(role = Role.Button, onClick = onAction)
                        .heightIn(min = 48.dp)
                        .wrapContentHeight()
                        .padding(start = 8.dp),
            )
        }
    }
}
