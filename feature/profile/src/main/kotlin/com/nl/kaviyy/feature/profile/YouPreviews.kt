package com.nl.kaviyy.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.ACTIVITY_DAYS
import com.nl.kaviyy.core.data.Account
import com.nl.kaviyy.core.data.AdminServerInfo
import com.nl.kaviyy.core.data.AllStats
import com.nl.kaviyy.core.data.ReadCounts
import com.nl.kaviyy.core.data.ReadFormat
import com.nl.kaviyy.core.data.ServerJob
import com.nl.kaviyy.core.data.Streaks
import com.nl.kaviyy.core.data.WeekStats
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import java.time.LocalDate
import java.time.YearMonth

internal val previewYou =
    YouUiState(
        server =
            Server(
                ServerId(1),
                "Home Server",
                (ServerUrl.parse("https://kavita.home.lan") as ServerUrlResult.Valid).url,
            ),
        account = Account(1, "Neo Anderson", null, admin = true, kavitaVersion = "0.9.1"),
        week =
            WeekStats(
                hours = 6,
                pages = 412,
                chapters = 14,
                days = (0L until 7L).map { LocalDate.of(2026, 11, 16).plusDays(it) },
                perDay = listOf(3, 5, 0, 8, 2, 6, 1),
            ),
    )

internal val previewAllStats =
    AllStatsUiState(
        AllStats(
            hours = 312,
            pages = 48_210,
            words = 1_204_500,
            hoursPerWeek = 4.2,
            pagesPerYear = listOf(2022 to 6_100L, 2023 to 9_800L, 2024 to 12_400L, 2025 to 14_010L, 2026 to 5_900L),
            byWeekday = listOf(40L, 32, 28, 45, 50, 90, 76),
            genres = listOf("Action" to 210L, "Fantasy" to 180L, "Drama" to 95L, "Sci-Fi" to 60L),
            // Deterministic "random" year of reading: busier at weekends, a gap mid-year, a 9-day current streak.
            activity =
                List(ACTIVITY_DAYS) { d ->
                    if (d >=
                        ACTIVITY_DAYS - 9
                    ) {
                        1L + d % 4
                    } else {
                        ((d * 7 + d / 5) % 9 - 3L).coerceAtLeast(0)
                    }
                },
            activityEnd = LocalDate.of(2026, 11, 21),
            streaks = Streaks(current = 9, longest = 23),
            formats = listOf(ReadFormat.Images to 720L, ReadFormat.Epub to 210L, ReadFormat.Pdf to 40L),
            readsByMonth =
                (0L until 12L).map {
                    YearMonth.of(2025, 12).plusMonths(it) to
                        listOf(30L, 42, 18, 25, 51, 60, 12, 8, 33, 47, 55, 39)[it.toInt()]
                },
            byHour = listOf(1L, 0, 0, 0, 0, 0, 2, 4, 6, 3, 2, 3, 5, 4, 3, 4, 6, 8, 10, 14, 18, 22, 16, 7),
            counts = ReadCounts(books = 38, comics = 412, authors = 57, ratings = 64, reviews = 9),
            tags = listOf("Isekai" to 88L, "Found Family" to 61L, "Time Travel" to 40L, "Slow Burn" to 22L),
        ),
    )

@Composable
internal fun YouPreview(appearance: Appearance = Appearance.Light) =
    KavitaTheme(appearance = appearance) { YouScreen(previewYou, YouActions()) }

@Composable
internal fun AllStatsPreview(appearance: Appearance = Appearance.Light) =
    KavitaTheme(appearance = appearance) { AllStatsScreen(previewAllStats, {}) }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun YouLightPreview() = YouPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun YouDarkPreview() = YouPreview(Appearance.Dark)

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun AllStatsLightPreview() = AllStatsPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun AllStatsDarkPreview() = AllStatsPreview(Appearance.Dark)

internal val previewAdmin =
    AdminUiState(
        info = AdminServerInfo("0.9.1", docker = true, installedAt = 1_700_000_000_000),
        libraries = listOf(LibraryEntity(1, 1, "Manga", 0), LibraryEntity(1, 2, "Comics", 1)),
        jobs = listOf(ServerJob("Scan Libraries", 1_763_600_000_000), ServerJob("Backup", 0)),
        recent = listOf("Ember Road" to 1_763_500_000_000, "Salt & Signal" to 1_763_400_000_000),
    )

@Composable
internal fun AdminPreview(appearance: Appearance = Appearance.Light) =
    KavitaTheme(appearance = appearance) { AdminScreen(previewAdmin, AdminActions()) }

@Preview(widthDp = 412, heightDp = 1200)
@Composable
private fun AdminLightPreview() = AdminPreview()

@Preview(widthDp = 412, heightDp = 1200)
@Composable
private fun AdminDarkPreview() = AdminPreview(Appearance.Dark)
