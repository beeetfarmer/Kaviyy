package com.nl.kaviyy.core.network.tracker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** A tracker answered with an error status. 401 means the token is gone (reconnect). */
class TrackerHttpException(
    val code: Int,
) : IOException("Tracker HTTP $code")

/** A manga entry on a tracker, with every title it's known by (for exact-title matching). */
data class TrackerMedia(
    val id: Int,
    val titles: List<String>,
)

/** A signed-in tracker account. [expiresAt] 0 = no expiry known. */
data class TrackerLogin(
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAt: Long = 0,
    val username: String = "",
    val userId: String = "",
)

/** What every tracker does for scrobbling. Progress is a chapter count. */
interface TrackerApi {
    suspend fun search(
        login: TrackerLogin,
        title: String,
    ): List<TrackerMedia>

    /** The user's progress on [mediaId], or null when it isn't on their list. */
    suspend fun progress(
        login: TrackerLogin,
        mediaId: Int,
    ): Int?

    /** Puts [mediaId] on the user's list as reading, at [chapters]. */
    suspend fun save(
        login: TrackerLogin,
        mediaId: Int,
        chapters: Int,
    )
}

private val JSON = "application/json".toMediaType()
private val JSON_API = "application/vnd.api+json".toMediaType()
private const val SEARCH_LIMIT = 10
private const val SECONDS_TO_MS = 1000L

/** [segments] appended to this base URL ("manga/12/my_list_status"). */
private fun HttpUrl.path(segments: String): HttpUrl = newBuilder().addPathSegments(segments).build()

private fun Request.Builder.bearer(login: TrackerLogin) = header("Authorization", "Bearer ${login.accessToken}")

private fun expiry(
    expiresInSeconds: Long?,
    now: Long,
) = expiresInSeconds?.let { now + it * SECONDS_TO_MS } ?: 0L

/**
 * AniList GraphQL (https://docs.anilist.co). Sign-in is the implicit grant shown as a PIN page: the user's own API
 * client must use `https://anilist.co/api/v2/oauth/pin` as its redirect URL; they paste the token it shows.
 */
class AniListApi(
    private val http: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val endpoint: HttpUrl = "https://graphql.anilist.co".toHttpUrl(),
) : TrackerApi {
    fun authorizeUrl(clientId: String): String =
        "https://anilist.co/api/v2/oauth/authorize"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("client_id", clientId.trim())
            .addQueryParameter("response_type", "token")
            .build()
            .toString()

    /** Checks a pasted token and reads who it belongs to. */
    suspend fun viewer(token: String): TrackerLogin {
        val viewer = query(TrackerLogin(token.trim()), "query { Viewer { id name } }").obj("Viewer")
        return TrackerLogin(
            token.trim(),
            username = viewer.str("name").orEmpty(),
            userId = viewer.int("id")?.toString().orEmpty(),
        )
    }

    override suspend fun search(
        login: TrackerLogin,
        title: String,
    ): List<TrackerMedia> =
        query(
            login,
            "query (\$s: String) { Page(perPage: $SEARCH_LIMIT) { media(search: \$s, type: MANGA) " +
                "{ id title { romaji english native } synonyms } } }",
            "s" to JsonPrimitive(title),
        ).obj("Page").arr("media").mapNotNull { m ->
            m.int("id")?.let { TrackerMedia(it, m.obj("title").strings() + m.arr("synonyms").strings()) }
        }

    override suspend fun progress(
        login: TrackerLogin,
        mediaId: Int,
    ): Int? =
        query(
            login,
            "query (\$id: Int) { Media(id: \$id) { mediaListEntry { progress } } }",
            "id" to JsonPrimitive(mediaId),
        ).obj("Media")
            .obj("mediaListEntry")
            .int("progress")

    override suspend fun save(
        login: TrackerLogin,
        mediaId: Int,
        chapters: Int,
    ) {
        query(
            login,
            "mutation (\$id: Int, \$p: Int) " +
                "{ SaveMediaListEntry(mediaId: \$id, progress: \$p, status: CURRENT) { id } }",
            "id" to JsonPrimitive(mediaId),
            "p" to JsonPrimitive(chapters),
        )
    }

    private suspend fun query(
        login: TrackerLogin,
        query: String,
        vararg variables: Pair<String, JsonPrimitive>,
    ) = http
        .json(
            Request
                .Builder()
                .url(endpoint)
                .bearer(login)
                .header("Accept", "application/json")
                .post(
                    buildJsonObject {
                        put("query", query)
                        putJsonObject("variables") { variables.forEach { (k, v) -> put(k, v) } }
                    }.toString().toRequestBody(JSON),
                ).build(),
            io,
        ).obj("data")
}

