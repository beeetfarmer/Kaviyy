package com.nl.kaviyy.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

/**
 * Direct scrobbling state (AniList, MyAnimeList, Kitsu), sealed like the server credentials: accounts with their
 * tokens, the series → tracker entry matches, what was last sent, and series waiting to be sent.
 */
@Suppress("MagicNumber")
@Serializable
data class TrackerFile(
    /** Keyed by tracker name ("AniList", "Mal", "Kitsu"). */
    @ProtoNumber(1) val accounts: Map<String, TrackerAccount> = emptyMap(),
    /** "tracker|serverId|seriesId" → the tracker's media id; 0 = searched, no confident match. */
    @ProtoNumber(2) val matches: Map<String, Int> = emptyMap(),
    /** Same key → the chapter count last sent. */
    @ProtoNumber(3) val sent: Map<String, Int> = emptyMap(),
    /** "serverId|seriesId" with newly finished chapters. */
    @ProtoNumber(4) val pending: List<String> = emptyList(),
)

@Suppress("MagicNumber")
@Serializable
data class TrackerAccount(
    @ProtoNumber(1) val username: String = "",
    @ProtoNumber(2) val accessToken: String = "",
    @ProtoNumber(3) val refreshToken: String = "",
    /** Epoch millis; 0 = doesn't expire (or unknown). */
    @ProtoNumber(4) val expiresAt: Long = 0,
    /** The user's own app registration (AniList/MAL require one). */
    @ProtoNumber(5) val clientId: String = "",
    /** The tracker's user id (Kitsu library entries need it). */
    @ProtoNumber(6) val userId: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
class TrackerSerializer(
    private val aead: Aead,
) : Serializer<TrackerFile> {
    override val defaultValue = TrackerFile()

    override suspend fun readFrom(input: InputStream): TrackerFile {
        val sealed = input.readBytes()
        if (sealed.isEmpty()) return defaultValue
        return try {
            ProtoBuf.decodeFromByteArray(aead.decrypt(sealed, AAD))
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Tracker file failed authentication", e)
        } catch (e: SerializationException) {
            throw CorruptionException("Tracker file unreadable", e)
        }
    }

    override suspend fun writeTo(
        t: TrackerFile,
        output: OutputStream,
    ) = output.write(aead.encrypt(ProtoBuf.encodeToByteArray(t), AAD))

    private companion object {
        val AAD = "kaviyy.trackers.v1".toByteArray()
    }
}

@Singleton
class TrackerStore
    @Inject
    constructor(
        private val dataStore: DataStore<TrackerFile>,
    ) {
        val accounts: Flow<Map<String, TrackerAccount>> = dataStore.data.map { it.accounts }.distinctUntilChanged()

        suspend fun snapshot(): TrackerFile = dataStore.data.first()

        suspend fun update(transform: (TrackerFile) -> TrackerFile) {
            dataStore.updateData(transform)
        }

        /** Disconnecting forgets the account and its matches (a new account may have different entries). */
        suspend fun disconnect(tracker: String) =
            update { f ->
                f.copy(
                    accounts = f.accounts - tracker,
                    matches = f.matches.filterKeys { !it.startsWith("$tracker|") },
                    sent = f.sent.filterKeys { !it.startsWith("$tracker|") },
                )
            }

        /** Server removal / local wipe. */
        suspend fun forgetServer(serverId: Long) =
            update { f ->
                f.copy(
                    matches = f.matches.filterKeys { it.split('|').getOrNull(1) != "$serverId" },
                    sent = f.sent.filterKeys { it.split('|').getOrNull(1) != "$serverId" },
                    pending = f.pending.filterNot { it.startsWith("$serverId|") },
                )
            }

        suspend fun clearAll() = update { TrackerFile() }
    }
