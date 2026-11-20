package com.nl.kaviyy.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonSizeLimitInterceptorTest {
    private val mock = MockWebServer().apply { start() }
    private val client = OkHttpClient.Builder().addInterceptor(JsonSizeLimitInterceptor(maxBytes = 10)).build()

    @After
    fun tearDown() = mock.close()

    private fun fetch() =
        client.newCall(Request.Builder().url(mock.url("/")).build()).execute().use { it.body.string() }

    private val json = headersOf("Content-Type", "application/json")

    @Test
    fun smallJsonPasses() {
        mock.enqueue(MockResponse(headers = json, body = "{}"))
        assertEquals("{}", fetch())
    }

    @Test
    fun declaredLengthOverLimitFails() {
        mock.enqueue(MockResponse(headers = json, body = "{\"a\":\"0123456789\"}"))
        assertFailsWith<ResponseTooLargeException> { fetch() }
    }

    @Test
    fun chunkedBodyOverLimitFails() {
        mock.enqueue(
            MockResponse
                .Builder()
                .headers(json)
                .chunkedBody(Buffer().writeUtf8("{\"a\":\"0123456789\"}"), 4)
                .build(),
        )
        assertFailsWith<ResponseTooLargeException> { fetch() }
    }

    @Test
    fun nonJsonIsNotLimitedHere() {
        mock.enqueue(MockResponse(headers = headersOf("Content-Type", "image/webp"), body = "x".repeat(100)))
        assertEquals(100, fetch().length)
    }
}
