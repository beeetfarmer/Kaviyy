package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita

/** DESIGN.md icons "clear" / "remove" / "add": filled r10 circle (24 viewBox) with a white glyph. */
enum class CircleGlyph { Clear, Remove, Add }

@Composable
fun CircleGlyphIcon(
    glyph: CircleGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val fill =
        when (glyph) {
            CircleGlyph.Clear -> Kavita.colors.labelTertiary
            CircleGlyph.Remove -> Kavita.colors.destructive
            CircleGlyph.Add -> Kavita.colors.accent
        }
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        drawCircle(fill, radius = 10f * u)

        fun line(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
        ) = drawLine(Color.White, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 2.2f * u, StrokeCap.Round)
        when (glyph) {
            CircleGlyph.Clear -> {
                line(9f, 9f, 15f, 15f)
                line(15f, 9f, 9f, 15f)
            }

            CircleGlyph.Remove -> {
                line(7.5f, 12f, 16.5f, 12f)
            }

            CircleGlyph.Add -> {
                line(12f, 7.5f, 12f, 16.5f)
                line(7.5f, 12f, 16.5f, 12f)
            }
        }
    }
}
