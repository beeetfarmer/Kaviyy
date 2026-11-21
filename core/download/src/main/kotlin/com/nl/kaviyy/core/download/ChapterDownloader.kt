package com.nl.kaviyy.core.download

import com.nl.kaviyy.core.data.DownloadManifest
import com.nl.kaviyy.core.data.DownloadedContent
import com.nl.kaviyy.core.data.DownloadedContent.Companion.MANIFEST
import com.nl.kaviyy.core.data.DownloadedContent.Companion.json
import com.nl.kaviyy.core.data.ManifestPage
import com.nl.kaviyy.core.data.ServerApis
import com.nl.kaviyy.core.data.guardedUnder
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.KavitaApi
import com.nl.kaviyy.core.network.apiCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_PARTIAL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Progress so far; return false to stop (the download was paused or removed). */
fun interface ProgressSink {
    suspend fun report(event: DownloadEvent.Progress): Boolean
}

sealed interface DownloadOutcome {
    data object Done : DownloadOutcome

    data object Stopped : DownloadOutcome

    data class Failed(
        val error: AppError,
    ) : DownloadOutcome
}

/**
 * Downloads one chapter into `filesDir/downloads/{serverId}/{seriesId}/{chapterId}/`: work happens in a sibling
 * `{chapterId}.part` folder that is kept between attempts (resume), verified, then renamed into place in one step.
 */
@Singleton
class ChapterDownloader
    @Inject
    constructor(
        private val content: DownloadedContent,
        private val apis: ServerApis,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        private val root = content.root

        /** The finished folder for a chapter. Built from ids only, and checked to stay under the downloads root. */
        fun dir(
            serverId: Long,
            seriesId: Int,
            chapterId: Int,
        ): File = content.dir(serverId, seriesId, chapterId)

        private fun partDir(row: DownloadEntity) =
            guardedUnder(root, File(root, "${row.serverId}/${row.seriesId}/${row.chapterId}.part"))

        /** The manifest of a finished download, or null. */
        suspend fun manifest(row: DownloadEntity): DownloadManifest? =
            withContext(io) {
                val file = File(dir(row.serverId, row.seriesId, row.chapterId), MANIFEST)
                runCatching { json.decodeFromString<DownloadManifest>(file.readText()) }.getOrNull()
            }

        /** Deletes the finished and partial folders of a chapter. */
        suspend fun delete(row: DownloadEntity) =
            withContext(io) {
                dir(row.serverId, row.seriesId, row.chapterId).deleteRecursively()
                partDir(row).deleteRecursively()
            }

        /** Everything under the downloads root (all servers). */
        suspend fun deleteAll() = withContext(io) { root.deleteRecursively() }

        suspend fun download(
            server: Server,
            row: DownloadEntity,
            progress: ProgressSink,
        ): DownloadOutcome =
            withContext(io) {
                try {
                    val part = partDir(row).apply { mkdirs() }
                    val api = apis.api(server)
                    if (row.pageImages) pages(api, row, part, progress) else book(api, row, part, progress)
                    val target = dir(row.serverId, row.seriesId, row.chapterId)
                    target.deleteRecursively() // an update replaces the old copy
                    if (part.renameTo(target)) DownloadOutcome.Done else DownloadOutcome.Failed(AppError.Unknown)
                } catch (abort: Abort) {
                    abort.outcome
                }
            }

        private suspend fun pages(
            api: KavitaApi,
            row: DownloadEntity,
            part: File,
            progress: ProgressSink,
        ) {
            val info = apiCall { api.chapterInfo(row.chapterId) }.orAbort()
            if (info.pages <= 0) fail(AppError.InvalidData)
            for (page in 0 until info.pages) {
                val file = File(part, "$page")
                if (file.length() == 0L) { // resume: finished pages are kept
                    val temp = File(part, "$page.tmp")
                    apiCall { api.pageImage(row.chapterId, page).use { stream(it.byteStream(), temp.outputStream()) } }
                        .orAbort()
                    if (temp.length() == 0L || !temp.renameTo(file)) fail(AppError.InvalidData)
                }
                val bytes = part.listFiles().orEmpty().sumOf { it.length() }
                progress.reportOrStop(DownloadEvent.Progress(page + 1, info.pages, bytes, 0))
            }
            val dims = info.pageDimensions.orEmpty().associateBy { it.pageNumber }
            val pages =
                (0 until info.pages).map { page ->
                    val file = File(part, "$page")
                    if (file.length() == 0L) fail(AppError.InvalidData) // verify every page
                    ManifestPage(page, file.name, sniffMime(file), dims[page]?.width ?: 0, dims[page]?.height ?: 0)
                }
            File(part, MANIFEST).writeText(json.encodeToString(DownloadManifest(row.chapterId, pages, info = info)))
        }

        private suspend fun book(
            api: KavitaApi,
            row: DownloadEntity,
            part: File,
            progress: ProgressSink,
        ) {
            val partial = File(part, "$BOOK.tmp")
            val have = partial.length()
            val response =
                apiCall {
                    api.downloadChapterFrom(row.chapterId, if (have > 0) "bytes=$have-" else null).also {
                        if (!it.isSuccessful || it.body() == null) throw HttpException(it)
                    }
                }.orAbort()
            val append = response.code() == HTTP_PARTIAL // otherwise the server sent the whole file again
            val start = if (append) have else 0L
            checkNotNull(response.body()).use { body ->
                val total = body.contentLength().takeIf { it >= 0 }?.plus(start) ?: 0L
                var reported = start
                ioOrAbort {
                    stream(body.byteStream(), FileOutputStream(partial, append)) { n ->
                        val done = start + n
                        if (done - reported >= REPORT_BYTES) {
                            reported = done
                            progress.reportOrStop(DownloadEvent.Progress(0, 0, done, total))
                        }
                    }
                }
                progress.reportOrStop(DownloadEvent.Progress(0, 0, partial.length(), maxOf(total, partial.length())))
            }
            if (!partial.isZip()) { // EPUB is a zip; an HTML error page is not
                partial.delete()
                fail(AppError.InvalidData)
            }
            if (!partial.renameTo(File(part, BOOK))) fail(AppError.Unknown)
            File(part, MANIFEST).writeText(json.encodeToString(DownloadManifest(row.chapterId, book = BOOK)))
        }

        internal companion object {
            const val BOOK = "book.epub"
            private const val REPORT_BYTES = 512L * 1024
        }
    }

