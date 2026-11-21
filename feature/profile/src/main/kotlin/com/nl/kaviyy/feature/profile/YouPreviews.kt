package com.nl.kaviyy.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.Account
import com.nl.kaviyy.core.data.AllStats
import com.nl.kaviyy.core.data.WeekStats
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import java.time.LocalDate

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
