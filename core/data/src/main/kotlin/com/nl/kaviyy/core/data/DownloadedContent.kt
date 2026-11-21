package com.nl.kaviyy.core.data

import android.content.Context
import com.nl.kaviyy.core.database.DownloadDao
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.ChapterInfoDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Written last into a finished chapter folder; readers open local content from it. */
@Serializable
data class DownloadManifest(
    val chapterId: Int,
    val pages: List<ManifestPage> = emptyList(),
    /** File name of the original book (EPUB), when not downloaded as pages. */
    val book: String? = null,
    /** The chapter info the pages were fetched with, so the image reader can open offline. */
    val info: ChapterInfoDto? = null,
)

@Serializable
data class ManifestPage(
    val page: Int,
    val file: String,
    val mime: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/** A finished download: its folder and manifest. */
data class LocalChapter(
    val dir: File,
    val manifest: DownloadManifest,
) {
    val pageFiles: List<File> get() = manifest.pages.sortedBy { it.page }.map { File(dir, it.file) }
    val bookFile: File? get() = manifest.book?.let { File(dir, it) }
}

/**
 * Where downloads live: `filesDir/downloads/{serverId}/{seriesId}/{chapterId}/` (ids only, never backed up).
 * core:download writes there; readers look here first. Only COMPLETED rows count, so a half-written folder is
 * never opened.
 */
@Singleton
class DownloadedContent
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val dao: DownloadDao,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) {
        val root = File(context.filesDir, "downloads")

        fun dir(
            serverId: Long,
            seriesId: Int,
            chapterId: Int,
        ): File = guardedUnder(root, File(root, "$serverId/$seriesId/$chapterId"))

        suspend fun chapter(
            server: Server,
            chapterId: Int,
        ): LocalChapter? {
            val row = dao.get(server.id.value, chapterId)?.takeIf { it.state == COMPLETED } ?: return null
            return withContext(io) {
                val dir = dir(row.serverId, row.seriesId, row.chapterId)
                runCatching { json.decodeFromString<DownloadManifest>(File(dir, MANIFEST).readText()) }
                    .getOrNull()
                    ?.let { LocalChapter(dir, it) }
            }
        }

        companion object {
            const val MANIFEST = "manifest.json"
            private const val COMPLETED = "COMPLETED"
            val json = Json { ignoreUnknownKeys = true }
        }
    }

/** [file] if it is inside [root] (after resolving `..` and links), else throws. */
fun guardedUnder(
    root: File,
    file: File,
): File {
    require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "path escapes downloads" }
    return file
}
