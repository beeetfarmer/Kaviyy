package com.nl.kaviyy.core.datastore

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsTest {
    @Test
    fun roundTrips() =
        runTest {
            val settings = AppSettings(ThemeMode.Dark, "Ocean", dynamicColor = true)
            val out = ByteArrayOutputStream()
            AppSettingsSerializer.writeTo(settings, out)
            assertEquals(settings, AppSettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
        }

    @Test
    fun emptyFileIsTheDefaults() =
        runTest {
            assertEquals(AppSettings(), AppSettingsSerializer.readFrom(ByteArrayInputStream(ByteArray(0))))
        }

    @Test
    fun garbageIsCorruption() =
        runTest {
            assertFailsWith<CorruptionException> {
                AppSettingsSerializer.readFrom(ByteArrayInputStream(byteArrayOf(0x0A, 0x7F, 0x01)))
            }
        }

    @Test
    fun sensitiveContentRoundTripsAndUnhiddenToggles() =
        runTest {
            val settings =
                AppSettings(blurTags = listOf("Ecchi"), blurRadius = 30, hideTags = listOf("Mature"))
                    .withUnhidden(1, 7, show = true)
                    .withUnhidden(1, 7, show = true)
                    .withUnhidden(2, 8, show = true)
            assertEquals(listOf("1:7", "2:8"), settings.unhidden)
            assertTrue(settings.isUnhidden(1, 7))
            assertFalse(settings.isUnhidden(2, 7)) // per server
            assertFalse(settings.withUnhidden(1, 7, show = false).isUnhidden(1, 7))
            val out = ByteArrayOutputStream()
            AppSettingsSerializer.writeTo(settings, out)
            assertEquals(settings, AppSettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
        }

    @Test
    fun libraryExceptionsAreSeparateAndBlurSwitchZeroesTheStrength() {
        val s = AppSettings(blurRadius = 12).withUnhidden(1, 7, show = true, library = true)
        assertTrue(s.isUnhidden(1, 7, library = true))
        assertFalse(s.isUnhidden(1, 7))
        assertEquals(12, s.activeBlurRadius)
        assertEquals(0, s.copy(blurEnabled = false).activeBlurRadius)
    }
}
