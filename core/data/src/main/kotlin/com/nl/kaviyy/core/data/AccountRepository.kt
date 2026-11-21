package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ResetPasswordDto
import com.nl.kaviyy.core.network.UpdateEmailDto
import com.nl.kaviyy.core.network.apiCall
import javax.inject.Inject

/** Kavita's password rule (ResetPasswordDto: 6–256 characters). */
const val PASSWORD_MIN = 6
const val PASSWORD_MAX = 256

enum class EmailChange { Sent, NotSent, Invalid }

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
    }
