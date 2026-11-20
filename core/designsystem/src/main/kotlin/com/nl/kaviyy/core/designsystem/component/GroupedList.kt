package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing

/** `surface` card, radius 16. Put rows inside; pass `divider = true` to every row but the last. */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(KavitaShapes.groupedList).background(Kavita.colors.surface),
        content = content,
    )
}

/** 0.5dp hairline at the bottom, inset from the leading content. */
@Composable
private fun Modifier.rowDivider(
    show: Boolean,
    inset: Dp,
    color: Color = Kavita.colors.separator,
): Modifier =
    if (!show) {
        this
    } else {
        drawBehind {
            val y = size.height - 0.5.dp.toPx() / 2
            drawLine(color, Offset(inset.toPx(), y), Offset(size.width, y), 0.5.dp.toPx())
        }
    }

@Composable
private fun Modifier.rowClick(onClick: (() -> Unit)?): Modifier =
    if (onClick != null) clickable(role = Role.Button, onClick = onClick) else this

@Composable
private fun Chevron() = Icon(KavitaIcons.ChevronRight, null, Modifier.size(14.dp), tint = Kavita.colors.labelTertiary)

/** Generic 52 high row: label, optional value, optional trailing slot and chevron. */
@Composable
fun GroupedRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    divider: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Kavita.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = KavitaSpacing.rowHeight)
                .rowClick(onClick)
                .rowDivider(divider, 16.dp)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Kavita.type.body, color = colors.label, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = Kavita.type.callout, color = colors.labelSecondary, maxLines = 1)
        trailing()
        if (showChevron) Chevron()
    }
}

/**
 * Form row: fixed-width label (82) + inline text field + optional trailing (e.g. reachability icon).
 * [secure] uses [BasicSecureTextField] (no copy, masked, never offered to IME suggestions).
 */
@Composable
fun GroupedInputRow(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secure: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    divider: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Kavita.colors
    val fieldModifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
    val textStyle = Kavita.type.bodyRegular.copy(color = colors.label)
    val cursor = SolidColor(colors.accent)
    val decorator =
        TextFieldDecorator { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty() && placeholder != null) {
                    Text(placeholder, style = Kavita.type.bodyRegular, color = colors.placeholder)
                }
                field()
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(KavitaSpacing.rowHeight)
                .rowDivider(divider, 16.dp, colors.separatorForm)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Kavita.type.body, color = colors.label, modifier = Modifier.width(82.dp))
        Box(Modifier.weight(1f)) {
            if (secure) {
                BasicSecureTextField(
                    state = state,
                    modifier = fieldModifier,
                    textStyle = textStyle,
                    cursorBrush = cursor,
                    keyboardOptions = keyboardOptions,
                    onKeyboardAction = onKeyboardAction,
                    decorator = decorator,
                )
            } else {
                BasicTextField(
                    state = state,
                    modifier = fieldModifier,
                    textStyle = textStyle,
                    cursorBrush = cursor,
                    keyboardOptions = keyboardOptions,
                    onKeyboardAction = onKeyboardAction,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    decorator = decorator,
                )
            }
        }
        trailing()
    }
}

/** Label + [KavitaSwitch]; the whole row toggles. */
@Composable
fun GroupedSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(KavitaSpacing.rowHeight)
                .rowDivider(divider, 16.dp)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Kavita.type.body, color = Kavita.colors.label, modifier = Modifier.weight(1f))
        KavitaSwitch(checked, onCheckedChange, contentDescription = label)
    }
}

/** Thumbnail (40×60) + title/subtitle + state text + chevron (volumes, chapters, downloads). */
@Composable
fun GroupedThumbRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    state: String? = null,
    stateColor: Color = Kavita.colors.labelSecondary,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
    thumbnail: @Composable () -> Unit,
) {
    val colors = Kavita.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .rowClick(onClick)
                .rowDivider(divider, 0.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        thumbnail()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = Kavita.type.bodyStrong,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = Kavita.type.footnote,
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state !=
            null
        ) {
            Text(
                state,
                style = Kavita.type.footnote.copy(fontWeight = Kavita.type.bodyStrong.fontWeight),
                color = stateColor,
            )
        }
        if (onClick != null) Chevron()
    }
}

/** 50 high settings row: 30×30 colored tile with a white glyph, label, value, chevron (Profile canvas). */
@Composable
fun SettingsRow(
    icon: ImageVector,
    tileColor: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    divider: Boolean = false,
) {
    val colors = Kavita.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(KavitaSpacing.settingsRowHeight)
                .rowClick(onClick)
                // Divider sits under label + value only: 14 padding + 30 tile + 12 gap.
                .rowDivider(divider, 56.dp)
                .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(KavitaShapes.settingsTile).background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(17.dp), tint = Color.White)
        }
        Text(label, style = Kavita.type.bodyRegular, color = colors.label, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (value != null) Text(value, style = Kavita.type.callout, color = colors.labelSecondary, maxLines = 1)
            Icon(KavitaIcons.ChevronRight, null, Modifier.size(12.dp), tint = colors.labelTertiary)
        }
    }
}
