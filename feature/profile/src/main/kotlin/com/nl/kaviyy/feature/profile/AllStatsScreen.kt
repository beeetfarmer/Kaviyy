package com.nl.kaviyy.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.AllStats
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.StatBars
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.format.TextStyle

/** StatBars shows 7 bars; pages per year shows the latest years. */
private const val YEARS_SHOWN = 7

@Composable
fun AllStatsRoute(
    onBack: () -> Unit,
    viewModel: AllStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AllStatsScreen(state, onBack)
}

/** [derived] All Stats: totals card (same stat style as This Week), bars for years and weekdays, top genres. */
@Composable
fun AllStatsScreen(
    state: AllStatsUiState,
    onBack: () -> Unit,
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
    CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.you_back), onBack, Modifier.padding(top = 8.dp))
    LargeTitleHeader(stringResource(R.string.you_all_stats))
    val s = state.stats
    when {
        s != null -> {
            Stats(s)
        }

        state.failed -> {
            Text(
                stringResource(R.string.you_stats_unavailable),
                style = Kavita.type.callout,
                color = Kavita.colors.labelSecondary,
            )
        }

        else -> {
            Text(
                stringResource(R.string.you_loading),
                style = Kavita.type.callout,
                color = Kavita.colors.labelSecondary,
            )
        }
    }
    Spacer(Modifier.height(KavitaSpacing.contentBottomPadding))
}

@Composable
private fun Stats(s: AllStats) {
    val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    Card {
        Row {
            Stat(stringResource(R.string.you_hours, s.hours.toInt()), stringResource(R.string.you_time_reading))
            Stat(number.format(s.pages), stringResource(R.string.you_pages_read))
            Stat(number.format(s.words), stringResource(R.string.you_words_read))
        }
        Text(
            stringResource(R.string.you_hours_per_week, s.hoursPerWeek),
            style = Kavita.type.footnote,
            color = Kavita.colors.labelSecondary,
        )
    }
    val years = s.pagesPerYear.takeLast(YEARS_SHOWN)
    if (years.isNotEmpty()) {
        Card {
            Text(stringResource(R.string.you_pages_per_year), style = Kavita.type.headline, color = Kavita.colors.label)
            StatBars(
                years.map { it.second.toFloat() },
                years.map { "'" + (it.first % 100).toString().padStart(2, '0') },
                stringResource(R.string.you_pages_per_year),
            )
        }
    }
    Card {
        Text(stringResource(R.string.you_by_weekday), style = Kavita.type.headline, color = Kavita.colors.label)
        StatBars(
            s.byWeekday.map { it.toFloat() },
            DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0]) },
            stringResource(R.string.you_by_weekday),
        )
    }
    if (s.genres.isNotEmpty()) {
        Column {
            SectionHeader(stringResource(R.string.you_top_genres))
            GroupedList {
                s.genres.forEachIndexed { i, (genre, count) ->
                    GroupedRow(genre, value = number.format(count), divider = i < s.genres.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) =
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KavitaShapes.card)
            .background(Kavita.colors.surface)
            .padding(KavitaSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(
    value: String,
    caption: String,
) = Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        value,
        style = Kavita.type.title2.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
        color = Kavita.colors.label,
    )
    Text(caption, style = Kavita.type.caption, color = Kavita.colors.labelSecondary)
}
