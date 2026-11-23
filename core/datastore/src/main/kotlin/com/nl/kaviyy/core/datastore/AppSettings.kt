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
    /** Unused since collection / library rails replaced those shelves; kept so the numbers aren't reused. */
    @ProtoNumber(15) val collectionSort: String = "",
    /** Reading Lists order before [homeOrder] existed (Recent, NameAsc, NameDesc, Largest); read as a fallback. */
    @ProtoNumber(16) val readingListSort: String = "",
    @ProtoNumber(17) val librarySort: String = "",
    /** Per-section order, "OnDeck=NameAsc,Collection:3=Random"; sections not listed use their default order. */
    @ProtoNumber(18) val homeOrder: String = "",
    /** Home blurs the covers of series with any of these genres / tags (names, matched ignoring case). */
    @ProtoNumber(19) val blurTags: List<String> = emptyList(),
    /** Blur strength in dp. */
    @ProtoNumber(20) val blurRadius: Int = DEFAULT_BLUR_RADIUS,
    /** Home leaves out series with any of these genres / tags. */
    @ProtoNumber(21) val hideTags: List<String> = emptyList(),
    /** Series shown on Home despite [hideTags], as "serverId:seriesId". */
    @ProtoNumber(22) val unhidden: List<String> = emptyList(),
    /** Blur on or off; [blurTags] and [blurRadius] are kept either way. */
    @ProtoNumber(23) val blurEnabled: Boolean = true,
    /** Library leaves out series with any of these genres / tags. */
    @ProtoNumber(24) val libraryHideTags: List<String> = emptyList(),
    /** Series shown in Library despite [libraryHideTags], as "serverId:seriesId". */
    @ProtoNumber(25) val libraryUnhidden: List<String> = emptyList(),
) {
    /** Blur strength in effect: 0 while blur is switched off. */
    val activeBlurRadius: Int get() = if (blurEnabled) blurRadius else 0

    fun isUnhidden(
        serverId: Long,
        seriesId: Int,
        library: Boolean = false,
    ): Boolean = "$serverId:$seriesId" in (if (library) libraryUnhidden else unhidden)

    /** Home's (or Library's) exceptions with ([show] = true) or without the series. */
    fun withUnhidden(
        serverId: Long,
        seriesId: Int,
        show: Boolean,
        library: Boolean = false,
    ): AppSettings {
        val key = "$serverId:$seriesId"
        val list = if (library) libraryUnhidden else unhidden
        val updated = if (show) (list + key).distinct() else list - key
        return if (library) copy(libraryUnhidden = updated) else copy(unhidden = updated)
    }

    companion object {
        const val DEFAULT_ACCENT = "KavitaGreen"
        const val DEFAULT_BLUR_RADIUS = 16
        const val MAX_BLUR_RADIUS = 40
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
