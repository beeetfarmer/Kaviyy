package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.EmailDeviceDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceRepositoryTest {
    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val api = FakeKavitaApi()
    private val repo = KavitaDeviceRepository { api }

    @Test
    fun savesCreateNewAndUpdateExistingAndSendsBothKinds() =
        runTest {
            val created = repo.save(server, EmailDeviceDto(0, "Kindle", "me@kindle.com", 2))
            assertEquals(9, (created as AppResult.Success).value.id)
            repo.save(server, EmailDeviceDto(4, "Kobo", "me@kobo.com", 3))
            repo.delete(server, 4)
            repo.send(server, 9, SendTarget.Chapters(listOf(1, 2)))
            repo.send(server, 9, SendTarget.Series(7))
            assertEquals(
                listOf("create Kindle", "update 4", "delete 4", "send [1, 2] to 9", "send series 7 to 9"),
                api.deviceCalls,
            )
        }

    @Test
    fun emailCheck() {
        assertTrue(isDeviceEmail("me@kindle.com"))
        assertTrue(isDeviceEmail("a.b+c@free.kindle.com"))
        assertFalse(isDeviceEmail("me@kindle"))
        assertFalse(isDeviceEmail("@kindle.com"))
        assertFalse(isDeviceEmail("me@.com"))
        assertFalse(isDeviceEmail("me@kindle.com."))
        assertFalse(isDeviceEmail("me@a@b.com"))
        assertFalse(isDeviceEmail("me @kindle.com"))
    }
}
