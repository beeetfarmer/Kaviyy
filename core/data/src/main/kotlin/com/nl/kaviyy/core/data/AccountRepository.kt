package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.AuthKeyDto
import com.nl.kaviyy.core.network.AuthKeyRequestDto
import com.nl.kaviyy.core.network.ResetPasswordDto
import com.nl.kaviyy.core.network.UpdateEmailDto
import com.nl.kaviyy.core.network.apiCall
import com.nl.kaviyy.core.network.kavitaTime
import java.time.Instant
import javax.inject.Inject

/** Kavita's password rule (ResetPasswordDto: 6–256 characters). */
const val PASSWORD_MIN = 6
const val PASSWORD_MAX = 256

enum class EmailChange { Sent, NotSent, Invalid }

/** Longest key Kavita issues (RotateAuthKeyRequestDto.keyLength 8–32). */
private const val AUTH_KEY_LENGTH = 32
private const val KEY_HINT = 4

/** An Auth Key without its secret: [hint] is the last 4 characters, enough to tell keys apart. */
data class AuthKey(
    val id: Int,
    val name: String,
    val hint: String,
    val createdAt: Long,
    val expiresAt: Long,
    val lastUsedAt: Long,
    /** Managed by the server (e.g. OPDS); can't be changed here. */
    val system: Boolean,
)

/** A key just created or rotated: the only time the full secret is shown. */
data class NewAuthKey(
    val key: AuthKey,
    val secret: String,
) {
    override fun toString() = "NewAuthKey(key=$key, <redacted>)"
}

private fun AuthKeyDto.toAuthKey() =
    AuthKey(
        id,
        name.orEmpty(),
        key.orEmpty().takeLast(KEY_HINT),
        kavitaTime(createdAtUtc),
        kavitaTime(expiresAtUtc),
        kavitaTime(lastAccessedAtUtc),
        system = provider == 1,
    )

private fun AuthKeyDto.toNew() = NewAuthKey(toAuthKey(), key.orEmpty())

/** The signed-in user's own account. Online only; passwords are passed through, never stored. */
interface AccountRepository {
    suspend fun changePassword(
        server: Server,
        username: String,
        current: String,
        new: String,
    ): AppResult<Unit>

    suspend fun changeEmail(
        server: Server,
        email: String,
        password: String,
    ): AppResult<EmailChange>

    suspend fun authKeys(server: Server): AppResult<List<AuthKey>>

    /** [expiresAt] null = never. */
    suspend fun createAuthKey(
        server: Server,
        name: String,
        expiresAt: Instant?,
    ): AppResult<NewAuthKey>

    suspend fun rotateAuthKey(
        server: Server,
        key: AuthKey,
    ): AppResult<NewAuthKey>

    suspend fun deleteAuthKey(
        server: Server,
        id: Int,
    ): AppResult<Unit>
}

class KavitaAccountRepository
    @Inject
    constructor(
        private val apis: ServerApis,
    ) : AccountRepository {
        override suspend fun changePassword(
            server: Server,
            username: String,
            current: String,
            new: String,
        ) = apiCall { apis.api(server).changePassword(ResetPasswordDto(username, new, current)) }

        override suspend fun changeEmail(
            server: Server,
            email: String,
            password: String,
        ) = apiCall {
            val r = apis.api(server).changeEmail(UpdateEmailDto(email, password))
            when {
                r.invalidEmail -> EmailChange.Invalid
                r.emailSent -> EmailChange.Sent
                else -> EmailChange.NotSent
            }
        }

        override suspend fun authKeys(server: Server) = apiCall { apis.api(server).authKeys().map { it.toAuthKey() } }

        override suspend fun createAuthKey(
            server: Server,
            name: String,
            expiresAt: Instant?,
        ) = apiCall {
            apis.api(server).createAuthKey(AuthKeyRequestDto(AUTH_KEY_LENGTH, name, expiresAt?.toString())).toNew()
        }

        override suspend fun rotateAuthKey(
            server: Server,
            key: AuthKey,
        ) = apiCall {
            apis.api(server).rotateAuthKey(key.id, AuthKeyRequestDto(AUTH_KEY_LENGTH, key.name)).toNew()
        }

        override suspend fun deleteAuthKey(
            server: Server,
            id: Int,
        ) = apiCall { apis.api(server).deleteAuthKey(id) }
    }
