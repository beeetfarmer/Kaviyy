package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.apiCall
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val DAYS_IN_WEEK = 7
private const val TOP_GENRES = 5
private const val TOP_TAGS = 8

/** Heatmap + streak window, and the monthly chart's length. */
const val ACTIVITY_DAYS = 365
const val MONTHS_SHOWN = 12
private const val HOURS_IN_DAY = 24

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
    /** Reading events per day for the last [ACTIVITY_DAYS] days, oldest first (last = today). */
    val activity: List<Long> = emptyList(),
    /** The day [activity] ends on (its last entry). */
    val activityEnd: LocalDate? = null,
    val streaks: Streaks = Streaks(0, 0),
    /** Share of reading events by format over the same window, largest first. */
    val formats: List<Pair<ReadFormat, Long>> = emptyList(),
    /** Chapters read per month, oldest first. */
    val readsByMonth: List<Pair<YearMonth, Long>> = emptyList(),
    /** Reading per hour of day (0–23), relative values; empty when the server doesn't say. */
    val byHour: List<Long> = emptyList(),
    val counts: ReadCounts? = null,
    val tags: List<Pair<String, Long>> = emptyList(),
)

/** Lifetime counts from `api/Stats/user-stats`. */
data class ReadCounts(
    val books: Int,
    val comics: Int,
    val authors: Int,
    val ratings: Int,
    val reviews: Int,
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
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
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
            today: LocalDate,
            zone: ZoneId,
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
            ).withExtras(api, userId, today, zone)
        }

        /** (day, format, count) reading events for the last [ACTIVITY_DAYS] days; empty if the call fails. */
        private suspend fun activityEvents(
            api: KavitaApi,
            userId: Int,
            today: LocalDate,
            end: String,
            zone: ZoneId,
        ): List<Triple<LocalDate, ReadFormat, Long>> =
            optional {
                val from = today.minusDays(ACTIVITY_DAYS - 1L).atStartOfDay().toString()
                api.readingCounts(userId, from, end, zone.id).mapNotNull { c ->
                    c.value
                        ?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() }
                        ?.let { Triple(it, readFormat(c.format), c.count.toLong()) }
                }
            }.orEmpty()

        /**
         * The richer charts. Each call is optional: one failing (older server, permission) leaves its chart out
         * instead of failing the whole page.
         */
        private suspend fun AllStats.withExtras(
            api: KavitaApi,
            userId: Int,
            today: LocalDate,
            zone: ZoneId,
        ): AllStats {
            val end =
                today
                    .plusDays(1)
                    .atStartOfDay()
                    .minusSeconds(1)
                    .toString()
            val events = activityEvents(api, userId, today, end, zone)
            val activity =
                dailyTotals(
                    events.groupBy({ it.first }, { it.third }).mapValues { it.value.sum() },
                    today,
                    ACTIVITY_DAYS,
                )
            val month = YearMonth.from(today)
            val months =
                optional {
                    val from =
                        month
                            .minusMonths(MONTHS_SHOWN - 1L)
                            .atDay(1)
                            .atStartOfDay()
                            .toString()
                    api
                        .readsByMonth(userId, from, end, zone.id)
                        .mapNotNull { m -> m.value?.let { YearMonth.of(it.year, it.month) to m.count } }
                        .toMap()
                }
            val hours =
                optional {
                    val byHour = api.avgTimeByHour(userId, zone.id).stats.associate { it.value to it.count }
                    (0 until HOURS_IN_DAY).map { byHour[it] ?: 0L }
                }
            return copy(
                activity = if (events.isEmpty()) emptyList() else activity,
                activityEnd = today,
                streaks = streaks(activity),
                formats =
                    events
                        .groupBy({ it.second }, { it.third })
                        .map { (format, counts) -> format to counts.sum() }
                        .sortedByDescending { it.second },
                readsByMonth = months?.let { monthlyTotals(it, month, MONTHS_SHOWN) }.orEmpty(),
                byHour = hours?.takeIf { h -> h.any { it > 0 } }.orEmpty(),
                counts = optional { readCounts(api, userId) },
                tags =
                    optional {
                        api
                            .tagBreakdown(userId)
                            .data
                            .mapNotNull { t -> t.value?.let { it to t.count } }
                            .take(TOP_TAGS)
                    }.orEmpty(),
            )
        }
    }

private suspend fun readCounts(
    api: KavitaApi,
    userId: Int,
) = api.userStats(userId).let { ReadCounts(it.booksRead, it.comicsRead, it.authorsRead, it.ratings, it.reviews) }

/** Runs [block]; a failure becomes null (cancellation still propagates). */
private suspend fun <T> optional(block: suspend () -> T): T? =
    runCatching { block() }.onFailure { if (it is CancellationException) throw it }.getOrNull()
