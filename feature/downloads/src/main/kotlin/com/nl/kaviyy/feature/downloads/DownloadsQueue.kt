package com.nl.kaviyy.feature.downloads

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.ProgressBar
import com.nl.kaviyy.core.designsystem.component.rowDivider
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.network.seriesCoverUrl

@Composable
internal fun QueueList(state: DownloadsUiState) =
    GroupedList {
        state.queue.forEachIndexed { i, item ->
            QueueRow(item, state.server?.seriesCoverUrl(item.row.seriesId), divider = i < state.queue.lastIndex)
        }
    }

/** "Ember Road · Ch. 51", a 4dp bar and what's happening ("12 of 28 pages", "Paused", "Failed"). */
@Composable
private fun QueueRow(
    item: DownloadItem,
    coverUrl: String?,
    divider: Boolean,
) = Row(
    Modifier.fillMaxWidth().rowDivider(divider, 0.dp).padding(horizontal = 16.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Thumb(item.row.seriesId, coverUrl)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            queueTitle(item),
            style = Kavita.type.headline,
            color = Kavita.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ProgressBar(item.progress())
        val failed = item.state == DownloadState.FAILED
        Text(
            queueCaption(item),
            style = Kavita.type.footnote,
            color = if (failed) Kavita.colors.destructive else Kavita.colors.labelSecondary,
        )
    }
}

@Composable
private fun queueTitle(item: DownloadItem): String {
    val chapter = item.chapter?.number()?.let { stringResource(R.string.downloads_chapter, it) }
    return listOfNotNull(item.seriesName, chapter).joinToString(" · ").ifEmpty {
        stringResource(R.string.downloads_unknown_series)
    }
}

@Composable
private fun queueCaption(item: DownloadItem): String {
    val context = LocalContext.current
    val row = item.row
    val amount =
        when {
            row.pageImages && row.pagesTotal > 0 -> {
                stringResource(R.string.downloads_pages_of, row.pagesDone, row.pagesTotal)
            }

            !row.pageImages && row.bytesTotal > 0 -> {
                stringResource(
                    R.string.downloads_bytes_of,
                    Formatter.formatShortFileSize(context, row.bytesDone),
                    Formatter.formatShortFileSize(context, row.bytesTotal),
                )
            }

            else -> {
                null
            }
        }
    val status =
        when (item.state) {
            DownloadState.QUEUED -> stringResource(R.string.downloads_waiting)
            DownloadState.PAUSED -> stringResource(R.string.downloads_paused)
            DownloadState.FAILED -> stringResource(R.string.downloads_failed)
            else -> null
        }
    return listOfNotNull(status, amount).joinToString(" · ")
}

internal fun DownloadItem.progress(): Float {
    val (done, total) =
        if (row.pageImages) {
            row.pagesDone.toLong() to row.pagesTotal.toLong()
        } else {
            row.bytesDone to
                row.bytesTotal
        }
    return if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}
