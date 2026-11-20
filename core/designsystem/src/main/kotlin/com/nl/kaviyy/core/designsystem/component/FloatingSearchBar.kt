package com.nl.kaviyy.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing

private val BAR_HEIGHT = 56.dp

/**
 * Search screen bottom bar (DESIGN.md §6): Home button (56) + search field pill (56, 2dp accent ring when focused),
 * above the keyboard. Replaces [FloatingTabBar] while Search is open.
 */
@Composable
fun FloatingSearchBar(
    state: TextFieldState,
    label: String,
    homeLabel: String,
    clearLabel: String,
    onHome: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val colors = Kavita.colors
    val ring = if (colors.isDark) colors.hairline else Color.Black.copy(alpha = 0.10f)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    fun Modifier.glass() =
        cssShadow(KavitaShapes.pill, 10.dp, 30.dp, colors.tabBarShadow.alpha)
            .clip(KavitaShapes.pill)
            .background(colors.tabBar)
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(
                start = KavitaSpacing.tabBarSideInset,
                end = KavitaSpacing.tabBarSideInset,
                bottom = KavitaSpacing.tabBarBottomInset,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(BAR_HEIGHT)
                .glass()
                .border(0.5.dp, ring, KavitaShapes.pill)
                .clickable(role = Role.Button, onClick = onHome),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KavitaIcons.Home, homeLabel, Modifier.size(22.dp), tint = colors.tabInactive)
        }
        Row(
            Modifier
                .weight(1f)
                .height(BAR_HEIGHT)
                .glass()
                .border(if (focused) 2.dp else 0.5.dp, if (focused) colors.accent else ring, KavitaShapes.pill)
                .padding(start = 18.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KavitaIcons.Search, null, Modifier.size(20.dp), tint = colors.labelSecondary)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty()) {
                    Text(label, style = Kavita.type.bodyRegular, color = colors.placeholder, maxLines = 1)
                }
                BasicTextField(
                    state,
                    Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { contentDescription = label },
                    textStyle = Kavita.type.bodyRegular.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.accent),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = KeyboardActionHandler { onSubmit() },
                    interactionSource = interaction,
                )
            }
            if (state.text.isNotEmpty()) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(KavitaShapes.pill)
                        .clickable(role = Role.Button) { state.clearText() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(KavitaShapes.pill).background(colors.labelTertiary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(KavitaIcons.Close, clearLabel, Modifier.size(12.dp), tint = colors.surface)
                    }
                }
            }
        }
    }
}
