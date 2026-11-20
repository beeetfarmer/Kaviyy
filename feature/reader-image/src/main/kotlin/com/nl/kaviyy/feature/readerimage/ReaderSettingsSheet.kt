package com.nl.kaviyy.feature.readerimage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

private val FITS = listOf(SCALING_AUTOMATIC, SCALING_FIT_WIDTH, SCALING_FIT_HEIGHT, SCALING_ORIGINAL)
private val BACKGROUNDS = listOf("#000000", "#3A3A3C", "#FFFFFF")
private const val DEFAULT_BRIGHTNESS = 0.5f

/** Kavita's hex background; anything unparseable falls back to black. */
internal fun ReaderSettings.backgroundColor(): Color =
    runCatching { Color(android.graphics.Color.parseColor(background)) }.getOrDefault(Color.Black)

/**
 * Settings sheet [derived] (DESIGN.md §296), always dark like the reader. Fit, background, swipe and auto-close are
 * Kavita reading-profile fields; keep screen on and brightness are device-only.
 */
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    actions: ImageReaderActions,
) = KavitaTheme(appearance = Appearance.Dark) {
    val close = { actions.onShowSettings(false) }
    val set = actions.onSettings
    KavitaBottomSheet(onDismiss = close) {
        SheetHeader(stringResource(R.string.reader_settings), stringResource(R.string.reader_done), close)
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
            Label(R.string.reader_fit)
            SegmentedControl(
                listOf(
                    R.string.reader_fit_auto,
                    R.string.reader_fit_width,
                    R.string.reader_fit_height,
                    R.string.reader_fit_original,
                ).map { stringResource(it) },
                FITS.indexOf(settings.scaling).coerceAtLeast(0),
                { set(settings.copy(scaling = FITS[it])) },
            )
            Label(R.string.reader_background)
            SegmentedControl(
                listOf(
                    R.string.reader_background_black,
                    R.string.reader_background_gray,
                    R.string.reader_background_white,
                ).map { stringResource(it) },
                BACKGROUNDS.indexOfFirst { it.equals(settings.background, ignoreCase = true) }.coerceAtLeast(0),
                { set(settings.copy(background = BACKGROUNDS[it])) },
            )
            GroupedList {
                GroupedSwitchRow(stringResource(R.string.reader_swipe), settings.swipeToTurn, {
                    set(settings.copy(swipeToTurn = it))
                }, divider = true)
                GroupedSwitchRow(stringResource(R.string.reader_auto_close), settings.autoCloseMenu, {
                    set(settings.copy(autoCloseMenu = it))
                })
            }
            GroupedList {
                GroupedSwitchRow(stringResource(R.string.reader_keep_screen_on), settings.keepScreenOn, {
                    set(settings.copy(keepScreenOn = it))
                }, divider = true)
                GroupedSwitchRow(
                    stringResource(R.string.reader_system_brightness),
                    settings.brightness == null,
                    { system -> set(settings.copy(brightness = if (system) null else DEFAULT_BRIGHTNESS)) },
                    divider = settings.brightness != null,
                )
                settings.brightness?.let { level ->
                    val label = stringResource(R.string.reader_brightness)
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
            Text(
                stringResource(R.string.reader_device_note),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
    }
}

@Composable
private fun Label(text: Int) =
    Text(stringResource(text), style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
