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

@Composable
internal fun WeekdayCard(byWeekday: List<Long>) =
    StatCard {
        CardTitle(stringResource(R.string.you_by_weekday))
        val loc = locale()
        StatBars(
            byWeekday.map { it.toFloat() },
            DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, loc) },
            stringResource(R.string.you_by_weekday) + ": " +
                DayOfWeek.entries.zip(byWeekday).joinToString { (d, n) ->
                    "${d.getDisplayName(TextStyle.SHORT, loc)} $n"
                },
        )
    }

@Composable
internal fun YearsCard(years: List<Pair<Int, Long>>) =
    StatCard {
        CardTitle(stringResource(R.string.you_pages_per_year))
        StatBars(
            years.map { it.second.toFloat() },
            years.map { "'" + (it.first % 100).toString().padStart(2, '0') },
            stringResource(R.string.you_pages_per_year) + ": " + years.joinToString { "${it.first} ${it.second}" },
        )
    }

@Composable
internal fun FormatsCard(formats: List<Pair<ReadFormat, Long>>) =
    StatCard {
        val title = stringResource(R.string.you_formats)
        CardTitle(title)
        val labels = formats.map { formatLabel(it.first) to it.second }
        val total = formats.sumOf { it.second }.coerceAtLeast(1)
        ShareBar(labels, "$title: " + labels.joinToString { "${it.first} ${it.second * 100 / total}%" })
    }

@Composable
private fun formatLabel(f: ReadFormat) =
    stringResource(
        when (f) {
            ReadFormat.Images -> R.string.you_format_images
            ReadFormat.Epub -> R.string.you_format_epub
            ReadFormat.Pdf -> R.string.you_format_pdf
        },
    )

@Composable
internal fun RankedCard(
    title: String,
    items: List<Pair<String, Long>>,
) = StatCard {
    CardTitle(title)
    val number = NumberFormat.getIntegerInstance(locale())
    RankedBars(items.map { it.first to number.format(it.second) }, items.map { it.second.toFloat() })
}
