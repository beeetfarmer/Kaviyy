package com.nl.kaviyy.core.network

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageResult
import com.nl.kaviyy.core.model.server.Server
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

private const val IMAGE_DISK_CACHE_BYTES = 256L * 1024 * 1024
private val SECRET_PARAMS = setOf("apikey", "token", "access_token")

/**
 * Cache key for an image URL with credential query params removed, so a key never contains a secret and a
 * rotated key does not invalidate the cache. Returns null when nothing needs stripping.
 */
internal fun credentialFreeKey(url: String): String? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    val secret = parsed.queryParameterNames.filter { it.lowercase() in SECRET_PARAMS }
    return if (secret.isEmpty()) {
        null
    } else {
        secret
            .fold(
                parsed.newBuilder(),
            ) { b, n -> b.removeAllQueryParameters(n) }
            .build()
            .toString()
    }
}

/** Coil interceptor applying [credentialFreeKey] to memory and disk cache keys. */
internal object CredentialFreeCacheKeys : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val key = (chain.request.data as? String)?.let(::credentialFreeKey) ?: return chain.proceed()
        return chain
            .withRequest(
                chain.request
                    .newBuilder()
                    .memoryCacheKey(key)
                    .diskCacheKey(key)
                    .build(),
            ).proceed()
    }
}

/**
 * One Coil [ImageLoader] per server (N6): images go through that server's authenticated [OkHttpClient], so they get
 * the same TLS/pin/cleartext policy and host-scoped auth. Disk cache per server, deleted with the server.
 * The `apiKey` image parameter is added on the wire by AuthInterceptor, so request data and cache keys stay clean.
 */
object ServerImageLoaders {
    fun cacheDir(
        context: Context,
        server: Server,
    ) = File(context.cacheDir, "images/${server.id.value}")

    fun create(
        context: Context,
        server: Server,
        client: OkHttpClient,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(CredentialFreeCacheKeys)
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir(context, server).toOkioPath())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }.build()
}

/** Cover URLs (api/openapi.json, Image endpoints). `apiKey` is added on the wire by [AuthInterceptor], never here. */
fun Server.seriesCoverUrl(seriesId: Int): String = url.resolve("api/Image/series-cover?seriesId=$seriesId")

fun Server.volumeCoverUrl(volumeId: Int): String = url.resolve("api/Image/volume-cover?volumeId=$volumeId")

fun Server.collectionCoverUrl(collectionId: Int): String =
    url.resolve("api/Image/collection-cover?collectionTagId=$collectionId")

fun Server.chapterCoverUrl(chapterId: Int): String = url.resolve("api/Image/chapter-cover?chapterId=$chapterId")

/** One page of an image chapter (0-based [page]); for a PDF, Kavita renders the page to an image (`extractPdf`). */
fun Server.pageUrl(
    chapterId: Int,
    page: Int,
): String = url.resolve("api/Reader/image?chapterId=$chapterId&page=$page&extractPdf=true")
