package com.nl.kaviyy.feature.profile

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nl.kaviyy.core.data.AllStats
import com.nl.kaviyy.core.data.ReadFormat
import com.nl.kaviyy.core.designsystem.component.ActivityHeatmap
import com.nl.kaviyy.core.designsystem.component.ColumnChart
import com.nl.kaviyy.core.designsystem.component.HeatmapLegend
import com.nl.kaviyy.core.designsystem.component.RankedBars
import com.nl.kaviyy.core.designsystem.component.ShareBar
import com.nl.kaviyy.core.designsystem.component.StatBars
import com.nl.kaviyy.core.designsystem.theme.Kavita
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Heatmap shows the last 26 weeks (fits a phone's width); streaks use the whole year. */
private const val HEATMAP_WEEKS = 26
private const val DAYS_IN_WEEK = 7
private const val HOUR_TICK = 6

@Composable
internal fun locale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun CardTitle(text: String) = Text(text, style = Kavita.type.headline, color = Kavita.colors.label)

@Composable
internal fun Footnote(text: String) = Text(text, style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)

@Composable
internal fun TotalsCard(s: AllStats) =
    StatCard {
        val number = NumberFormat.getIntegerInstance(locale())
        Row {
            Stat(stringResource(R.string.you_hours, s.hours.toInt()), stringResource(R.string.you_time_reading))
            Stat(number.format(s.pages), stringResource(R.string.you_pages_read))
            Stat(number.format(s.words), stringResource(R.string.you_words_read))
        }
        Footnote(stringResource(R.string.you_hours_per_week, s.hoursPerWeek))
    }

/** Streaks and reading days, then lifetime books/comics/authors when the server reports them. */
@Composable
internal fun HighlightsCard(s: AllStats) =
    StatCard {
        if (s.activity.isNotEmpty()) {
            Row {
                Stat(days(s.streaks.current), stringResource(R.string.you_streak_current))
                Stat(days(s.streaks.longest), stringResource(R.string.you_streak_longest))
                Stat(s.activity.count { it > 0 }.toString(), stringResource(R.string.you_active_days))
            }
        }
        s.counts?.let { c ->
            Row {
                Stat(c.books.toString(), stringResource(R.string.you_books_read))
                Stat(c.comics.toString(), stringResource(R.string.you_comics_read))
                Stat(c.authors.toString(), stringResource(R.string.you_authors))
            }
            Footnote(
                listOf(
                    pluralStringResource(R.plurals.you_ratings, c.ratings, c.ratings),
                    pluralStringResource(R.plurals.you_reviews, c.reviews, c.reviews),
                ).joinToString(" · "),
            )
        }
    }

@Composable
private fun days(n: Int) = pluralStringResource(R.plurals.you_days_short, n, n)

/** GitHub-style grid of the last 26 weeks, Monday at the top, ending with today. */
@Composable
internal fun ActivityCard(
    s: AllStats,
    end: java.time.LocalDate,
) = StatCard {
    CardTitle(stringResource(R.string.you_activity))
    // Today's position in its week (Mon = 0) decides how much of the current column is filled.
    val today = end.dayOfWeek.value - 1
    val cells = (HEATMAP_WEEKS - 1) * DAYS_IN_WEEK + today + 1
    val window = s.activity.takeLast(cells)
    val active = window.count { it > 0 }
    ActivityHeatmap(
        window,
        firstDayOffset = cells - window.size,
        contentDescription = stringResource(R.string.you_activity_a11y, active, s.streaks.current),
    )
    Row {
        Footnote(pluralStringResource(R.plurals.you_active_days_window, active, active))
        androidx.compose.foundation.layout
            .Spacer(
                androidx.compose.ui.Modifier
                    .weight(1f),
            )
        HeatmapLegend(stringResource(R.string.you_less), stringResource(R.string.you_more))
    }
}

@Composable
internal fun MonthsCard(months: List<Pair<YearMonth, Long>>) =
    StatCard {
        val title = stringResource(R.string.you_reads_by_month)
        CardTitle(title)
        val loc = locale()
        val short = months.joinToString { (m, n) -> "${m.month.getDisplayName(TextStyle.SHORT, loc)} $n" }
        ColumnChart(
            months.map { it.second.toFloat() },
            months.map { it.first.month.getDisplayName(TextStyle.NARROW, loc) },
            "$title: $short",
        )
    }

@Composable
internal fun HoursCard(byHour: List<Long>) =
    StatCard {
        val title = stringResource(R.string.you_by_hour)
        CardTitle(title)
        val loc = locale()
        // "6 AM" or "06" per the user's 12/24h locale.
        val format = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(loc, "j"), loc)
        val hour = { h: Int -> LocalTime.of(h, 0).format(format) }
        val peak = byHour.indices.maxBy { byHour[it] }
        val summary = stringResource(R.string.you_peak_hour, hour(peak))
        ColumnChart(
            byHour.map { it.toFloat() },
            byHour.indices.map { if (it % HOUR_TICK == 0) hour(it) else "" },
            "$title. $summary",
        )
        Footnote(summary)
    }
