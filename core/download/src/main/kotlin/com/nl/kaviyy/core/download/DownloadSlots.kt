package com.nl.kaviyy.core.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Caps how many chapters download at once; the limit is read on every attempt so a settings change applies. */
class DownloadSlots {
    private val running = MutableStateFlow(0)

    suspend fun <T> withSlot(
        limit: suspend () -> Int,
        block: suspend () -> T,
    ): T {
        while (true) {
            val n = running.value
            val max = limit().coerceAtLeast(1)
            if (n < max && running.compareAndSet(n, n + 1)) break
            if (n >= max) running.first { it < limit().coerceAtLeast(1) }
        }
        return try {
            block()
        } finally {
            running.update { it - 1 }
        }
    }

    companion object {
        /** One per process, shared by every download worker. */
        val shared = DownloadSlots()
    }
}
