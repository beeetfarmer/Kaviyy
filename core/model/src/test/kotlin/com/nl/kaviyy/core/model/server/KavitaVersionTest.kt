package com.nl.kaviyy.core.model.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KavitaVersionTest {
    private fun v(s: String) = checkNotNull(KavitaVersion.parse(s))

    @Test
    fun parsesAndCompares() {
        assertEquals("0.9.0.2", v("0.9.0.2").toString())
        assertEquals(v("0.9"), v("0.9.0.0"))
        assertTrue(v("0.10.0.0") > v("0.9.9.9"))
        assertTrue(v("v0.9.0.2-beta") == v("0.9.0.2"))
        assertNull(KavitaVersion.parse("nightly"))
        assertNull(KavitaVersion.parse(null))
    }

    @Test
    fun minimumSupportedIsTheSecurityHotfix() {
        assertTrue(v("0.9.0.2").isSupported)
        assertTrue(v("0.9.1").isSupported)
        assertFalse(v("0.9.0.1").isSupported)
        assertFalse(v("0.8.6.0").isSupported)
    }
}
