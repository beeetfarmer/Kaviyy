package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.HighlightColors
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

private val ToolbarColor = Color(0xEB1C1C1E) // rgba(28,28,30,0.92)

/** 48 high dark pill: 4 color dots (selected has a 2dp white ring), divider, Note, Copy. */
@Composable
fun HighlightToolbar(
    colorLabels: List<String>,
    selectedColor: Int?,
    onSelectColor: (Int) -> Unit,
    noteLabel: String,
    onNote: () -> Unit,
    copyLabel: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(48.dp)
                .cssShadow(KavitaShapes.pill, 10.dp, 30.dp, 0.25f)
                .clip(KavitaShapes.pill)
                .background(ToolbarColor)
                .padding(start = 12.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HighlightColors.all.forEachIndexed { i, color ->
                val selected = i == selectedColor
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(KavitaShapes.pill)
                        .background(color)
                        .then(if (selected) Modifier.border(2.dp, Color.White, KavitaShapes.pill) else Modifier)
                        .selectable(selected, role = Role.RadioButton) { onSelectColor(i) }
                        .semantics { contentDescription = colorLabels.getOrElse(i) { "" } },
                )
            }
        }
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(24.dp)
                .background(Color.White.copy(alpha = 0.25f)),
        )
        listOf(noteLabel to onNote, copyLabel to onCopy).forEach { (label, action) ->
            Box(
                Modifier
                    .height(
                        36.dp,
                    ).clip(KavitaShapes.pill)
                    .clickable(role = Role.Button, onClick = action)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Kavita.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}
