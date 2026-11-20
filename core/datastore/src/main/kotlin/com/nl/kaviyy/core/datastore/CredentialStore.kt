package com.nl.kaviyy.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.server.ServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/** On-disk shape (protobuf). A non-empty token means JWT (apiKey optional); otherwise apiKey alone is an Auth Key. */
@Suppress("MagicNumber")
@Serializable
data class CredentialFile(
    @ProtoNumber(1) val entries: Map<Long, Entry> = emptyMap(),
) {
    @Serializable
    data class Entry(
        @ProtoNumber(1) val username: String = "",
        @ProtoNumber(2) val token: String = "",
        @ProtoNumber(3) val refreshToken: String = "",
        @ProtoNumber(4) val apiKey: String = "",
    )
}

/**
 * Typed DataStore serializer: protobuf bytes sealed with Tink AEAD (AES-256-GCM, key wrapped by Android Keystore).
 * The associated data binds ciphertext to this file's purpose. A file that fails to decrypt raises
 * [CorruptionException] so the store's handler can reset it (the user signs in again; nothing crashes).
 */
@OptIn(ExperimentalSerializationApi::class)
class CredentialSerializer(
    private val aead: Aead,
) : Serializer<CredentialFile> {
    override val defaultValue = CredentialFile()

    override suspend fun readFrom(input: InputStream): CredentialFile {
        val sealed = input.readBytes()
        if (sealed.isEmpty()) return defaultValue
        return try {
            ProtoBuf.decodeFromByteArray(aead.decrypt(sealed, AAD))
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Credential file failed authentication", e)
        } catch (e: SerializationException) {
            throw CorruptionException("Credential file unreadable", e)
        }
    }

    override suspend fun writeTo(
        t: CredentialFile,
        output: OutputStream,
    ) = output.write(aead.encrypt(ProtoBuf.encodeToByteArray(t), AAD))

    private companion object {
        val AAD = "kaviyy.credentials.v1".toByteArray()
    }
}

/** Per-server secrets. Server metadata (URL, name, pin) lives in Room, never here. */
@Singleton
class CredentialStore
    @Inject
    constructor(
        private val dataStore: DataStore<CredentialFile>,
    ) {
        /** Servers that have stored credentials. */
        val serverIds: Flow<Set<ServerId>> =
            dataStore.data
                .map { file ->
                    file.entries.keys
                        .map(::ServerId)
                        .toSet()
                }.distinctUntilChanged()

        fun credentials(serverId: ServerId): Flow<Credentials?> =
            dataStore.data.map { it.entries[serverId.value]?.toCredentials() }.distinctUntilChanged()

        suspend fun save(
            serverId: ServerId,
            credentials: Credentials,
        ) {
            dataStore.updateData { it.copy(entries = it.entries + (serverId.value to credentials.toEntry())) }
        }

        suspend fun clear(serverId: ServerId) {
            dataStore.updateData { it.copy(entries = it.entries - serverId.value) }
        }

        suspend fun clearAll() {
            dataStore.updateData { CredentialFile() }
        }
    }

private fun Credentials.toEntry() =
    when (this) {
        is Credentials.Jwt -> CredentialFile.Entry(username, token, refreshToken, apiKey.orEmpty())
        is Credentials.AuthKey -> CredentialFile.Entry(apiKey = apiKey)
    }

private fun CredentialFile.Entry.toCredentials(): Credentials? =
    when {
        token.isNotEmpty() -> Credentials.Jwt(username, token, refreshToken, apiKey.ifEmpty { null })
        apiKey.isNotEmpty() -> Credentials.AuthKey(apiKey)
        else -> null
    }
