package com.nl.kaviyy.core.model.result

import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

/** What went wrong, in terms the UI can explain. Never carries credentials or raw server text. */
sealed interface AppError {
    /** No connection, DNS failure, timeout. */
    data object Network : AppError

    /** TLS handshake / certificate problem (incl. pin mismatch). */
    data object Tls : AppError

    data object Unauthorized : AppError

    data object Forbidden : AppError

    data object NotFound : AppError

    data class Server(
        val code: Int,
    ) : AppError

    /** Response could not be parsed or failed validation. */
    data object InvalidData : AppError

    data object Unknown : AppError

    companion object {
        fun fromHttpCode(code: Int): AppError =
            when (code) {
                HTTP_UNAUTHORIZED -> Unauthorized
                HTTP_FORBIDDEN -> Forbidden
                HTTP_NOT_FOUND -> NotFound
                else -> Server(code)
            }

        fun from(throwable: Throwable): AppError =
            when (throwable) {
                is SSLException -> Tls
                is IOException -> Network
                is IllegalArgumentException, is IllegalStateException -> InvalidData
                else -> Unknown
            }
    }
}

sealed interface AppResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AppResult<T>

    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>
}

/** Runs [block], mapping exceptions to [AppError]. Cancellation is rethrown, never swallowed. */
@Suppress("TooGenericExceptionCaught")
inline fun <T> appResult(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.from(e))
    }

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
