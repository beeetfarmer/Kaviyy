package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

private const val DISABLED_ALPHA = 0.4f

/** DESIGN.md §5 CircleIconButton variants. Visual size only: Compose expands the touch target to 48dp. */
enum class CircleIconButtonStyle(
    val size: Dp,
    val iconSize: Dp,
) {
    /** 36, `surface` + hairline, accent icon (headers). */
    Surface(36.dp, 18.dp),

    /** 40, translucent white over a colored hero (series detail). */
    Translucent(40.dp, 20.dp),

    /** 44, reader chrome. */
    Chrome(44.dp, 20.dp),

    /** 50, series action (Want to Read, Download). */
    Action(50.dp, 20.dp),
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CircleIconButtonStyle = CircleIconButtonStyle.Surface,
    containerColor: Color? = null,
    contentColor: Color? = null,
    enabled: Boolean = true,
) {
    val colors = Kavita.colors
    val (bg, fg) =
        when (style) {
            CircleIconButtonStyle.Translucent -> Color.White.copy(alpha = 0.22f) to Color.White
            CircleIconButtonStyle.Chrome -> Color.Transparent to Color.White
            else -> colors.surface to colors.accent
        }
    val ring = style == CircleIconButtonStyle.Surface || style == CircleIconButtonStyle.Action
    Box(
        modifier =
            modifier
                .size(style.size)
                .clip(KavitaShapes.pill)
                .background(containerColor ?: bg)
                .then(if (ring) Modifier.hairline() else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(style.iconSize), tint = contentColor ?: fg)
    }
}

/** Heights 40 (card), 50, 54 (DESIGN.md §5 PrimaryButton). */
enum class ButtonSize(
    val height: Dp,
    val iconSize: Dp,
    val padding: PaddingValues,
) {
    Small(40.dp, 14.dp, PaddingValues(start = 14.dp, end = 18.dp)),
    Medium(50.dp, 15.dp, PaddingValues(horizontal = 20.dp)),
    Large(54.dp, 18.dp, PaddingValues(horizontal = 24.dp)),
}

@Composable
private fun ButtonSize.textStyle(): TextStyle =
    when (this) {
        ButtonSize.Small -> Kavita.type.callout.copy(fontWeight = FontWeight.SemiBold)
        ButtonSize.Medium -> Kavita.type.bodyStrong
        ButtonSize.Large -> Kavita.type.buttonLarge
    }

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    size: ButtonSize,
    leadingIcon: ImageVector?,
    enabled: Boolean,
    container: Color,
    content: Color,
    ring: Boolean,
) {
    Row(
        modifier =
            modifier
                .height(size.height)
                .clip(KavitaShapes.pill)
                .background(container)
                .then(if (ring) Modifier.hairline() else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(size.padding),
        horizontalArrangement =
            Arrangement.spacedBy(
                if (size ==
                    ButtonSize.Small
                ) {
                    6.dp
                } else {
                    8.dp
                },
                Alignment.CenterHorizontally,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(size.iconSize), tint = content)
        Text(text, style = size.textStyle(), color = content, maxLines = 1)
    }
}

/** Pill, accent fill, `onAccent` text. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Large,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) = PillButton(
    text,
    onClick,
    modifier,
    size,
    leadingIcon,
    enabled,
    Kavita.colors.accent,
    Kavita.colors.onAccent,
    ring = false,
)

/** Pill, `surface` fill, accent text (destructive red when [destructive]), hairline ring. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Large,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) = PillButton(
    text,
    onClick,
    modifier,
    size,
    leadingIcon,
    enabled,
    Kavita.colors.surface,
    if (destructive) Kavita.colors.destructiveText else Kavita.colors.accent,
    ring = true,
)
