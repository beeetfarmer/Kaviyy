package com.nl.kaviyy.core.data

import android.content.Context
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.apiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

const val ROLE_DOWNLOAD = "Download"

/** Kavita only hands out original files (needed by the EPUB/PDF readers) to users with the Download role. */
val Server.canDownload: Boolean get() = ROLE_DOWNLOAD in roles || ROLE_ADMIN in roles

/** Original book files for the EPUB/PDF readers, fetched once into an app-private cache. */
interface BookFileRepository {
    suspend fun file(
        server: Server,
        chapterId: Int,
        extension: String,
    ): AppResult<File>
}

@Singleton
class CachedBookFileRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val apis: ServerApis,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
    ) : BookFileRepository {
        // Ids only in paths; cacheDir is app-private and never backed up.
        private val root = File(context.cacheDir, "books")
        private val lock = Mutex()

        override suspend fun file(
            server: Server,
            chapterId: Int,
            extension: String,
        ): AppResult<File> {
            if (!server.canDownload) return AppResult.Failure(AppError.Forbidden)
            val dir = File(root, "${server.id.value}")
            val target = File(dir, "$chapterId.${extension.filter(Char::isLetter).take(MAX_EXT)}")
            return lock.withLock {
                withContext(io) {
                    if (target.length() > 0) {
                        target.setLastModified(System.currentTimeMillis())
                        return@withContext AppResult.Success(target)
                    }
                    dir.mkdirs()
                    val temp = File(dir, "${target.name}.part")
                    val result =
                        apiCall {
                            apis.api(server).downloadChapter(chapterId).use { body ->
                                temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                            }
                        }
                    when {
                        result is AppResult.Failure -> {
                            temp.delete()
                            result
                        }

                        !temp.isZip() -> { // EPUB is a zip; anything else (an HTML error page) is not a book
                            temp.delete()
                            AppResult.Failure(AppError.InvalidData)
                        }

                        temp.renameTo(target) -> {
                            evict(dir)
                            AppResult.Success(target)
                        }

                        else -> {
                            temp.delete()
                            AppResult.Failure(AppError.Unknown)
                        }
                    }
                }
            }
        }

        // ponytail: keeps the newest MAX_CACHED books per server; switch to a size budget if books get huge.
        private fun evict(dir: File) {
            dir
                .listFiles { f -> !f.name.endsWith(".part") }
                .orEmpty()
                .sortedByDescending { it.lastModified() }
                .drop(MAX_CACHED)
                .forEach { it.delete() }
        }

        private fun File.isZip(): Boolean =
            length() > ZIP_MAGIC.size && inputStream().use { s -> ZIP_MAGIC.all { it.toInt() == s.read() } }

        private companion object {
            const val MAX_CACHED = 5
            const val MAX_EXT = 5
            val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        }
    }
