package com.nl.kaviyy.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each server's clock offset, learned from the `Date` header of its responses, so local change times compare
 * fairly with the server's (newest wins). Unknown until the first response: offset 0.
 */
@Singleton
class ServerClock
    @Inject
    constructor() {
        private val offsets = ConcurrentHashMap<Long, Long>()

        fun offset(serverId: Long): Long = offsets[serverId] ?: 0

        /** Now, on [serverId]'s clock. */
        fun now(serverId: Long): Long = System.currentTimeMillis() + offset(serverId)

        fun record(
            serverId: Long,
            serverTime: Long,
            localTime: Long = System.currentTimeMillis(),
        ) {
            offsets[serverId] = serverTime - localTime
        }
    }

/** Network interceptor: records the server clock offset from every response's `Date` header. */
internal class ServerClockInterceptor(
    private val serverId: Long,
    private val clock: ServerClock,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request()).also { response ->
            response.headers.getDate("Date")?.let { clock.record(serverId, it.time) }
        }
}

/** Kavita UTC timestamps, with or without a zone suffix; 0 when absent or unreadable. */
fun parseUtc(value: String?): Long {
    if (value.isNullOrBlank()) return 0
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrDefault(0)
}
