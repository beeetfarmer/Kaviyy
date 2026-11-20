package com.nl.kaviyy.core.model.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialsTest {
    @Test
    fun toStringNeverContainsSecrets() {
        val jwt = Credentials.Jwt("aarav", "secret-token", "secret-refresh", "secret-api").toString()
        val key = Credentials.AuthKey("secret-key").toString()
        listOf("secret-token", "secret-refresh", "secret-api").forEach { assertFalse(it in jwt) }
        assertFalse("secret-key" in key)
        assertTrue("aarav" in jwt)
    }
}
