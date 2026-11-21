package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.fixedScale

private val MaxBarHeight = 70.dp

/** Bar heights as a fraction of the largest value (all zero when there is no data). */
internal fun barFractions(values: List<Float>): List<Float> {
    val max = values.maxOrNull() ?: return emptyList()
    return if (max <= 0f) values.map { 0f } else values.map { (it / max).coerceIn(0f, 1f) }
}

/** 7 bars (radius 6, max 70), peak = accent, others `chartMuted`, 11/600 day labels. One a11y label. */
@Composable
fun StatBars(
    values: List<Float>,
    labels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = Kavita.colors
    val fractions = barFractions(values)
    val peak = fractions.indexOfFirst { it == 1f }
    Row(
        modifier =
            modifier.fillMaxWidth().height(92.dp).clearAndSetSemantics {
                this.contentDescription =
                    contentDescription
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        fractions.forEachIndexed { i, f ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MaxBarHeight * f)
                        .background(
                            if (i ==
                                peak
                            ) {
                                colors.accent
                            } else {
                                colors.chartMuted
                            },
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(6.dp),
                        ),
                )
                Text(
                    labels.getOrElse(i) { "" },
                    // Day letters under fixed-height bars; the figures are in the chart's content description.
                    style =
                        Kavita.type.tabLabel.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize =
                                Kavita.type.tabLabel.fontSize
                                    .fixedScale(),
                        ),
                    color = colors.labelSecondary,
                )
            }
        }
    }
}
