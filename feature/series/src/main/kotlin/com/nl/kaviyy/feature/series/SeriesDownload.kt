package com.nl.kaviyy.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.DownloadEntity
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.download.downloadState

/** [derived] Series Download button states (DESIGN.md §04 actions). */
sealed interface SeriesDownload {
    data object Idle : SeriesDownload

    data class Active(
        val progress: Float,
    ) : SeriesDownload

    data object Done : SeriesDownload

    data object Error : SeriesDownload
}

/** Waiting/running/paused beat failed, failed beats done; done means every cached chapter is downloaded. */
internal fun seriesDownload(
    chapters: List<ChapterEntity>,
    rows: List<DownloadEntity>,
): SeriesDownload {
    val states = rows.map { it.downloadState }
    val active = setOf(DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.PAUSED)
    val done = states.count { it == DownloadState.COMPLETED }
    return when {
        states.any { it in active } -> SeriesDownload.Active((rows.sumOf { it.fraction() } / rows.size).toFloat())
        DownloadState.FAILED in states -> SeriesDownload.Error
        chapters.isNotEmpty() && done >= chapters.size -> SeriesDownload.Done
        else -> SeriesDownload.Idle
    }
}

private fun DownloadEntity.fraction(): Double =
    when {
        downloadState == DownloadState.COMPLETED -> 1.0
        pageImages && pagesTotal > 0 -> pagesDone.toDouble() / pagesTotal
        !pageImages && bytesTotal > 0 -> bytesDone.toDouble() / bytesTotal
        else -> 0.0
    }.coerceIn(0.0, 1.0)

/** 50dp round action: download icon, a progress ring, a check on accentContainer, or a red icon to retry. */
@Composable
internal fun DownloadButton(
    state: SeriesDownload,
    onDownload: () -> Unit,
) {
    val label =
        stringResource(
            when (state) {
                SeriesDownload.Idle -> R.string.series_download_series
                is SeriesDownload.Active -> R.string.series_downloading
                SeriesDownload.Done -> R.string.series_downloaded
                SeriesDownload.Error -> R.string.series_download_retry
            },
        )
    val tappable = state == SeriesDownload.Idle || state == SeriesDownload.Error
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (state == SeriesDownload.Done) Kavita.colors.accentContainer else Kavita.colors.surface)
            .border(0.5.dp, Kavita.colors.hairline, CircleShape)
            .then(if (tappable) Modifier.clickable(role = Role.Button, onClick = onDownload) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is SeriesDownload.Active -> {
                CircularProgressIndicator(
                    { state.progress },
                    Modifier.size(30.dp),
                    color = Kavita.colors.accent,
                    strokeWidth = 3.dp,
                    trackColor = Kavita.colors.track,
                )
                Icon(KavitaIcons.Download, null, Modifier.size(14.dp), tint = Kavita.colors.accent)
            }

            SeriesDownload.Done -> {
                Icon(KavitaIcons.Check, null, Modifier.size(24.dp), tint = Kavita.colors.accent)
            }

            SeriesDownload.Error -> {
                Icon(KavitaIcons.Download, null, Modifier.size(20.dp), tint = Kavita.colors.destructive)
            }

            SeriesDownload.Idle -> {
                Icon(KavitaIcons.Download, null, Modifier.size(20.dp), tint = Kavita.colors.accent)
            }
        }
    }
}
