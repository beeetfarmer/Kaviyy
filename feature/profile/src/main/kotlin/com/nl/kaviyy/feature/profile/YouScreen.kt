package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.WeekStats
import com.nl.kaviyy.core.datastore.ThemeMode
import com.nl.kaviyy.core.designsystem.component.Avatar
import com.nl.kaviyy.core.designsystem.component.Chevron
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SettingsRow
import com.nl.kaviyy.core.designsystem.component.StatBars
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.designsystem.theme.SettingsTileColors
import java.time.format.TextStyle

@Composable
fun YouRoute(
    actions: YouActions,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    YouScreen(state, actions)
}

/** Screen 10 (DESIGN.md §7): title, profile card, This Week, then the settings groups. */
@Composable
fun YouScreen(
    state: YouUiState,
    actions: YouActions,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .fillMaxSize()
        .background(Kavita.colors.background)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = KavitaSpacing.screenHorizontal),
    verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
) {
    LargeTitleHeader(stringResource(R.string.you_title), Modifier.padding(top = KavitaSpacing.topBelowStatusBar))
    ProfileCard(state, actions.onProfile)
    WeekCard(state.week, state.statsFailed, actions.onAllStats)
    GroupedList {
        SettingsRow(
            KavitaIcons.Sliders,
            SettingsTileColors.reader,
            stringResource(R.string.you_settings_reader),
            actions.onReaderPrefs,
            divider = true,
        )
        SettingsRow(
            KavitaIcons.Moon,
            SettingsTileColors.theme,
            stringResource(R.string.you_settings_theme),
            actions.onTheme,
            value = stringResource(themeLabel(state.theme)),
        )
    }
    GroupedList {
        SettingsRow(
            KavitaIcons.Server,
            SettingsTileColors.servers,
            stringResource(R.string.you_settings_servers),
            actions.onServers,
            value = state.server?.name,
        )
    }
    AccountGroup(actions)
    Spacer(Modifier.height(KavitaSpacing.contentBottomPadding))
}

/** 56 avatar, name 18/700, "Admin · host", chevron. */
@Composable
private fun ProfileCard(
    state: YouUiState,
    onClick: () -> Unit,
) {
    val name = state.account?.username ?: state.server?.username.orEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KavitaShapes.card)
            .background(Kavita.colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(KavitaSpacing.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initials(name), null, size = 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = Kavita.type.headline, color = Kavita.colors.label, maxLines = 1)
            val role =
                stringResource(
                    if (state.account?.admin ==
                        true
                    ) {
                        R.string.you_role_admin
                    } else {
                        R.string.you_role_user
                    },
                )
            Text(
                listOfNotNull(role, state.server?.url?.host).joinToString(" · "),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
                maxLines = 1,
            )
        }
        Chevron()
    }
}

internal fun initials(name: String): String =
    name
        .split(' ', '.', '_', '-')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

/** "This Week": three stats (22/800 over 12 captions), StatBars, "All Stats". */
@Composable
private fun WeekCard(
    week: WeekStats?,
    failed: Boolean,
    onAllStats: () -> Unit,
) = Column(
    Modifier
        .fillMaxWidth()
        .clip(KavitaShapes.card)
        .background(Kavita.colors.surface)
        .padding(KavitaSpacing.cardPadding),
    verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.you_this_week),
            Modifier.weight(1f),
            style = Kavita.type.headline,
            color = Kavita.colors.label,
        )
        Text(
            stringResource(R.string.you_all_stats),
            Modifier.clickable(role = Role.Button, onClick = onAllStats).padding(vertical = 12.dp, horizontal = 4.dp),
            style = Kavita.type.callout.copy(fontWeight = FontWeight.Medium),
            color = Kavita.colors.accent,
        )
    }
    Row {
        Stat(week?.let { stringResource(R.string.you_hours, it.hours) }, stringResource(R.string.you_time_reading))
        Stat(week?.pages?.toString(), stringResource(R.string.you_pages_read))
        Stat(week?.chapters?.toString(), stringResource(R.string.you_chapters))
    }
    if (week != null) {
        val labels =
            week.days.map {
                it.dayOfWeek.getDisplayName(
                    TextStyle.NARROW,
                    LocalConfiguration.current.locales[0],
                )
            }
        StatBars(
            week.perDay.map { it.toFloat() },
            labels,
            stringResource(R.string.you_week_bars, week.perDay.sum()),
        )
    } else if (failed) {
        Text(
            stringResource(R.string.you_stats_unavailable),
            style = Kavita.type.footnote,
            color = Kavita.colors.labelSecondary,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(
    value: String?,
    caption: String,
) = Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        value ?: "–",
        style = Kavita.type.title2.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
        color = Kavita.colors.label,
    )
    Text(caption, style = Kavita.type.caption, color = Kavita.colors.labelSecondary)
}

/** [derived] Group 3 (Account / Privacy pages land with M14.4–5): sign out and delete local data (two taps). */
@Composable
private fun AccountGroup(actions: YouActions) {
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    GroupedList {
        GroupedRow(
            stringResource(R.string.you_sign_out),
            onClick = actions.onSignOut,
            showChevron = false,
            divider = true,
        )
        GroupedRow(
            stringResource(if (confirmWipe) R.string.you_delete_local_data_confirm else R.string.you_delete_local_data),
            onClick = { if (confirmWipe) actions.onDeleteLocalData() else confirmWipe = true },
            showChevron = false,
            labelColor = Kavita.colors.destructive,
        )
    }
}

internal fun themeLabel(mode: ThemeMode) =
    when (mode) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }
