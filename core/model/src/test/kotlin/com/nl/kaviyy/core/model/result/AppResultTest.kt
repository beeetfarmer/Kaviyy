package com.nl.kaviyy.core.model.result

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppResultTest {
    @Test
    fun successWrapsValue() {
        assertEquals(AppResult.Success(3), appResult { 1 + 2 })
    }

    @Test
    fun exceptionsMapToErrors() {
        assertEquals(AppResult.Failure(AppError.Network), appResult { throw SocketTimeoutException() })
        assertEquals(AppResult.Failure(AppError.Network), appResult { throw IOException() })
        assertEquals(AppResult.Failure(AppError.Tls), appResult { throw SSLHandshakeException("x") })
        assertEquals(AppResult.Failure(AppError.InvalidData), appResult { require(false) })
        assertEquals(AppResult.Failure(AppError.Unknown), appResult { throw UnsupportedOperationException() })
    }

    @Test
    fun cancellationIsRethrown() {
        assertFailsWith<CancellationException> { appResult { throw CancellationException("c") } }
    }

    @Test
    fun httpCodes() {
        assertEquals(AppError.Unauthorized, AppError.fromHttpCode(401))
        assertEquals(AppError.Forbidden, AppError.fromHttpCode(403))
        assertEquals(AppError.NotFound, AppError.fromHttpCode(404))
        assertEquals(AppError.Server(500), AppError.fromHttpCode(500))
    }

    @Test
    fun mapAndGetOrNull() {
        assertEquals(AppResult.Success("2"), AppResult.Success(2).map { it.toString() })
        val failure: AppResult<Int> = AppResult.Failure(AppError.Network)
        assertEquals(failure, failure.map { it + 1 })
        assertNull(failure.getOrNull())
    }
}
