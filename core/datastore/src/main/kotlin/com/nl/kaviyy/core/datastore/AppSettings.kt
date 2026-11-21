package com.nl.kaviyy.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { System, Light, Dark }

/** Device-wide app preferences (not secret, not per server). Proto numbers are append-only. */
@Suppress("MagicNumber") // proto field numbers
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AppSettings(
    @ProtoNumber(1) val theme: ThemeMode = ThemeMode.System,
    /** An `AccentPreset` name; unknown names fall back to the default. */
    @ProtoNumber(2) val accent: String = DEFAULT_ACCENT,
    /** Android 12+: take the accent from the wallpaper. */
    @ProtoNumber(3) val dynamicColor: Boolean = false,
    /** Ask for the device PIN/biometric on launch and after [lockAfterMinutes] away. */
    @ProtoNumber(4) val appLock: Boolean = false,
    /** 0 = as soon as the app leaves the screen. */
    @ProtoNumber(5) val lockAfterMinutes: Int = 1,
    /** Blank the Recents thumbnail (and, before Android 13, block screenshots). */
    @ProtoNumber(6) val hideInRecents: Boolean = false,
    /** Downloads wait for an unmetered network. */
    @ProtoNumber(7) val downloadWifiOnly: Boolean = true,
    /** Total downloads cap in MB; 0 = no limit. */
    @ProtoNumber(8) val storageLimitMb: Int = 0,
    /** Next unread chapters of On Deck series to fetch in the background; 0 = off. */
    @ProtoNumber(9) val autoDownloadCount: Int = 0,
    /** Background auto-downloads also wait for the charger. */
    @ProtoNumber(10) val autoDownloadChargingOnly: Boolean = true,
    /** Days after reading before a downloaded chapter is removed; -1 = never, 0 = immediately. */
    @ProtoNumber(11) val autoDeleteAfterDays: Int = -1,
    /** Auto-delete keeps at least this many of the most recently read chapters per series. */
    @ProtoNumber(12) val keepReadCount: Int = 0,
    /** Chapters downloading at once (1–4). */
    @ProtoNumber(13) val concurrentDownloads: Int = 2,
    /**
     * Home sections in order, comma-separated `HomeSection` names, "!" before hidden ones. Empty = the default
     * layout; sections missing from the string (added in later versions) are appended in their default place.
     */
    @ProtoNumber(14) val homeLayout: String = "",
    /** `ListSort` names for the Collections, Reading Lists and Libraries rails; empty = default. */
    @ProtoNumber(15) val collectionSort: String = "",
    @ProtoNumber(16) val readingListSort: String = "",
    @ProtoNumber(17) val librarySort: String = "",
) {
    companion object {
        const val DEFAULT_ACCENT = "KavitaGreen"
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings =
        try {
            ProtoBuf.decodeFromByteArray(AppSettings.serializer(), input.readBytes())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable settings", e)
        }

    override suspend fun writeTo(
        t: AppSettings,
        output: OutputStream,
    ) = output.write(ProtoBuf.encodeToByteArray(AppSettings.serializer(), t))
}

@Singleton
class AppSettingsStore
    @Inject
    constructor(
        private val store: DataStore<AppSettings>,
    ) {
        val settings: Flow<AppSettings> =
            store.data.catch { emit(AppSettings()) }.distinctUntilChanged()

        suspend fun update(transform: (AppSettings) -> AppSettings) {
            store.updateData(transform)
        }
    }
