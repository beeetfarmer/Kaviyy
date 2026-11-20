package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

/** The canvas' `box-shadow: 0 0 0 0.5px hairline` ring around white pills and circle buttons. */
@Composable
fun Modifier.hairline(shape: Shape = KavitaShapes.pill): Modifier = border(0.5.dp, Kavita.colors.hairline, shape)

/** CSS-style `box-shadow: 0 {y} {blur} rgba(0,0,0,{alpha})`. */
fun Modifier.cssShadow(
    shape: Shape,
    y: Dp,
    blur: Dp,
    alpha: Float,
    color: Color = Color.Black,
): Modifier = dropShadow(shape, Shadow(radius = blur, color = color.copy(alpha = alpha), offset = DpOffset(0.dp, y)))
