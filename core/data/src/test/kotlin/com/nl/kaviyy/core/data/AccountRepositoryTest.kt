package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.EmailChangeDto
import com.nl.kaviyy.core.network.ResetPasswordDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccountRepositoryTest {
    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val api = FakeKavitaApi()
    private val repo = KavitaAccountRepository { api }

    @Test
    fun passwordChangeSendsUsernameOldAndNew() =
        runTest {
            repo.changePassword(server, "neo", "old-pass", "new-pass")
            assertEquals(listOf("password neo old-pass->new-pass"), api.accountCalls)
        }

    @Test
    fun emailOutcomes() =
        runTest {
            assertEquals(AppResult.Success(EmailChange.Sent), repo.changeEmail(server, "a@b.co", "pw"))
            api.emailResult = EmailChangeDto(emailSent = false)
            assertEquals(AppResult.Success(EmailChange.NotSent), repo.changeEmail(server, "a@b.co", "pw"))
            api.emailResult = EmailChangeDto(invalidEmail = true)
            assertEquals(AppResult.Success(EmailChange.Invalid), repo.changeEmail(server, "a@b", "pw"))
        }

    @Test
    fun passwordsNeverReachToString() {
        assertFalse("secret" in ResetPasswordDto("neo", "secret", "secret").toString())
    }
}
