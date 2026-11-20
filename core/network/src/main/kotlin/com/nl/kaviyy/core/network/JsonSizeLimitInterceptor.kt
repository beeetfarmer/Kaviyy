package com.nl.kaviyy.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

class ResponseTooLargeException(
    limit: Long,
) : IOException("Response exceeds $limit bytes")

private const val JSON_LIMIT_BYTES = 10L * 1024 * 1024

/** N5: JSON bodies are capped (default 10 MB), whether or not the server sends Content-Length. */
internal class JsonSizeLimitInterceptor(
    private val maxBytes: Long = JSON_LIMIT_BYTES,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        val isJson = body.contentType()?.subtype?.endsWith("json") == true
        if (isJson && body.contentLength() > maxBytes) {
            response.close()
            throw ResponseTooLargeException(maxBytes)
        }
        return if (isJson) {
            val limited = LimitedSource(body.source(), maxBytes).buffer()
            response.newBuilder().body(limited.asResponseBody(body.contentType(), body.contentLength())).build()
        } else {
            response
        }
    }
}

private class LimitedSource(
    delegate: Source,
    private val maxBytes: Long,
) : ForwardingSource(delegate) {
    private var read = 0L

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        val n = super.read(sink, byteCount)
        if (n > 0) read += n
        if (read > maxBytes) throw ResponseTooLargeException(maxBytes)
        return n
    }
}
