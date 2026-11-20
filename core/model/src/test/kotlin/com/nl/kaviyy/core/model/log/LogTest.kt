package com.nl.kaviyy.core.model.log

import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogTest {
    private val lines = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        Log.sink = null
    }

    @Test
    fun noSinkMeansMessageIsNeverBuilt() {
        Log.sink = null
        var built = false
        Log.d("T") {
            built = true
            "x"
        }
        assertFalse(built)
    }

    @Test
    fun messagesAndErrorsAreRedacted() {
        Log.sink = { _, _, message, _ -> lines += message }
        Log.i("Net") { "Authorization: Bearer abc" }
        Log.e("Net", IOException("failed GET /api?apiKey=k1")) { "request failed" }
        assertEquals("Authorization: <redacted>", lines[0])
        assertTrue(lines[1].contains("apiKey=<redacted>"))
        assertFalse(lines.any { it.contains("abc") || it.contains("k1") })
    }

    @Test
    fun throwableIsNotPassedToSink() {
        var received: Throwable? = IllegalStateException()
        Log.sink = { _, _, _, error -> received = error }
        Log.w("T", IOException("token=t")) { "w" }
        assertEquals(null, received)
    }
}