/**
 * MyAnimeList API v2 (https://myanimelist.net/apiconfig/references/api/v2). OAuth 2 with PKCE (`plain`): the user
 * registers an app (type "other") with any redirect URL, approves in the browser, then pastes the URL they land on
 * (or just its `code`).
 */
class MalApi(
    private val http: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val api: HttpUrl = "https://api.myanimelist.net/v2/".toHttpUrl(),
    private val auth: HttpUrl = "https://myanimelist.net/v1/oauth2/".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) : TrackerApi {
    fun authorizeUrl(
        clientId: String,
        verifier: String,
    ): String =
        auth
            .path("authorize")
            .newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", clientId.trim())
            .addQueryParameter("code_challenge", verifier)
            .addQueryParameter("code_challenge_method", "plain")
            .build()
            .toString()

    suspend fun exchange(
        clientId: String,
        pasted: String,
        verifier: String,
    ): TrackerLogin =
        token(
            FormBody
                .Builder()
                .add("client_id", clientId.trim())
                .add("grant_type", "authorization_code")
                .add("code", codeFrom(pasted))
                .add("code_verifier", verifier)
                .build(),
        ).withUser()

    suspend fun refresh(
        clientId: String,
        refreshToken: String,
    ): TrackerLogin =
        token(
            FormBody
                .Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build(),
        ).withUser()

    override suspend fun search(
        login: TrackerLogin,
        title: String,
    ): List<TrackerMedia> {
        val url =
            api
                .path("manga")
                .newBuilder()
                .addQueryParameter("q", title.take(MAL_QUERY_MAX))
                .addQueryParameter("limit", "$SEARCH_LIMIT")
                .addQueryParameter("fields", "alternative_titles")
                .build()
        return get(login, url).arr("data").mapNotNull { item ->
            val node = item.obj("node")
            val alt = node.obj("alternative_titles")
            node.int("id")?.let { id ->
                TrackerMedia(
                    id,
                    listOfNotNull(node.str("title"), alt.str("en"), alt.str("ja")) + alt.arr("synonyms").strings(),
                )
            }
        }
    }

    override suspend fun progress(
        login: TrackerLogin,
        mediaId: Int,
    ): Int? {
        val url =
            api
                .path("manga/$mediaId")
                .newBuilder()
                .addQueryParameter("fields", "my_list_status")
                .build()
        return get(login, url).obj("my_list_status").int("num_chapters_read")
    }

    override suspend fun save(
        login: TrackerLogin,
        mediaId: Int,
        chapters: Int,
    ) {
        http.json(
            Request
                .Builder()
                .url(api.path("manga/$mediaId/my_list_status"))
                .bearer(login)
                .patch(
                    FormBody
                        .Builder()
                        .add("status", "reading")
                        .add("num_chapters_read", "$chapters")
                        .build(),
                ).build(),
            io,
        )
    }

    private suspend fun get(
        login: TrackerLogin,
        url: HttpUrl,
    ) = http.json(
        Request
            .Builder()
            .url(url)
            .bearer(login)
            .build(),
        io,
    )

    private suspend fun token(form: FormBody): TrackerLogin {
        val t =
            http.json(
                Request
                    .Builder()
                    .url(auth.path("token"))
                    .post(form)
                    .build(),
                io,
            )
        return TrackerLogin(
            t.str("access_token").orEmpty(),
            t.str("refresh_token").orEmpty(),
            expiry(t.long("expires_in"), now()),
        )
    }

    private suspend fun TrackerLogin.withUser(): TrackerLogin {
        val me = get(this, api.path("users/@me"))
        return copy(username = me.str("name").orEmpty(), userId = me.int("id")?.toString().orEmpty())
    }

    companion object {
        private const val MAL_QUERY_MAX = 64

        /** The `code` from a pasted redirect URL, or the paste itself when it's just the code. */
        fun codeFrom(pasted: String): String {
            val text = pasted.trim()
            return Regex("""[?&]code=([^&#\s]+)""").find(text)?.groupValues?.get(1) ?: text
        }
    }
}

