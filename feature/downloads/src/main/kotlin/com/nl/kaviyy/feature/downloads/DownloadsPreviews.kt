package com.nl.kaviyy.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult

private const val MB = 1024L * 1024
private const val PAGES = 28

private fun item(
    chapterId: Int,
    seriesId: Int,
    series: String,
    number: Float,
    state: DownloadState,
    pages: Pair<Int, Int> = PAGES to PAGES,
    bytes: Long = 0,
    updatedAt: Long = 0,
    libraryType: Int = 0,
    pagesRead: Int = 0,
) = DownloadItem(
    DownloadEntity(
        1,
        chapterId,
        seriesId,
        1,
        state.name,
        pageImages = true,
        pagesDone = pages.first,
        pagesTotal = pages.second,
        bytesDone = bytes,
        updatedAt = updatedAt,
    ),
    series,
    ChapterEntity(
        1,
        chapterId,
        1,
        seriesId,
        range = "${number.toInt()}",
        minNumber = number,
        sortOrder = number,
        pages = pages.second,
        pagesRead = pagesRead,
        format = 1,
    ),
    libraryType,
)

internal val previewItems =
    listOf(
        item(1, 11, "Ember Road", 48f, DownloadState.COMPLETED, bytes = 140 * MB, pagesRead = PAGES),
        item(9, 11, "Ember Road", 50f, DownloadState.COMPLETED, bytes = 45 * MB, pagesRead = 7),
        item(2, 11, "Ember Road", 49f, DownloadState.COMPLETED, bytes = 132 * MB),
        item(3, 12, "Salt & Signal", 3f, DownloadState.COMPLETED, bytes = 61 * MB, libraryType = 1),
        item(8, 14, "The Quiet Archive", 1f, DownloadState.COMPLETED, bytes = 9 * MB, libraryType = 2),
        item(4, 11, "Ember Road", 51f, DownloadState.RUNNING, pages = 12 to 28, bytes = 30 * MB),
        item(5, 11, "Ember Road", 52f, DownloadState.QUEUED, pages = 0 to 0, updatedAt = 1),
        item(6, 13, "Northern Tides", 7f, DownloadState.PAUSED, pages = 9 to 30),
        item(7, 13, "Northern Tides", 8f, DownloadState.FAILED, pages = 2 to 30),
    )

internal val previewServer =
    Server(ServerId(1), "Home", (ServerUrl.parse("https://kavita.home.lan") as ServerUrlResult.Valid).url)

@Composable
internal fun DownloadsPreview(
    appearance: Appearance,
    segment: Int = 0,
    items: List<DownloadItem> = previewItems,
) = KavitaTheme(appearance = appearance) {
    DownloadsScreen(items.toUiState(previewServer, segment), DownloadsActions(), freeBytes = 28_400 * MB)
}

@Preview(name = "Downloaded · light")
@Composable
private fun DownloadedLight() = DownloadsPreview(Appearance.Light)

@Preview(name = "Downloaded · dark")
@Composable
private fun DownloadedDark() = DownloadsPreview(Appearance.Dark)

@Preview(name = "Queue · light")
@Composable
private fun QueueLight() = DownloadsPreview(Appearance.Light, segment = 1)

@Preview(name = "Queue · dark")
@Composable
private fun QueueDark() = DownloadsPreview(Appearance.Dark, segment = 1)

@Preview(name = "Empty · light")
@Composable
private fun EmptyLight() = DownloadsPreview(Appearance.Light, items = emptyList())

@Preview(name = "Empty · dark")
@Composable
private fun EmptyDark() = DownloadsPreview(Appearance.Dark, items = emptyList())
