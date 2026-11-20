package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

/** 34 high pill. Active: accent + white 14/600. Inactive: surface + hairline 14/500. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    dropdown: Boolean = false,
) {
    val colors = Kavita.colors
    val fg = if (selected) colors.onAccent else colors.labelStrong
    Row(
        modifier =
            modifier
                .height(34.dp)
                .clip(KavitaShapes.pill)
                .background(if (selected) colors.accent else colors.surface)
                .then(if (selected) Modifier else Modifier.hairline())
                .selectable(selected = selected, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dropdown) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(15.dp), tint = fg)
        Text(
            label,
            style = Kavita.type.subhead.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = fg,
            maxLines = 1,
        )
        if (dropdown) Icon(KavitaIcons.ChevronDown, null, Modifier.size(12.dp), tint = fg)
    }
}

enum class StatusChipStyle { Accent, Neutral }

/** 26 high pill, 12/600 (Ongoing, year, rating…). */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    style: StatusChipStyle = StatusChipStyle.Neutral,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) {
    val colors = Kavita.colors
    val (bg, fg) =
        when (style) {
            StatusChipStyle.Accent -> colors.accentContainer to colors.onAccentContainer
            StatusChipStyle.Neutral -> colors.surface to colors.labelBody
        }
    Row(
        modifier =
            modifier
                .height(26.dp)
                .clip(KavitaShapes.pill)
                .background(bg)
                .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(12.dp), tint = leadingIconTint ?: fg)
        Text(text, style = Kavita.type.caption.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}
