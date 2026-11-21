package com.nl.kaviyy.feature.series

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedThumbRow
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.chapterCoverUrl
import com.nl.kaviyy.core.network.seriesCoverUrl
import com.nl.kaviyy.core.network.volumeCoverUrl

/** Segmented tabs + the selected tab's grouped list (screen 04, lower half). */
@Composable
internal fun SeriesTabs(
    detail: SeriesDetail,
    server: Server?,
    tab: SeriesTab,
    related: RelatedState,
    actions: SeriesActions,
    downloads: Map<Int, DownloadState> = emptyMap(),
) {
    val tabs = detail.tabs()
    val selected = tab.takeIf { it in tabs } ?: tabs.first()
    Column(Modifier.fillMaxWidth()) {
        SegmentedControl(tabs.map { stringResource(it.label()) }, tabs.indexOf(selected), { actions.onTab(tabs[it]) })
        Column(Modifier.padding(top = 16.dp)) {
            when (selected) {
                SeriesTab.Volumes -> VolumeRows(detail, server, actions)
                SeriesTab.Chapters -> ChapterRows(detail.numberedChapters(), server, actions, downloads)
                SeriesTab.Specials -> ChapterRows(detail.specials(), server, actions, downloads)
                SeriesTab.Related -> RelatedRows(related, server, actions)
            }
        }
    }
}

@Composable
private fun VolumeRows(
    detail: SeriesDetail,
    server: Server?,
    actions: SeriesActions,
) {
    val volumes = detail.realVolumes()
    GroupedList {
        volumes.forEachIndexed { i, v ->
            GroupedThumbRow(
                title = stringResource(R.string.series_volume, v.label()),
                subtitle = volumeSubtitle(v, detail.chapters.filter { it.volumeId == v.id }),
                state = stateText(rowState(v.pages, v.pagesRead, asPagesLeft = false)),
                stateColor = stateColor(rowState(v.pages, v.pagesRead, asPagesLeft = false)),
                onClick = { actions.onSheet(SeriesSheet.Volume(v.id)) },
                divider = i < volumes.lastIndex,
            ) { Thumb(v.seriesId, server?.volumeCoverUrl(v.id)) }
        }
    }
}

@Composable
private fun ChapterRows(
    chapters: List<ChapterEntity>,
    server: Server?,
    actions: SeriesActions,
    downloads: Map<Int, DownloadState>,
) {
    GroupedList {
        chapters.forEachIndexed { i, c ->
            val state = rowState(c.pages, c.pagesRead, asPagesLeft = true)
            val download = downloadLabel(downloads[c.id])?.let { stringResource(it) }
            GroupedThumbRow(
                title = chapterTitle(c),
                subtitle = listOfNotNull(chapterSubtitle(c), download).joinToString(" · "),
                state = stateText(state),
                stateColor = stateColor(state),
                onClick = { actions.onSheet(SeriesSheet.Chapter(c.id)) },
                divider = i < chapters.lastIndex,
            ) { Thumb(c.seriesId, server?.chapterCoverUrl(c.id)) }
        }
    }
}

@Composable
private fun RelatedRows(
    related: RelatedState,
    server: Server?,
    actions: SeriesActions,
) {
    when (related) {
        RelatedState.Idle, RelatedState.Loading -> {
            Hint(stringResource(R.string.series_loading))
        }

        is RelatedState.Failed -> {
            Hint(stringResource(R.string.series_related_failed))
        }

        is RelatedState.Loaded -> {
            if (related.items.isEmpty()) {
                Hint(stringResource(R.string.series_related_empty))
            } else {
                GroupedList {
                    related.items.forEachIndexed { i, r ->
                        val s = r.series
                        val state = rowState(s.pages, s.pagesRead, asPagesLeft = false)
                        GroupedThumbRow(
                            title = s.name,
                            subtitle = stringResource(r.relation.label()),
                            state = stateText(state),
                            stateColor = stateColor(state),
                            onClick = { actions.onOpenSeries(s.id) },
                            divider = i < related.items.lastIndex,
                        ) { Thumb(s.id, server?.seriesCoverUrl(s.id)) }
                    }
                }
            }
        }
    }
}

/** [derived] Chapter rows say "Downloaded" or "Downloading" after the page count. */
internal fun downloadLabel(state: DownloadState?): Int? =
    when (state) {
        DownloadState.COMPLETED -> R.string.series_downloaded
        DownloadState.QUEUED, DownloadState.RUNNING -> R.string.series_downloading
        else -> null
    }
