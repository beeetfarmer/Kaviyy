package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.result.appResult
import com.nl.kaviyy.core.model.server.Server
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** `POST api/Account/login` body (LoginDto). */
@Serializable
internal data class LoginDto(
    val username: String? = null,
    val password: String? = null,
    val apiKey: String? = null,
)

/** How the user signs in. */
sealed interface LoginMethod {
    data class Password(
        val username: String,
        val password: String,
    ) : LoginMethod {
        override fun toString() = "Password(username=$username, password=<redacted>)"
    }

    /** Kavita Auth Key (SSO users). Validated via login, then used as `x-api-key` (no expiry, no refresh). */
    data class AuthKey(
        val key: String,
    ) : LoginMethod {
        override fun toString() = "AuthKey(<redacted>)"
    }
}

/** The UserDto fields we use. */
@Serializable
internal data class UserDto(
    val username: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
    val roles: List<String>? = null,
)

data class LoginResult(
    val credentials: Credentials,
    /** Server roles, e.g. "Download" gates offline downloads. */
    val roles: Set<String>,
    val username: String? = null,
)

fun interface AccountApi {
    suspend fun login(
        server: Server,
        method: LoginMethod,
    ): AppResult<LoginResult>
}

class KavitaAccountApi
    @Inject
    constructor(
        private val clients: ServerHttpClients,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) : AccountApi {
        override suspend fun login(
            server: Server,
            method: LoginMethod,
        ): AppResult<LoginResult> =
            withContext(io) {
                val dto =
                    when (method) {
                        is LoginMethod.Password -> LoginDto(username = method.username, password = method.password)
                        is LoginMethod.AuthKey -> LoginDto(apiKey = method.key)
                    }
                val body = json.encodeToString(dto).toRequestBody(JSON)
                val request =
                    Request
                        .Builder()
                        .url(server.url.resolve("api/Account/login"))
                        .post(body)
                        .build()
                val response = appResult { clients.create(server).newCall(request).execute() }
                if (response !is AppResult.Success) return@withContext response as AppResult.Failure
                response.value.use { r ->
                    if (!r.isSuccessful) return@use AppResult.Failure(AppError.fromHttpCode(r.code))
                    appResult {
                        val user = json.decodeFromString<UserDto>(r.body.string())
                        val token = requireNotNull(user.token) { "No token in login response" }
                        val credentials =
                            when (method) {
                                is LoginMethod.Password -> {
                                    Credentials.Jwt(
                                        user.username ?: method.username,
                                        token,
                                        user.refreshToken.orEmpty(),
                                        user.apiKey,
                                    )
                                }

                                is LoginMethod.AuthKey -> {
                                    Credentials.AuthKey(method.key)
                                }
                            }
                        val username = user.username ?: (method as? LoginMethod.Password)?.username
                        LoginResult(credentials, user.roles.orEmpty().toSet(), username)
                    }
                }
            }

        private companion object {
            val JSON = "application/json".toMediaType()
            val json =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
        }
    }
