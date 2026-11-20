package com.nl.kaviyy

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.ComponentShowcase
import com.nl.kaviyy.core.designsystem.component.FilterChip
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.theme.AccentPreset
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

/** Debug-only component catalogue (M1.6). Replaced by real navigation from M3; never in release builds. */
@Composable
internal fun DevHome() {
    var appearance by rememberSaveable { mutableStateOf(Appearance.System) }
    var accent by rememberSaveable { mutableStateOf(AccentPreset.KavitaGreen) }
    KavitaTheme(appearance, accent) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Kavita.colors.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedControl(
                    options = Appearance.entries.map { it.name },
                    selectedIndex = appearance.ordinal,
                    onSelect = { appearance = Appearance.entries[it] },
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccentPreset.entries.forEach { preset ->
                        FilterChip(preset.name, selected = preset == accent, onClick = { accent = preset })
                    }
                }
            }
            ComponentShowcase(Modifier.fillMaxWidth())
        }
    }
}
