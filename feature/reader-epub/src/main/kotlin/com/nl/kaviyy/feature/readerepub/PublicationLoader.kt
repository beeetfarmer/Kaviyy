package com.nl.kaviyy.feature.readerepub

import android.content.Context
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject

/** Opens a local EPUB with Readium. An interface so the ViewModel can be tested without Readium. */
interface PublicationLoader {
    suspend fun open(file: File): AppResult<Publication>
}

class ReadiumPublicationLoader
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : PublicationLoader {
        // Only local files are opened; the HTTP client is required by the API but never used for them.
        private val http = DefaultHttpClient()
        private val assets = AssetRetriever(context.contentResolver, http)
        private val opener = PublicationOpener(DefaultPublicationParser(context, http, assets, pdfFactory = null))

        override suspend fun open(file: File): AppResult<Publication> {
            val asset = assets.retrieve(file).getOrNull() ?: return AppResult.Failure(AppError.InvalidData)
            val publication = opener.open(asset, allowUserInteraction = false).getOrNull()
            if (publication == null) asset.close()
            return publication?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.InvalidData)
        }
    }
