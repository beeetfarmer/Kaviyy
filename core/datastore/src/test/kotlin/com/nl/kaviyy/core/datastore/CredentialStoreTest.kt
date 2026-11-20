package com.nl.kaviyy.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.server.ServerId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class CredentialStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val aead: Aead =
        newAead()

    private val jwt = Credentials.Jwt("aarav", "jwt-secret-token", "refresh-secret")

    private fun TestScope.store(file: File) =
        CredentialStore(
            DataStoreFactory.create(
                serializer = CredentialSerializer(aead),
                corruptionHandler = ReplaceFileCorruptionHandler { CredentialFile() },
                scope = backgroundScope,
                produceFile = { file },
            ),
        )

    @Test
    fun savesAndIsolatesPerServer() =
        runTest {
            val store = store(tmp.newFile("c.pb").apply { delete() })
            store.save(ServerId(1), jwt)
            store.save(ServerId(2), Credentials.AuthKey("api-secret"))
            assertEquals(jwt, store.credentials(ServerId(1)).first())
            assertEquals(Credentials.AuthKey("api-secret"), store.credentials(ServerId(2)).first())
            assertNull(store.credentials(ServerId(3)).first())
        }

    @Test
    fun clearRemovesOnlyThatServer() =
        runTest {
            val store = store(tmp.newFile("c.pb").apply { delete() })
            store.save(ServerId(1), jwt)
            store.save(ServerId(2), jwt)
            store.clear(ServerId(1))
            assertNull(store.credentials(ServerId(1)).first())
            assertEquals(jwt, store.credentials(ServerId(2)).first())
            store.clearAll()
            assertNull(store.credentials(ServerId(2)).first())
        }

    @Test
    fun fileOnDiskIsEncrypted() =
        runTest {
            val file = tmp.newFile("c.pb").apply { delete() }
            store(file).save(ServerId(1), jwt)
            val raw = String(file.readBytes(), Charsets.ISO_8859_1)
            listOf("jwt-secret-token", "refresh-secret", "aarav").forEach { assertFalse(it in raw, it) }
        }

    @Test
    fun tamperedFileResetsInsteadOfCrashing() =
        runTest {
            val file = tmp.newFile("c.pb").apply { writeBytes(ByteArray(64) { 7 }) }
            assertNull(store(file).credentials(ServerId(1)).first())
        }

    @Test
    fun ciphertextFromAnotherKeyIsRejected() =
        runTest {
            val file = tmp.newFile("c.pb").apply { delete() }
            store(file).save(ServerId(1), jwt)
            val otherKey =
                newAead()
            val result = runCatching { CredentialSerializer(otherKey).readFrom(file.inputStream()) }
            assertIs<CorruptionException>(result.exceptionOrNull())
        }
}

private fun newAead(): Aead {
    AeadConfig.register()
    return KeysetHandle
        .generateNew(PredefinedAeadParameters.AES256_GCM)
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
