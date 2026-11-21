package com.nl.kaviyy.core.datastore

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
