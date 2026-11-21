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

    @Test
    fun authKeysKeepOnlyAHintAndShowNewSecretsOnce() =
        runTest {
            api.keys =
                listOf(
                    com.nl.kaviyy.core.network.AuthKeyDto(
                        1,
                        "abcdefgh1234",
                        "Phone",
                        "2026-01-02T03:04:05Z",
                        provider = 0,
                    ),
                    com.nl.kaviyy.core.network
                        .AuthKeyDto(2, "zzzzOPDS", "OPDS", provider = 1),
                )
            val keys = (repo.authKeys(server) as AppResult.Success).value
            assertEquals(listOf("1234", "OPDS"), keys.map { it.hint })
            assertEquals(listOf(false, true), keys.map { it.system })
            assertEquals(
                java.time.Instant
                    .parse("2026-01-02T03:04:05Z")
                    .toEpochMilli(),
                keys[0].createdAt,
            )
            val created = repo.createAuthKey(server, "Tablet", java.time.Instant.parse("2027-01-01T00:00:00Z"))
            val new = (created as AppResult.Success).value
            assertEquals(32, new.secret.length)
            assertFalse(new.secret in new.toString())
            repo.rotateAuthKey(server, keys[0])
            repo.deleteAuthKey(server, 1)
            assertEquals(listOf("create Tablet 32 2027-01-01T00:00:00Z", "rotate 1", "delete 1"), api.keyCalls)
        }
}
