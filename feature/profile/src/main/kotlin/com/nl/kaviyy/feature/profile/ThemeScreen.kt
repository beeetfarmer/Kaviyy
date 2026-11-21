package com.nl.kaviyy.feature.profile

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.ThemeMode
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.AccentPreset
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing

@Composable
fun ThemeRoute(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ThemeScreen(
        state,
        ThemeActions(
            onBack = onBack,
            onTheme = viewModel::setTheme,
            onAccent = viewModel::setAccent,
            onDynamicColor = viewModel::setDynamicColor,
            onLanguage = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }
            },
        ),
        dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        languageSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    )
}

/** DESIGN.md §2.4 [derived]: appearance, accent presets, dynamic color; language opens the system per-app setting. */
@Composable
fun ThemeScreen(
    state: AppSettings,
    actions: ThemeActions,
    modifier: Modifier = Modifier,
    dynamicSupported: Boolean = true,
    languageSupported: Boolean = true,
) = Column(
    modifier
        .fillMaxSize()
        .background(Kavita.colors.background)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = KavitaSpacing.screenHorizontal)
        .padding(bottom = KavitaSpacing.contentBottomPadding),
    verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
) {
    CircleIconButton(
        KavitaIcons.ChevronLeft,
        stringResource(R.string.you_back),
        actions.onBack,
        Modifier.padding(top = 8.dp),
    )
    LargeTitleHeader(stringResource(R.string.theme_title))
    Column {
        SectionHeader(stringResource(R.string.theme_appearance))
        SegmentedControl(
            listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
            ),
            state.theme.ordinal,
            { actions.onTheme(ThemeMode.entries[it]) },
        )
    }
    val accentOff = dynamicSupported && state.dynamicColor
    Column {
        SectionHeader(stringResource(R.string.theme_accent))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(KavitaShapes.card)
                .background(Kavita.colors.surface)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val dark = Kavita.colors.isDark
            AccentPreset.entries.forEach { preset ->
                Swatch(
                    preset,
                    if (dark) preset.dark.accent else preset.light.accent,
                    selected = !accentOff && preset.name == state.accent,
                    onClick = { actions.onAccent(preset.name) },
                )
            }
        }
        if (dynamicSupported) {
            GroupedList(Modifier.padding(top = 12.dp)) {
                GroupedSwitchRow(stringResource(R.string.theme_dynamic), state.dynamicColor, actions.onDynamicColor)
            }
        }
    }
    if (languageSupported) {
        GroupedList {
            GroupedRow(
                stringResource(R.string.theme_language),
                value = LocalConfiguration.current.locales[0].let { it.getDisplayLanguage(it) },
                onClick = actions.onLanguage,
            )
        }
    }
}

/** 36 visual, 48 hit area; a ring marks the selection. */
@Composable
private fun Swatch(
    preset: AccentPreset,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(accentName(preset))
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .then(if (selected) Modifier.border(2.dp, Kavita.colors.label, CircleShape) else Modifier)
                .padding(if (selected) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(KavitaIcons.Check, null, Modifier.size(16.dp), tint = Kavita.colors.onAccent)
        }
    }
}

private fun accentName(preset: AccentPreset) =
    when (preset) {
        AccentPreset.KavitaGreen -> R.string.theme_accent_green
        AccentPreset.Ocean -> R.string.theme_accent_ocean
        AccentPreset.Ember -> R.string.theme_accent_ember
        AccentPreset.Plum -> R.string.theme_accent_plum
        AccentPreset.Slate -> R.string.theme_accent_slate
    }
