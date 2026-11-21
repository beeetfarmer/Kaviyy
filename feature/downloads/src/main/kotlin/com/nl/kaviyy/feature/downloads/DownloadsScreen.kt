package com.nl.kaviyy.feature.downloads

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedThumbRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.ProgressBar
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.component.rowDivider
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.download.DownloadItem
import com.nl.kaviyy.core.download.DownloadState
import com.nl.kaviyy.core.network.seriesCoverUrl

@Composable
fun DownloadsRoute(
    onOpenSeries: (Int) -> Unit,
    onBrowse: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DownloadsScreen(state, DownloadsActions(viewModel::select, onOpenSeries, onBrowse))
}

/** [derived] Screen 11: Downloaded (grouped by series) / Queue. */
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    actions: DownloadsActions,
    modifier: Modifier = Modifier,
) = LazyColumn(
    modifier.fillMaxSize().background(Kavita.colors.background).statusBarsPadding(),
    contentPadding =
        PaddingValues(
            start = KavitaSpacing.screenHorizontal,
            end = KavitaSpacing.screenHorizontal,
            top = KavitaSpacing.topBelowStatusBar,
            bottom = KavitaSpacing.contentBottomPadding,
        ),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    item { LargeTitleHeader(stringResource(R.string.downloads_title)) }
    item {
        SegmentedControl(
            listOf(
                stringResource(R.string.downloads_downloaded),
                stringResource(R.string.downloads_queue, state.queue.size),
            ),
            state.segment,
            actions.onSegment,
        )
    }
    when {
        state.loading -> Unit
        state.segment == 0 && state.downloaded.isEmpty() -> item { EmptyDownloads(actions.onBrowse) }
        state.segment == 0 -> item { DownloadedList(state, actions) }
        state.queue.isEmpty() -> item { Hint(stringResource(R.string.downloads_queue_empty)) }
        else -> item { QueueList(state) }
    }
}

@Composable
private fun DownloadedList(
    state: DownloadsUiState,
    actions: DownloadsActions,
) {
    val context = LocalContext.current
    GroupedList {
        state.downloaded.forEachIndexed { i, g ->
            val chapters = pluralStringResource(R.plurals.downloads_chapters, g.chapters, g.chapters)
            GroupedThumbRow(
                title = g.name ?: stringResource(R.string.downloads_unknown_series),
                subtitle = "$chapters · ${Formatter.formatShortFileSize(context, g.bytes)}",
                onClick = { actions.onOpenSeries(g.seriesId) },
                divider = i < state.downloaded.lastIndex,
            ) { Thumb(g.seriesId, state.server?.seriesCoverUrl(g.seriesId)) }
        }
    }
}

/** [derived] Book glyph in a 64 accent-container circle, title, footnote, "Browse Library". */
@Composable
private fun EmptyDownloads(onBrowse: () -> Unit) =
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(64.dp).background(Kavita.colors.accentContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KavitaIcons.Book, null, Modifier.size(28.dp), tint = Kavita.colors.accent)
        }
        Text(stringResource(R.string.downloads_empty_title), style = Kavita.type.headline, color = Kavita.colors.label)
        Text(
            stringResource(R.string.downloads_empty_body),
            style = Kavita.type.footnote,
            color = Kavita.colors.labelSecondary,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(stringResource(R.string.downloads_browse), onBrowse, Modifier.padding(top = 8.dp))
    }

@Composable
private fun Hint(text: String) =
    Text(text, Modifier.padding(vertical = 12.dp), style = Kavita.type.callout, color = Kavita.colors.labelSecondary)

/** 40×60 cover thumb; placeholder colour from the series. */
@Composable
internal fun Thumb(
    seriesId: Int,
    imageUrl: String?,
) = CoverImage(
    title = "",
    color = coverColorFor(seriesId.toLong()),
    imageUrl = imageUrl,
    shape = KavitaShapes.rowThumb,
    modifier = Modifier.size(40.dp, 60.dp),
)