/** Ends a download early with [outcome] (failure or stop); caught in [ChapterDownloader.download]. */
private class Abort(
    val outcome: DownloadOutcome,
) : Exception()

private fun fail(error: AppError): Nothing = throw Abort(DownloadOutcome.Failed(error))

private fun <T> AppResult<T>.orAbort(): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> fail(error)
    }

private suspend fun ProgressSink.reportOrStop(event: DownloadEvent.Progress) {
    if (!report(event)) throw Abort(DownloadOutcome.Stopped)
}

/** I/O errors while writing a body become a network failure (the connection dropped mid-file). */
private inline fun ioOrAbort(block: () -> Unit) =
    try {
        block()
    } catch (e: IOException) {
        fail(AppError.from(e))
    }

private const val BUFFER = 64 * 1024
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** Copies [input] to [output] (closing it), checking for cancellation; [onBytes] gets the running total. */
private suspend fun stream(
    input: InputStream,
    output: OutputStream,
    onBytes: suspend (Long) -> Unit = {},
) = output.use { out ->
    val buffer = ByteArray(BUFFER)
    var total = 0L
    var n = input.read(buffer)
    while (n >= 0) {
        coroutineContext.ensureActive()
        out.write(buffer, 0, n)
        total += n
        onBytes(total)
        n = input.read(buffer)
    }
}

private fun File.isZip(): Boolean =
    length() > ZIP_MAGIC.size && inputStream().use { s -> ZIP_MAGIC.all { it.toInt() == s.read() } }

/** Image type from the first bytes (the page endpoint's Content-Type isn't kept across resumes). */
@Suppress("MagicNumber")
internal fun sniffMime(file: File): String? {
    val head = ByteArray(12)
    val n = file.inputStream().use { it.read(head) }
    if (n < 4) return null

    fun at(
        i: Int,
        vararg b: Int,
    ) = b.withIndex().all { (k, v) -> head[i + k] == v.toByte() }
    return when {
        at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
        at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
        at(0, 0x47, 0x49, 0x46) -> "image/gif"
        n >= 12 && at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp"
        n >= 12 && at(4, 0x66, 0x74, 0x79, 0x70) -> "image/avif"
        else -> null
    }
}
