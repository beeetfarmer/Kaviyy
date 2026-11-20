package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.server.Server
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/**
 * One server's credentials as the HTTP layer sees them. Called on OkHttp threads (never main), so blocking is fine.
 * Implemented over the encrypted CredentialStore in core:data.
 */
interface CredentialSource {
    fun current(): Credentials?

    fun update(credentials: Credentials.Jwt)

    /** Credentials are no longer valid: clear them so the UI returns to sign-in. */
    fun signOut()
}

internal const val AUTHORIZATION = "Authorization"
internal const val API_KEY = "x-api-key"

/** Same host and effective port as the configured server (N6 host scoping). */
internal fun HttpUrl.isServer(server: Server) =
    host == server.url.host && port == HttpUrl.defaultPort(server.url.scheme).let { server.url.port ?: it }

private fun Request.Builder.auth(credentials: Credentials?) =
    when (credentials) {
        is Credentials.Jwt -> header(AUTHORIZATION, "Bearer ${credentials.token}")
        is Credentials.AuthKey -> header(API_KEY, credentials.apiKey)
        null -> this
    }

/**
 * Adds auth to requests for the configured server. Redirect hops to other hosts have it stripped again by
 * [TransportPolicyInterceptor], which runs on every hop.
 */
internal class AuthInterceptor(
    private val server: Server,
    private val source: CredentialSource,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authed = if (request.url.isServer(server)) request.newBuilder().auth(source.current()).build() else request
        return chain.proceed(authed)
    }
}

@Serializable
internal data class TokenRequestDto(
    val token: String? = null,
    val refreshToken: String? = null,
)

/**
 * On 401 from the server: refresh the JWT once (`POST api/Account/refresh-token`), serialised so concurrent 401s
 * cause a single refresh. A rejected refresh (4xx), a rejected Auth Key, or a 401 right after refreshing signs out;
 * network and server errors do not (offline users stay signed in).
 */
internal class TokenAuthenticator(
    private val server: Server,
    private val source: CredentialSource,
    private val refreshClient: OkHttpClient,
) : Authenticator {
    // OkHttp's Authenticator is blocking, so a monitor is the Mutex here.
    private val lock = Any()

    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        val alreadyRetried =
            generateSequence(response.priorResponse) { it.priorResponse }.any {
                it.code ==
                    HTTP_UNAUTHORIZED
            }
        return when {
            !response.request.url.isServer(server) -> null
            alreadyRetried -> null.also { source.signOut() }
            else -> synchronized(lock) { retryWithFreshCredentials(response.request) }
        }
    }

    private fun retryWithFreshCredentials(request: Request): Request? {
        val sent = request.header(AUTHORIZATION)
        return when (val current = source.current()) {
            // Another call refreshed while we waited: just retry with the new token.
            is Credentials.Jwt if "Bearer ${current.token}" != sent -> request.newBuilder().auth(current).build()

            is Credentials.Jwt -> refresh(current)?.let { request.newBuilder().auth(it).build() }

            is Credentials.AuthKey -> null.also { source.signOut() }

            null -> null
        }
    }

    private fun refresh(current: Credentials.Jwt): Credentials.Jwt? {
        val body = json.encodeToString(TokenRequestDto(current.token, current.refreshToken)).toRequestBody(JSON)
        val request =
            Request
                .Builder()
                .url(server.url.resolve("api/Account/refresh-token"))
                .post(body)
                .build()
        return try {
            refreshClient.newCall(request).execute().use { r ->
                val dto =
                    if (r.isSuccessful) {
                        runCatching {
                            json.decodeFromString<TokenRequestDto>(r.body.string())
                        }.getOrNull()
                    } else {
                        null
                    }
                val token = dto?.token
                when {
                    token != null -> {
                        current
                            .copy(
                                token = token,
                                refreshToken = dto.refreshToken ?: current.refreshToken,
                            ).also(source::update)
                    }

                    r.code in HTTP_BAD_REQUEST until HTTP_INTERNAL_ERROR -> {
                        null.also { source.signOut() }
                    }

                    else -> {
                        null
                    }
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
