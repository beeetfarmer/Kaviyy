package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.database.SeriesDao
import com.nl.kaviyy.core.datastore.TrackerAccount
import com.nl.kaviyy.core.datastore.TrackerStore
import com.nl.kaviyy.core.model.log.Log
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.tracker.AniListApi
import com.nl.kaviyy.core.network.tracker.KitsuApi
import com.nl.kaviyy.core.network.tracker.MalApi
import com.nl.kaviyy.core.network.tracker.TrackerApi
import com.nl.kaviyy.core.network.tracker.TrackerHttpException
import com.nl.kaviyy.core.network.tracker.TrackerLogin
import com.nl.kaviyy.core.network.tracker.TrackerMedia
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Trackers the app scrobbles to directly (no Kavita+ needed). */
enum class Tracker { AniList, Mal, Kitsu }

/** Told when a chapter is finished or marked read; the next sync sends the series' progress to trackers. */
fun interface ScrobbleSignal {
    suspend fun finished(
        serverId: Long,
        seriesId: Int,
    )

    companion object {
        val None = ScrobbleSignal { _, _ -> }
    }
}

/** The three clients, injectable so tests can point them at a mock server. */
open class TrackerApis(
    val aniList: AniListApi,
    val mal: MalApi,
    val kitsu: KitsuApi,
) {
    /** The scrobbling side of [tracker] (tests swap in fakes). */
    open fun of(tracker: Tracker): TrackerApi =
        when (tracker) {
            Tracker.AniList -> aniList
            Tracker.Mal -> mal
            Tracker.Kitsu -> kitsu
        }
}

/**
 * Direct scrobbling. Finishing a chapter queues its series; [syncAll] (run by the background sync) sends the highest
 * finished chapter number to every connected tracker. It never lowers progress the tracker already has, and only
 * links a series it is sure about: a tracker link in the series' Kavita metadata, else an exact title match.
 */
