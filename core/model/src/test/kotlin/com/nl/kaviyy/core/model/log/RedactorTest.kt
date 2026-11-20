package com.nl.kaviyy.core.model.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RedactorTest {
    private fun assertRedacted(
        input: String,
        expected: String,
    ) = assertEquals(expected, Redactor.redact(input))

    @Test
    fun headers() {
        assertRedacted("Authorization: Bearer eyJhbGci.abc", "Authorization: <redacted>")
        assertRedacted("x-api-key: 1234-abcd", "x-api-key: <redacted>")
        assertRedacted("X-API-KEY:abcd", "X-API-KEY:<redacted>")
    }

    @Test
    fun queryParameters() {
        assertRedacted(
            "GET https://srv/api/image?chapterId=4&apiKey=s3cr3t&page=2",
            "GET https://srv/api/image?chapterId=4&apiKey=<redacted>&page=2",
        )
        assertRedacted("token=abc&password=hunter2", "token=<redacted>&password=<redacted>")
    }

    @Test
    fun jsonBodies() {
        assertRedacted(
            """{"username":"aarav","password":"hunter2","token":"jwt","refreshToken":"r1"}""",
            """{"username":"aarav","password":"<redacted>","token":"<redacted>","refreshToken":"<redacted>"}""",
        )
        assertRedacted("""{"apiKey" : "k"}""", """{"apiKey" : "<redacted>"}""")
    }

    @Test
    fun urlUserInfo() {
        assertRedacted("https://user:pass@kavita.lan/api", "https://<redacted>@kavita.lan/api")
    }

    @Test
    fun harmlessTextIsUntouched() {
        val text = "Loaded 24 series for library 3 from https://kavita.lan/api/series"
        assertEquals(text, Redactor.redact(text))
    }

    @Test
    fun secretNeverSurvivesInAnyCase() {
        listOf("AUTHORIZATION=abc", "Password: abc", "api_key=abc", "authToken: abc").forEach {
            assertFalse(Redactor.redact(it).contains("abc"), it)
        }
    }
}
