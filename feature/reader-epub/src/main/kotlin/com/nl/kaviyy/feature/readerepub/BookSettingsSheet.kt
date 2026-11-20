package com.nl.kaviyy.feature.readerepub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes

private const val SIZE_STEP = 10
private const val SPACING_STEP = 10
private const val MARGIN_STEP = 5
private const val DEFAULT_BRIGHTNESS = 0.5f

/** "Aa" sheet [derived] (DESIGN.md §7): font, size, line height, margins, paged/scroll, theme swatches, brightness. */
@Composable
internal fun BookSettingsSheet(
    settings: BookSettings,
    actions: EpubReaderActions,
) {
    val close = { actions.onShowSettings(false) }
    val set = actions.onSettings
    KavitaBottomSheet(onDismiss = close) {
        SheetHeader(stringResource(R.string.epub_settings), stringResource(R.string.epub_done), close)
        Column(
            Modifier
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 32.dp,
                ).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Themes(settings.theme) { set(settings.copy(theme = it)) }
            Label(R.string.epub_font)
            SegmentedControl(
                listOf(
                    R.string.epub_font_literata,
                    R.string.epub_font_inter,
                    R.string.epub_font_serif,
                ).map { stringResource(it) },
                settings.font.ordinal,
                { set(settings.copy(font = BookFont.entries[it])) },
            )
            Label(R.string.epub_layout)
            SegmentedControl(
                listOf(stringResource(R.string.epub_paged), stringResource(R.string.epub_scroll)),
                if (settings.scroll) 1 else 0,
                { set(settings.copy(scroll = it == 1)) },
            )
            GroupedList {
                Stepper(R.string.epub_size, "${settings.fontSize}%", divider = true) { up ->
                    set(
                        settings.copy(
                            fontSize =
                                (settings.fontSize + if (up) SIZE_STEP else -SIZE_STEP).coerceIn(
                                    FONT_SIZE_MIN,
                                    FONT_SIZE_MAX,
                                ),
                        ),
                    )
                }
                Stepper(R.string.epub_line_height, "${settings.lineSpacing}%", divider = true) { up ->
                    val next = settings.lineSpacing + if (up) SPACING_STEP else -SPACING_STEP
                    set(settings.copy(lineSpacing = next.coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX)))
                }
                Stepper(R.string.epub_margins, "${settings.margin}") { up ->
                    set(
                        settings.copy(
                            margin = (settings.margin + if (up) MARGIN_STEP else -MARGIN_STEP).coerceIn(0, MARGIN_MAX),
                        ),
                    )
                }
            }
            GroupedList {
                GroupedSwitchRow(stringResource(R.string.epub_keep_screen_on), settings.keepScreenOn, {
                    set(settings.copy(keepScreenOn = it))
                }, divider = true)
                GroupedSwitchRow(
                    stringResource(R.string.epub_system_brightness),
                    settings.brightness == null,
                    { system -> set(settings.copy(brightness = if (system) null else DEFAULT_BRIGHTNESS)) },
                    divider = settings.brightness != null,
                )
                settings.brightness?.let { level ->
                    val label = stringResource(R.string.epub_brightness)
                    Slider(
                        value = level,
                        onValueChange = { set(settings.copy(brightness = it)) },
                        modifier =
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics {
                                contentDescription =
                                    label
                            },
                        valueRange = 0.05f..1f,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Kavita.colors.accent,
                                inactiveTrackColor = Kavita.colors.separator,
                            ),
                    )
                }
            }
        }
    }
}

/** Paper / White / Night swatches: a radiogroup of 56dp circles in each theme's colours. */
@Composable
private fun Themes(
    selected: BookTheme,
    onSelect: (BookTheme) -> Unit,
) = Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
    BookTheme.entries.forEach { theme ->
        val on = theme == selected
        Column(
            Modifier.selectable(on, role = Role.RadioButton) { onSelect(theme) }.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(KavitaShapes.pill)
                    .background(theme.colors.background)
                    .border(
                        if (on) 3.dp else 1.dp,
                        if (on) Kavita.colors.accent else Kavita.colors.separator,
                        KavitaShapes.pill,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.epub_settings_glyph),
                    style = Kavita.type.callout,
                    color = theme.colors.foreground,
                )
            }
            Text(stringResource(theme.label()), style = Kavita.type.footnote, color = Kavita.colors.label)
        }
    }
}

private fun BookTheme.label() =
    when (this) {
        BookTheme.Paper -> R.string.epub_theme_paper
        BookTheme.White -> R.string.epub_theme_white
        BookTheme.Night -> R.string.epub_theme_night
    }

/** Row with − value + buttons (48dp targets). */
@Composable
private fun Stepper(
    label: Int,
    value: String,
    divider: Boolean = false,
    onStep: (up: Boolean) -> Unit,
) {
    val name = stringResource(label)
    GroupedRow(name, divider = divider) {
        Row(
            Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CircleIconButton(KavitaIcons.Minus, stringResource(R.string.epub_smaller, name), {
                onStep(false)
            }, modifier = Modifier.size(36.dp))
            Text(value, style = Kavita.type.body, color = Kavita.colors.label)
            CircleIconButton(KavitaIcons.Plus, stringResource(R.string.epub_larger, name), {
                onStep(true)
            }, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun Label(text: Int) =
    Text(stringResource(text), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
