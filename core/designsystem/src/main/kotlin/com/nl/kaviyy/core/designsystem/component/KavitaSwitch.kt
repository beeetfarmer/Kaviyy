package com.nl.kaviyy.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

private const val SWITCH_ANIM_MS = 200

/** 52×32 track (accent / `switchOff`), white 28 knob with shadow. */
@Composable
fun KavitaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val colors = Kavita.colors
    val haptics = LocalHapticFeedback.current
    val track by animateColorAsState(
        if (checked) colors.accent else colors.switchOff,
        tween(SWITCH_ANIM_MS),
        label = "track",
    )
    val knobX by animateDpAsState(if (checked) 22.dp else 2.dp, tween(SWITCH_ANIM_MS), label = "knob")
    Box(
        modifier =
            modifier
                .size(52.dp, 32.dp)
                .clip(KavitaShapes.pill)
                .background(track)
                .toggleable(checked, enabled = enabled, role = Role.Switch) {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onCheckedChange(it)
                }.semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(28.dp)
                .cssShadow(KavitaShapes.pill, 2.dp, 4.dp, 0.2f)
                .clip(KavitaShapes.pill)
                .background(Color.White),
        )
    }
}
