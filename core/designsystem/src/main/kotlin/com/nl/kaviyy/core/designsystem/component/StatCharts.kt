package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.fixedScale

/**
 * [derived] Column chart for many bars (hours of day, months): thin rounded columns, the peak in accent, axis
 * labels only where [labels] has one (blank elsewhere). TalkBack reads [contentDescription] instead of the bars.
 */
@Composable
fun ColumnChart(
    values: List<Float>,
    labels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = Kavita.colors
    val fractions = barFractions(values)
    val peak = fractions.indexOfFirst { it == 1f }
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
            val slot = size.width / fractions.size.coerceAtLeast(1)
            val bar = slot * BAR_FILL
            fractions.forEachIndexed { i, f ->
                val h = (size.height * f).coerceAtLeast(if (f > 0f) MIN_BAR_PX else 0f)
                drawRoundRect(
                    if (i == peak) colors.accent else colors.chartMuted,
                    topLeft = Offset(i * slot + (slot - bar) / 2, size.height - h),
                    size = Size(bar, h),
                    cornerRadius = CornerRadius(bar / 2, bar / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (label.isNotEmpty()) {
                        Text(
                            label,
                            Modifier.wrapContentWidth(unbounded = true),
                            style = axisStyle(),
                            color = colors.labelSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * [derived] Contribution-style heatmap: one column per week (oldest left), Monday at the top, four intensity levels
 * of the accent; empty days use the neutral fill. [days] is oldest first; [firstDayOffset] is how many cells of
 * the first week come before the first day (0 = it is a Monday).
 */
@Composable
fun ActivityHeatmap(
    days: List<Long>,
    firstDayOffset: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = Kavita.colors
    val levels = heatLevels(days)
    val weeks = (firstDayOffset + days.size + DAYS - 1) / DAYS
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(weeks.coerceAtLeast(1) / DAYS.toFloat())
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val cell = size.width / weeks.coerceAtLeast(1)
        val gap = cell * CELL_GAP
        levels.forEachIndexed { i, level ->
            val slot = i + firstDayOffset
            drawRoundRect(
                heatColor(level, colors.accent, colors.fillSegmented),
                topLeft = Offset(slot / DAYS * cell + gap / 2, slot % DAYS * cell + gap / 2),
                size = Size(cell - gap, cell - gap),
                cornerRadius = CornerRadius(cell * CELL_RADIUS, cell * CELL_RADIUS),
            )
        }
    }
}

/** "Less ■■■■■ More" key for [ActivityHeatmap]; decorative (the heatmap's description carries the meaning). */
@Composable
fun HeatmapLegend(
    less: String,
    more: String,
    modifier: Modifier = Modifier,
) = Row(
    modifier.clearAndSetSemantics { },
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val colors = Kavita.colors
    Text(less, style = axisStyle(), color = colors.labelSecondary)
    for (level in 0..HEAT_LEVELS) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(heatColor(level, colors.accent, colors.fillSegmented)),
        )
    }
    Text(more, style = axisStyle(), color = colors.labelSecondary)
}

/**
 * [derived] One horizontal bar split by share, with a dot legend ("Images · 72%"). Segments use the accent at
 * decreasing strength so they stay on-brand in both themes.
 */
@Composable
fun ShareBar(
    segments: List<Pair<String, Long>>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = Kavita.colors
    val total = segments.sumOf { it.second }.coerceAtLeast(1)
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segments.forEachIndexed { i, (_, value) ->
                if (value > 0) {
                    Box(Modifier.weight(value.toFloat()).height(12.dp).background(shareColor(i, colors.accent)))
                }
            }
        }
        segments.forEachIndexed { i, (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(shareColor(i, colors.accent)))
                Text(label, Modifier.weight(1f), style = Kavita.type.subhead, color = colors.label)
                Text(
                    "${value * PERCENT / total}%",
                    style = Kavita.type.subhead,
                    color = colors.labelSecondary,
                )
            }
        }
    }
}

/** [derived] Ranked list with a proportional bar under each name and the value on the right (top genres/tags). */
@Composable
fun RankedBars(
    items: List<Pair<String, String>>,
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = Kavita.colors
    val fractions = barFractions(values)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { i, (name, value) ->
            // One TalkBack stop per row: "Fantasy, 180".
            Column(
                Modifier.clearAndSetSemantics { this.contentDescription = "$name, $value" },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row {
                    Text(name, Modifier.weight(1f), style = Kavita.type.subhead, color = colors.label, maxLines = 1)
                    Text(value, style = Kavita.type.subhead, color = colors.labelSecondary)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colors.fillSegmented),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fractions.getOrElse(i) { 0f }.coerceAtLeast(MIN_RANK_FRACTION))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (i == 0) colors.accent else colors.chartMuted),
                    )
                }
            }
        }
    }
}

/** 0 for no reading, else 1–4 by quartile of the busiest day. */
internal fun heatLevels(days: List<Long>): List<Int> {
    val max = days.maxOrNull()?.takeIf { it > 0 } ?: return days.map { 0 }
    return days.map { if (it <= 0) 0 else ((it * HEAT_LEVELS + max - 1) / max).toInt().coerceIn(1, HEAT_LEVELS) }
}

private fun heatColor(
    level: Int,
    accent: Color,
    empty: Color,
) = if (level == 0) empty else accent.copy(alpha = HEAT_ALPHA_BASE + HEAT_ALPHA_STEP * level)

private fun shareColor(
    index: Int,
    accent: Color,
) = accent.copy(alpha = (1f - index * SHARE_ALPHA_STEP).coerceAtLeast(SHARE_ALPHA_MIN))

@Composable
private fun axisStyle() =
    Kavita.type.tabLabel.copy(
        fontWeight = FontWeight.SemiBold,
        // Axis ticks inside a fixed-height chart; the figures are in the chart's content description.
        fontSize =
            Kavita.type.tabLabel.fontSize
                .fixedScale(),
    )

private val ChartHeight = 80.dp
private const val BAR_FILL = 0.62f
private const val MIN_BAR_PX = 3f
private const val DAYS = 7
private const val CELL_GAP = 0.18f
private const val CELL_RADIUS = 0.2f
private const val HEAT_LEVELS = 4
private const val HEAT_ALPHA_BASE = 0.1f
private const val HEAT_ALPHA_STEP = 0.225f
private const val SHARE_ALPHA_STEP = 0.3f
private const val SHARE_ALPHA_MIN = 0.25f
private const val PERCENT = 100
private const val MIN_RANK_FRACTION = 0.02f
