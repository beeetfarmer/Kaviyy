package com.nl.kaviyy.core.model.server

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerTest {
    private val url = (ServerUrl.parse("http://192.168.1.2:5000") as ServerUrlResult.Valid).url

    @Test
    fun validServer() {
        val server = Server(ServerId(1), "Home Server", url, pinnedCertSha256 = "a".repeat(64), allowInsecure = true)
        assertTrue(server.cleartextPermitted)
    }

    @Test
    fun rejectsBlankNameAndBadPins() {
        assertFailsWith<IllegalArgumentException> { Server(ServerId(1), " ", url) }
        assertFailsWith<IllegalArgumentException> { Server(ServerId(1), "S", url, pinnedCertSha256 = "ABC") }
        assertFailsWith<IllegalArgumentException> { Server(ServerId(1), "S", url, pinnedCertSha256 = "A".repeat(64)) }
    }
}
