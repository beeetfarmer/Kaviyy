package com.nl.kaviyy.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

private const val SEGMENT_ANIM_MS = 220

/** 36 high, `fillSegmented` track with 2dp padding; selected segment raised with a soft shadow. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Kavita.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(KavitaShapes.segmentedTrack)
                .background(colors.fillSegmented)
                .padding(2.dp)
                .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) colors.segmentSelected else Color.Transparent,
                tween(SEGMENT_ANIM_MS),
                label = "segment",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (selected) Modifier.cssShadow(KavitaShapes.segment, 2.dp, 6.dp, 0.12f) else Modifier)
                        .clip(KavitaShapes.segment)
                        .background(bg)
                        .selectable(selected = selected, role = Role.Tab) {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style =
                        Kavita.type.footnote.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
