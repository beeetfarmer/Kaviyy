package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.datastore.TrackerAccount
import com.nl.kaviyy.core.datastore.TrackerStore
import com.nl.kaviyy.core.network.tracker.TrackerLogin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Connecting and disconnecting tracker accounts; [TrackerSync] does the scrobbling. Tokens go to [TrackerStore]. */
@Singleton
class TrackerAccounts
    @Inject
    constructor(
        private val store: TrackerStore,
        private val apis: TrackerApis,
    ) {
        val accounts: Flow<Map<Tracker, TrackerAccount>> =
            store.accounts.map { m -> m.mapNotNull { (k, v) -> trackerOf(k)?.let { it to v } }.toMap() }

        fun aniListAuthorizeUrl(clientId: String) = apis.aniList.authorizeUrl(clientId)

        suspend fun connectAniList(
            clientId: String,
            token: String,
        ) = save(Tracker.AniList, apis.aniList.viewer(token), clientId)

        /** A fresh PKCE verifier (43–128 unreserved characters); keep it until [connectMal]. */
        fun newMalVerifier(): String {
            val random = SecureRandom()
            return String(CharArray(VERIFIER_LENGTH) { VERIFIER_CHARS[random.nextInt(VERIFIER_CHARS.length)] })
        }

        fun malAuthorizeUrl(
            clientId: String,
            verifier: String,
        ) = apis.mal.authorizeUrl(clientId, verifier)

        suspend fun connectMal(
            clientId: String,
            pasted: String,
            verifier: String,
        ) = save(Tracker.Mal, apis.mal.exchange(clientId, pasted, verifier), clientId)

        suspend fun connectKitsu(
            email: String,
            password: String,
        ) = save(Tracker.Kitsu, apis.kitsu.login(email, password), "")

        suspend fun disconnect(tracker: Tracker) = store.disconnect(tracker.name)

        private suspend fun save(
            tracker: Tracker,
            login: TrackerLogin,
            clientId: String,
        ) = store.update { f -> f.copy(accounts = f.accounts + (tracker.name to login.toAccount(clientId.trim()))) }
    }

internal fun TrackerLogin.toAccount(clientId: String) =
    TrackerAccount(username, accessToken, refreshToken, expiresAt, clientId, userId)

private const val VERIFIER_LENGTH = 64

/** RFC 7636 unreserved characters, minus "~" (percent-encoded in some places, which a few servers mishandle). */
private const val VERIFIER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._"
