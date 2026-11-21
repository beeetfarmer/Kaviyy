package com.nl.kaviyy.core.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class DownloadSlotsTest {
    @Test
    fun noMoreThanTheLimitRunAndALargerLimitLetsWaitersIn() =
        runTest {
            val slots = DownloadSlots()
            var limit = 2
            var running = 0
            var peak = 0
            val gate = CompletableDeferred<Unit>()
            repeat(4) {
                launch {
                    slots.withSlot({ limit }) {
                        running++
                        peak = maxOf(peak, running)
                        gate.await()
                        running--
                    }
                }
            }
            runCurrent()
            assertEquals(2 to 2, running to peak)
            gate.complete(Unit)
            runCurrent()
            assertEquals(0, running)
            assertEquals(2, peak)
        }
}