@Singleton
class TrackerSync
    @Inject
    constructor(
        private val store: TrackerStore,
        private val apis: TrackerApis,
        private val series: SeriesDao,
    ) : ScrobbleSignal {
        private val now: () -> Long = System::currentTimeMillis

        override suspend fun finished(
            serverId: Long,
            seriesId: Int,
        ) {
            if (store.snapshot().accounts.isEmpty()) return
            val key = "$serverId|$seriesId"
            store.update { f -> if (key in f.pending) f else f.copy(pending = f.pending + key) }
        }

        /** Sends every queued series on [servers]; ones that fail on the network stay queued for next time. */
        suspend fun syncAll(servers: List<Server>) {
            val file = store.snapshot()
            if (file.accounts.isEmpty() || file.pending.isEmpty()) return
            val ids = servers.map { it.id.value }.toSet()
            file.pending.forEach { key ->
                val serverId = key.substringBefore('|').toLongOrNull()
                val seriesId = key.substringAfter('|').toIntOrNull()
                if (serverId == null || seriesId == null || serverId !in ids) return@forEach
                val done =
                    runCatching {
                        push(serverId, seriesId)
                    }.onFailure { Log.w(TAG) { "scrobble failed: ${it.javaClass.simpleName}" } }
                if (done.isSuccess) store.update { f -> f.copy(pending = f.pending - key) }
            }
        }

        private suspend fun push(
            serverId: Long,
            seriesId: Int,
        ) {
            val chapters = chaptersRead(serverId, seriesId) ?: return
            val row = series.seriesRows(serverId, listOf(seriesId)).firstOrNull() ?: return
            val names = listOfNotNull(row.name, row.localizedName, row.sortName).distinct()
            val links =
                series
                    .observeMetadata(serverId, seriesId)
                    .first()
                    ?.webLinks
                    .orEmpty()
            store.snapshot().accounts.forEach { (name, account) ->
                val tracker = trackerOf(name) ?: return@forEach
                try {
                    pushTo(tracker, account, Scrobble("$name|$serverId|$seriesId", chapters, names, links))
                } catch (e: TrackerHttpException) {
                    // Signed out on the tracker, or the entry is gone: skip this tracker (reconnecting fixes it).
                    Log.w(TAG) { "$name rejected the update (${e.code})" }
                }
            }
        }

        private suspend fun pushTo(
            tracker: Tracker,
            account: TrackerAccount,
            scrobble: Scrobble,
        ) {
            val (key, chapters) = scrobble
            val file = store.snapshot()
            if ((file.sent[key] ?: 0) >= chapters) return
            val login = fresh(tracker, account)
            val api = apis.of(tracker)
            val mediaId =
                file.matches[key]
                    ?: match(tracker, api, login, scrobble.names, scrobble.links).also { id ->
                        remember(key, matches = id)
                    }
            if (mediaId == NO_MATCH) return
            val remote = api.progress(login, mediaId) ?: 0
            if (chapters > remote) api.save(login, mediaId, chapters)
            remember(key, sent = maxOf(chapters, remote))
        }

        private suspend fun remember(
            key: String,
            matches: Int? = null,
            sent: Int? = null,
        ) = store.update { f ->
            f.copy(
                matches = matches?.let { f.matches + (key to it) } ?: f.matches,
                sent = sent?.let { f.sent + (key to it) } ?: f.sent,
            )
        }

        /** MAL and Kitsu tokens expire (about a month): refresh a day early and keep the new ones. */
        private suspend fun fresh(
            tracker: Tracker,
            account: TrackerAccount,
        ): TrackerLogin {
            val current =
                TrackerLogin(
                    account.accessToken,
                    account.refreshToken,
                    account.expiresAt,
                    account.username,
                    account.userId,
                )
            val expiring = account.expiresAt != 0L && account.expiresAt < now() + REFRESH_MARGIN_MS
            // AniList: a year-long token with no refresh, so it's reconnected when it ends.
            val renewed =
                when {
                    !expiring || account.refreshToken.isEmpty() -> null
                    tracker == Tracker.Mal -> apis.mal.refresh(account.clientId, account.refreshToken)
                    tracker == Tracker.Kitsu -> apis.kitsu.refresh(account.refreshToken)
                    else -> null
                } ?: return current
            store.update { f -> f.copy(accounts = f.accounts + (tracker.name to renewed.toAccount(account.clientId))) }
            return renewed
        }

        private suspend fun match(
            tracker: Tracker,
            api: TrackerApi,
            login: TrackerLogin,
            names: List<String>,
            links: String,
        ): Int {
            linkedId(tracker, links)?.let { return it }
            return names.firstNotNullOfOrNull { title -> api.search(login, title).exactMatch(names)?.id } ?: NO_MATCH
        }

        /** Highest finished, numbered, non-special chapter (from the local cache), or null if none. */
        private suspend fun chaptersRead(
            serverId: Long,
            seriesId: Int,
        ): Int? =
            series
                .observeChapters(serverId, seriesId)
                .first()
                .filter { !it.isSpecial && it.pages > 0 && it.pagesRead >= it.pages }
                .mapNotNull { it.number()?.toFloatOrNull() }
                .maxOrNull()
                ?.toInt()
                ?.takeIf { it > 0 }
    }

/** The tracker's id from a link in Kavita's series web links ("https://anilist.co/manga/30013/…"). */
internal fun linkedId(
    tracker: Tracker,
    links: String,
): Int? {
    val pattern =
        when (tracker) {
            Tracker.AniList -> """anilist\.co/manga/(\d+)"""
            Tracker.Mal -> """myanimelist\.net/manga/(\d+)"""
            Tracker.Kitsu -> """kitsu\.(?:app|io)/manga/(\d+)(?:[/?#\s,]|$)"""
        }
    return Regex(pattern)
        .find(links)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}

/** Case, spacing and punctuation don't count: "Ember Road!" = "ember road". */
internal fun normalize(title: String): String = title.lowercase().filter(Char::isLetterOrDigit)

/** First search hit whose titles exactly match one of [names] (normalised), else null. */
internal fun List<TrackerMedia>.exactMatch(names: List<String>): TrackerMedia? {
    val wanted = names.map(::normalize).filter { it.isNotEmpty() }.toSet()
    return firstOrNull { m -> m.titles.any { normalize(it) in wanted } }
}

internal fun trackerOf(name: String) = Tracker.entries.firstOrNull { it.name == name }

/** One series' update for one tracker: the store key, chapters read, its titles and Kavita web links. */
private data class Scrobble(
    val key: String,
    val chapters: Int,
    val names: List<String>,
    val links: String,
)

private const val TAG = "Trackers"
private const val NO_MATCH = 0
private val REFRESH_MARGIN_MS = TimeUnit.DAYS.toMillis(1)
