package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.apiCall
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

private const val DAYS_IN_WEEK = 7
private const val TOP_GENRES = 5

/** The signed-in user. */
data class Account(
    val id: Int,
    val username: String,
    val email: String?,
    val admin: Boolean,
    val kavitaVersion: String?,
)

/** "This Week" on You: hours, pages and chapters, and reading events per day from [days]'s first day. */
data class WeekStats(
    val hours: Int,
    val pages: Int,
    val chapters: Int,
    val days: List<LocalDate>,
    val perDay: List<Int>,
)

/** All Stats [derived]: lifetime totals and breakdowns. [byWeekday] is Monday-first. */
data class AllStats(
    val hours: Long,
    val pages: Long,
    val words: Long,
    val hoursPerWeek: Double,
    val pagesPerYear: List<Pair<Int, Long>>,
    val byWeekday: List<Long>,
    val genres: List<Pair<String, Long>>,
)

/** Profile and reading statistics, straight from the server (ponytail: not cached; offline shows nothing). */
interface ProfileRepository {
    suspend fun account(server: Server): AppResult<Account>

    /** The week (starting [firstDay]) containing [today], in [zone]. */
    suspend fun week(
        server: Server,
        userId: Int,
        today: LocalDate,
        firstDay: DayOfWeek,
        zone: ZoneId,
    ): AppResult<WeekStats>

    suspend fun allStats(
        server: Server,
        userId: Int,
    ): AppResult<AllStats>
}

class KavitaProfileRepository
    @Inject
    constructor(
        private val apis: ServerApis,
    ) : ProfileRepository {
        override suspend fun account(server: Server) =
            apiCall {
                val a = apis.api(server).account()
                Account(
                    a.id,
                    a.username ?: server.username.orEmpty(),
                    a.email,
                    a.roles.any { it.equals("Admin", ignoreCase = true) },
                    a.kavitaVersion,
                )
            }

        override suspend fun week(
            server: Server,
            userId: Int,
            today: LocalDate,
            firstDay: DayOfWeek,
            zone: ZoneId,
        ) = apiCall {
            val api = apis.api(server)
            val start = today.with(TemporalAdjusters.previousOrSame(firstDay))
            val days = (0 until DAYS_IN_WEEK).map { start.plusDays(it.toLong()) }
            val from = start.atStartOfDay().toString()
            val to =
                start
                    .plusDays(DAYS_IN_WEEK.toLong())
                    .atStartOfDay()
                    .minusSeconds(1)
                    .toString()
            val pace = api.readingPace(userId, from, to, zone.id)
            val counts =
                api
                    .readingCounts(userId, from, to, zone.id)
                    .mapNotNull { c ->
                        c.value?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() }?.to(c.count)
                    }.groupBy({ it.first }, { it.second })
            WeekStats(
                pace.hoursRead,
                pace.pagesRead,
                pace.booksRead + pace.comicsRead,
                days,
                days.map { counts[it]?.sum() ?: 0 },
            )
        }

        override suspend fun allStats(
            server: Server,
            userId: Int,
        ) = apiCall {
            val api = apis.api(server)
            val read = api.userReadStats(userId)
            val weekdays = api.dayBreakdown(userId).associate { it.value to it.count }
            AllStats(
                read.timeSpentReading,
                read.totalPagesRead,
                read.totalWordsRead,
                read.avgHoursPerWeekSpentReading,
                api.pagesPerYear(userId).sortedBy { it.value }.map { it.value to it.count },
                // Kavita's DayOfWeek is Sunday = 0; show Monday first.
                (1..DAYS_IN_WEEK).map { weekdays[it % DAYS_IN_WEEK] ?: 0L },
                api
                    .genreBreakdown(userId)
                    .data
                    .mapNotNull { g -> g.value?.let { it to g.count } }
                    .take(TOP_GENRES),
            )
        }
    }
