package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.AccountDto
import com.nl.kaviyy.core.network.DateStatCountDto
import com.nl.kaviyy.core.network.IntStatCountDto
import com.nl.kaviyy.core.network.ReadingPaceDto
import com.nl.kaviyy.core.network.StringBreakdownDto
import com.nl.kaviyy.core.network.StringStatCountDto
import com.nl.kaviyy.core.network.UserReadStatsDto
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class ProfileRepositoryTest {
    private val server =
        Server(ServerId(1), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url, username = "neo")
    private val calls = mutableListOf<String>()

    private val api =
        object : FakeKavitaApi() {
            override suspend fun account() =
                AccountDto(7, "Neo", "neo@example.invalid", listOf("Pleb", "admin"), "0.9.1")

            override suspend fun readingPace(
                userId: Int,
                startDate: String,
                endDate: String,
                timeZoneId: String,
            ): ReadingPaceDto {
                calls += "pace $userId $startDate $endDate $timeZoneId"
                return ReadingPaceDto(hoursRead = 4, pagesRead = 312, booksRead = 2, comicsRead = 9)
            }

            override suspend fun readingCounts(
                userId: Int,
                startDate: String,
                endDate: String,
                timeZoneId: String,
            ) = listOf(
                DateStatCountDto("2026-11-16T00:00:00", 3),
                DateStatCountDto("2026-11-16T00:00:00", 2), // another format, same day
                DateStatCountDto("2026-11-19T00:00:00", 6),
                DateStatCountDto("not a date", 9),
            )

            override suspend fun userReadStats(userId: Int) = UserReadStatsDto(5_000, 90_000, 120, 3.5)

            override suspend fun pagesPerYear(userId: Int) =
                listOf(IntStatCountDto(2026, 900), IntStatCountDto(2025, 4_100))

            override suspend fun dayBreakdown(userId: Int) = listOf(IntStatCountDto(0, 70), IntStatCountDto(1, 10))

            override suspend fun genreBreakdown(userId: Int) =
                StringBreakdownDto((1..7).map { StringStatCountDto("G$it", 10L - it) })
        }
    private val repo = KavitaProfileRepository { api }

    @Test
    fun accountMarksAdmins() =
        runTest {
            val a = (repo.account(server) as AppResult.Success).value
            assertEquals(Account(7, "Neo", "neo@example.invalid", admin = true, kavitaVersion = "0.9.1"), a)
        }

    @Test
    fun weekStartsOnTheLocaleDayAndSumsEachDay() =
        runTest {
            val w =
                (
                    repo.week(
                        server,
                        7,
                        LocalDate.of(2026, 11, 21),
                        DayOfWeek.MONDAY,
                        ZoneId.of("Europe/London"),
                    ) as AppResult.Success
                ).value
            assertEquals(listOf("pace 7 2026-11-16T00:00 2026-11-22T23:59:59 Europe/London"), calls)
            assertEquals(Triple(4, 312, 11), Triple(w.hours, w.pages, w.chapters))
            assertEquals(LocalDate.of(2026, 11, 16), w.days.first())
            assertEquals(listOf(5, 0, 0, 6, 0, 0, 0), w.perDay)
        }

    @Test
    fun allStatsAreMondayFirstWithTopGenres() =
        runTest {
            val s = (repo.allStats(server, 7) as AppResult.Success).value
            assertEquals(listOf(2025 to 4_100L, 2026 to 900L), s.pagesPerYear)
            assertEquals(listOf(10L, 0, 0, 0, 0, 0, 70), s.byWeekday)
            assertEquals(listOf("G1", "G2", "G3", "G4", "G5"), s.genres.map { it.first })
            assertEquals(120L, s.hours)
        }
}