/**
 * Kitsu JSON:API (https://kitsu.docs.apiary.io). Sign-in is the password grant: the password goes to Kitsu once
 * and is never stored; only the tokens are kept.
 */
class KitsuApi(
    private val http: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val base: HttpUrl = "https://kitsu.app/api/".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) : TrackerApi {
    suspend fun login(
        email: String,
        password: String,
    ): TrackerLogin =
        token(
            FormBody
                .Builder()
                .add("grant_type", "password")
                .add("username", email.trim())
                .add("password", password)
                .build(),
        ).withUser()

    suspend fun refresh(refreshToken: String): TrackerLogin =
        token(
            FormBody
                .Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build(),
        ).withUser()

    override suspend fun search(
        login: TrackerLogin,
        title: String,
    ): List<TrackerMedia> {
        val url =
            base
                .path("edge/manga")
                .newBuilder()
                .addQueryParameter("filter[text]", title)
                .addQueryParameter("page[limit]", "$SEARCH_LIMIT")
                .build()
        return get(login, url).arr("data").mapNotNull { item ->
            val a = item.obj("attributes")
            item.str("id")?.toIntOrNull()?.let { id ->
                TrackerMedia(
                    id,
                    listOfNotNull(a.str("canonicalTitle")) + a.obj("titles").strings() +
                        a.arr("abbreviatedTitles").strings(),
                )
            }
        }
    }

    override suspend fun progress(
        login: TrackerLogin,
        mediaId: Int,
    ): Int? = entry(login, mediaId)?.second

    override suspend fun save(
        login: TrackerLogin,
        mediaId: Int,
        chapters: Int,
    ) {
        val existing = entry(login, mediaId)?.first
        val body =
            buildJsonObject {
                putJsonObject("data") {
                    put("type", "libraryEntries")
                    existing?.let { put("id", it) }
                    putJsonObject("attributes") {
                        put("status", "current")
                        put("progress", chapters)
                    }
                    if (existing == null) {
                        putJsonObject("relationships") {
                            putJsonObject("user") {
                                putJsonObject("data") {
                                    put("type", "users")
                                    put("id", login.userId)
                                }
                            }
                            putJsonObject("manga") {
                                putJsonObject("data") {
                                    put("type", "manga")
                                    put("id", "$mediaId")
                                }
                            }
                        }
                    }
                }
            }.toString().toRequestBody(JSON_API)
        val request =
            Request.Builder().bearer(login).header("Accept", JSON_API.toString()).let {
                if (existing == null) {
                    it.url(base.path("edge/library-entries")).post(body)
                } else {
                    it.url(base.path("edge/library-entries/$existing")).patch(body)
                }
            }
        http.json(request.build(), io)
    }

    /** (entry id, progress) of the user's library entry for [mangaId]. */
    private suspend fun entry(
        login: TrackerLogin,
        mangaId: Int,
    ): Pair<String, Int>? {
        val url =
            base
                .path("edge/library-entries")
                .newBuilder()
                .addQueryParameter("filter[userId]", login.userId)
                .addQueryParameter("filter[mangaId]", "$mangaId")
                .build()
        val first = get(login, url).arr("data").firstOrNull()
        return first.str("id")?.let { id -> id to (first.obj("attributes").int("progress") ?: 0) }
    }

    private suspend fun get(
        login: TrackerLogin,
        url: HttpUrl,
    ) = http.json(
        Request
            .Builder()
            .url(url)
            .bearer(login)
            .header("Accept", JSON_API.toString())
            .build(),
        io,
    )

    private suspend fun token(form: FormBody): TrackerLogin {
        val t =
            http.json(
                Request
                    .Builder()
                    .url(base.path("oauth/token"))
                    .post(form)
                    .build(),
                io,
            )
        return TrackerLogin(
            t.str("access_token").orEmpty(),
            t.str("refresh_token").orEmpty(),
            expiry(t.long("expires_in"), now()),
        )
    }

    private suspend fun TrackerLogin.withUser(): TrackerLogin {
        val self =
            get(
                this,
                base
                    .path("edge/users")
                    .newBuilder()
                    .addQueryParameter("filter[self]", "true")
                    .build(),
            )
        val user = self.arr("data").firstOrNull()
        return copy(username = user.obj("attributes").str("name").orEmpty(), userId = user.str("id").orEmpty())
    }
}
