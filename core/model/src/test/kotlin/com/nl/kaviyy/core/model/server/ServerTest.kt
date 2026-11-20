package com.nl.kaviyy.core.model.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerTest {
    private val url = (ServerUrl.parse("http://192.168.1.2:5000") as ServerUrlResult.Valid).url

    @Test
    fun validServer() {
        val pin = PinnedCertificate.of(byteArrayOf(1, 2, 3))
        val server = Server(ServerId(1), "Home Server", url, pinnedCertificate = pin, allowInsecure = true)
        assertTrue(server.cleartextPermitted)
        assertTrue(pin.der.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun fingerprintIsLowercaseHexSha256() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            PinnedCertificate.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun rejectsBlankNameAndBadPins() {
        assertFailsWith<IllegalArgumentException> { Server(ServerId(1), " ", url) }
        assertFailsWith<IllegalArgumentException> { PinnedCertificate("not base64!") }
        assertFailsWith<IllegalArgumentException> { PinnedCertificate("") }
    }
}
