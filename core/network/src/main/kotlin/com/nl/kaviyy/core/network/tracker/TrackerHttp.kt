package com.nl.kaviyy.core.network.tracker

import com.nl.kaviyy.core.network.JsonSizeLimitInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The client for public tracker APIs (AniList, MyAnimeList, Kitsu): HTTPS with TLS 1.2+ only, no redirects to
 * other schemes, JSON size limit. Separate from the per-server Kavita clients: no Kavita credentials or pins here.
 */
fun trackerHttpClient(base: OkHttpClient): OkHttpClient =
    base
        .newBuilder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .followSslRedirects(false)
        .addInterceptor(JsonSizeLimitInterceptor())
        .build()

internal val trackerJson = Json { ignoreUnknownKeys = true }

/** Runs [request] on [io]; non-2xx → [TrackerHttpException]; returns the parsed JSON body (or null). */
internal suspend fun OkHttpClient.json(
    request: Request,
    io: CoroutineDispatcher,
): JsonElement? =
    withContext(io) {
        newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw TrackerHttpException(r.code)
            val text = r.body.string()
            if (text.isBlank()) null else trackerJson.parseToJsonElement(text)
        }
    }

// Small, forgiving JSON accessors: trackers omit fields freely.
internal fun JsonElement?.obj(key: String): JsonObject? =
    (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }?.jsonObject

internal fun JsonElement?.arr(key: String): JsonArray =
    (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }?.jsonArray ?: JsonArray(emptyList())

internal fun JsonElement?.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.int(key: String): Int? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.intOrNull

internal fun JsonElement?.long(key: String): Long? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull

/** Strings from an object's values or an array (title maps, synonym lists). */
internal fun JsonElement?.strings(): List<String> =
    when (this) {
        is JsonObject -> values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }
