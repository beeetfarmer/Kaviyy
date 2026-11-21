package com.nl.kaviyy.feature.downloads

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.ProgressBar
import com.nl.kaviyy.core.designsystem.component.rowDivider
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.network.seriesCoverUrl

@Composable
internal fun QueueList(
    state: DownloadsUiState,
    actions: DownloadsActions,
) = GroupedList {
    state.queue.forEachIndexed { i, item ->
        QueueRow(item, state.server?.seriesCoverUrl(item.row.seriesId), i < state.queue.lastIndex, actions)
    }
}

/** Header "more": Pause all / Resume all (Remove read downloads and Settings join with their features). */
@Composable
internal fun MoreMenu(
    state: DownloadsUiState,
    actions: DownloadsActions,
) = Box {
    var open by remember { mutableStateOf(false) }
    CircleIconButton(KavitaIcons.More, stringResource(R.string.downloads_more), { open = true })
    DropdownMenu(open, { open = false }, containerColor = Kavita.colors.surface, shape = KavitaShapes.card) {
        MenuItem(R.string.downloads_pause_all, state.queue.any { it.pausable }) {
            open = false
            actions.onPauseAll()
        }
        MenuItem(R.string.downloads_resume_all, state.queue.any { it.state == DownloadState.PAUSED }) {
            open = false
            actions.onResumeAll()
        }
    }
}

@Composable
private fun MenuItem(
    @StringRes label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) = DropdownMenuItem(
    text = {
        Text(
            stringResource(label),
            style = Kavita.type.body,
            color = if (enabled) Kavita.colors.label else Kavita.colors.labelTertiary,
        )
    },
    onClick = onClick,
    enabled = enabled,
)

/** "Ember Road · Ch. 51", a 4dp bar and what's happening ("12 of 28 pages", "Paused", "Failed"). */
@Composable
private fun QueueRow(
    item: DownloadItem,
    coverUrl: String?,
    divider: Boolean,
    actions: DownloadsActions,
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
            Modifier.then(
                if (failed) {
                    Modifier.clickable(
                        role = Role.Button,
                    ) { actions.onRetry(item.row.chapterId) }
                } else {
                    Modifier
                },
            ),
            style = Kavita.type.footnote,
            color = if (failed) Kavita.colors.destructive else Kavita.colors.labelSecondary,
        )
    }
    RowControls(item, actions)
}

/** Trailing circles: pause (running/waiting), resume (paused) or retry (failed), then cancel. */
@Composable
private fun RowControls(
    item: DownloadItem,
    actions: DownloadsActions,
) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    val id = item.row.chapterId
    when (item.state) {
        DownloadState.RUNNING, DownloadState.QUEUED -> {
            CircleIconButton(KavitaIcons.Pause, stringResource(R.string.downloads_pause), { actions.onPause(id) })
        }

        DownloadState.PAUSED -> {
            CircleIconButton(KavitaIcons.Play, stringResource(R.string.downloads_resume), { actions.onResume(id) })
        }

        DownloadState.FAILED -> {
            CircleIconButton(KavitaIcons.Play, stringResource(R.string.downloads_retry), { actions.onRetry(id) })
        }

        else -> {
            Unit
        }
    }
    CircleIconButton(KavitaIcons.Close, stringResource(R.string.downloads_cancel), { actions.onCancel(id) })
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
            DownloadState.FAILED -> return stringResource(R.string.downloads_failed_retry)
            else -> null
        }
    // ponytail: no live speed ("3.1 MB/s"); add a rolling rate from progress events if people ask.
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
