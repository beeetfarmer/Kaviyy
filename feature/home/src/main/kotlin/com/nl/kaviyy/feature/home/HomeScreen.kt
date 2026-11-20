package com.nl.kaviyy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.HomeItem
import com.nl.kaviyy.core.data.HomeRail
import com.nl.kaviyy.core.data.StreamType
import com.nl.kaviyy.core.data.displayTitle
import com.nl.kaviyy.core.data.minutesLeft
import com.nl.kaviyy.core.data.number
import com.nl.kaviyy.core.data.pagesLeft
import com.nl.kaviyy.core.data.progress
import com.nl.kaviyy.core.data.volumeNumber
import com.nl.kaviyy.core.designsystem.component.Avatar
import com.nl.kaviyy.core.designsystem.component.ContinueReadingCard
import com.nl.kaviyy.core.designsystem.component.CoverCard
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.RailHeader
import com.nl.kaviyy.core.designsystem.component.ServerPill
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.seriesCoverUrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Local-only rail id; server stream ids are positive, built-ins negative small. */
private const val DOWNLOADED_RAIL = Int.MIN_VALUE

/** DESIGN.md §8: non-downloaded items at 50% while offline. */
private const val DIMMED = 0.5f

@Composable
fun HomeRoute(
    actions: HomeActions,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val today = remember(locale) { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)) }
    HomeScreen(state, today, actions, viewModel::refresh, modifier)
}

/** Screen 02 (DESIGN.md §7). */
@Composable
fun HomeScreen(
    state: HomeUiState,
    today: String,
    actions: HomeActions,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverUrl: (Int) -> String? = { id -> state.server?.seriesCoverUrl(id) }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Kavita.colors.background),
        contentPadding =
            PaddingValues(top = KavitaSpacing.topBelowStatusBar, bottom = KavitaSpacing.contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapHome),
    ) {
        item(key = "header") { HomeHeader(state, today, actions) }
        if (state.downloadedRail.isNotEmpty()) {
            item(key = "downloaded") {
                val rail = HomeRail(DOWNLOADED_RAIL, 0, stringResource(R.string.home_downloaded), state.downloadedRail)
                Rail(rail, coverUrl, actions.copy(onSeeAll = { actions.onSeeDownloads() }), state::isDimmed)
            }
        }
        if (state.showStaleNotice) {
            item(key = "stale") { Notice(stringResource(R.string.home_refresh_failed)) }
        }
        state.home?.continueReading?.let { item ->
            item(key = "continue") {
                val padding =
                    Modifier
                        .padding(horizontal = KavitaSpacing.screenHorizontal)
                        .alpha(if (state.isDimmed(item.seriesId)) DIMMED else 1f)
                ContinueCard(item, coverUrl(item.seriesId), actions, padding)
            }
        }
        state.home?.rails.orEmpty().filter { it.items.isNotEmpty() }.forEach { rail ->
            item(key = "rail-${rail.streamId}") { Rail(rail, coverUrl, actions, state::isDimmed) }
        }
        if (state.showEmpty) {
            item(key = "empty") { EmptyState(failed = state.error != null, onRetry = onRetry) }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeUiState,
    today: String,
    actions: HomeActions,
) {
    LargeTitleHeader(
        title = stringResource(R.string.home_title),
        overline = today,
        modifier = Modifier.statusBarsPadding().padding(horizontal = KavitaSpacing.screenHorizontal),
    ) {
        state.server?.let {
            val label = if (state.offline) stringResource(R.string.home_offline) else it.name
            ServerPill(label, online = !state.offline && state.error == null, onClick = actions.onServerPill)
        }
        Avatar(
            initials(state.server?.username ?: state.server?.name),
            stringResource(R.string.home_profile),
            onClick = actions.onProfile,
        )
    }
}

@Composable
private fun ContinueCard(
    item: HomeItem,
    imageUrl: String?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val chapter = item.chapter ?: return
    val volume = chapter.volumeNumber()
    val number = chapter.number()
    val subtitle =
        when {
            volume != null && number != null -> stringResource(R.string.home_volume_chapter, volume, number)
            number != null -> stringResource(R.string.home_chapter, number)
            volume != null -> stringResource(R.string.home_volume, volume)
            else -> chapter.displayTitle().orEmpty()
        }
    ContinueReadingCard(
        overline = stringResource(R.string.home_continue_reading),
        title = item.title,
        subtitle = subtitle,
        coverColor = coverColorFor(item.seriesId.toLong()),
        progress = chapter.progress(),
        remaining =
            pluralStringResource(
                R.plurals.home_pages_left,
                chapter.pagesLeft(),
                chapter.pagesLeft(),
                chapter.minutesLeft(),
            ),
        resumeLabel = stringResource(R.string.home_resume),
        onResume = { actions.onResume(item) },
        coverOverline = volume?.let { stringResource(R.string.home_vol_overline, it) },
        imageUrl = imageUrl,
        modifier = modifier,
    )
}

@Composable
private fun Rail(
    rail: HomeRail,
    coverUrl: (Int) -> String?,
    actions: HomeActions,
    dimmed: (Int) -> Boolean,
) {
    val title =
        when (rail.type) {
            StreamType.ON_DECK -> stringResource(R.string.home_on_deck)
            StreamType.RECENTLY_UPDATED -> stringResource(R.string.home_recently_updated)
            StreamType.NEWLY_ADDED -> stringResource(R.string.home_recently_added)
            else -> rail.name
        }
    // Canvas gap is 12; the header's 48dp touch target already adds ~10 below the 22sp title.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RailHeader(
            title,
            Modifier.padding(horizontal = KavitaSpacing.screenHorizontal),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = { actions.onSeeAll(rail) },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = KavitaSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(KavitaSpacing.railGap),
        ) {
            items(rail.items, key = { it.seriesId }) { item ->
                val updated = rail.type == StreamType.RECENTLY_UPDATED
                CoverCard(
                    title = item.title,
                    subtitle = subtitle(rail.type, item),
                    color = coverColorFor(item.seriesId.toLong()),
                    onClick = { actions.onOpenSeries(item.seriesId) },
                    subtitleAccent = updated,
                    progress = item.progress.takeIf { rail.type == StreamType.ON_DECK },
                    unreadCount = item.newChapters.takeIf { updated },
                    imageUrl = coverUrl(item.seriesId),
                    // ponytail: no cloud-off hint on tap yet; Series (M8) opens cached data offline.
                    modifier = Modifier.alpha(if (dimmed(item.seriesId)) DIMMED else 1f),
                )
            }
        }
    }
}

@Composable
private fun subtitle(
    type: Int,
    item: HomeItem,
): String {
    val number = item.chapter?.number()
    return when {
        type == StreamType.RECENTLY_UPDATED -> {
            pluralStringResource(R.plurals.home_new_chapters, item.newChapters, item.newChapters)
        }

        type == StreamType.ON_DECK && number != null -> {
            stringResource(R.string.home_ch_progress, number, percent(item.progress))
        }

        item.pagesRead == 0 -> {
            stringResource(R.string.home_unread)
        }

        else -> {
            stringResource(R.string.home_percent, percent(item.progress))
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = Kavita.type.footnote,
        color = Kavita.colors.labelSecondary,
        modifier = Modifier.padding(horizontal = KavitaSpacing.screenHorizontal),
    )
}

@Composable
private fun EmptyState(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = KavitaSpacing.screenHorizontal, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.home_empty_title), style = Kavita.type.headline, color = Kavita.colors.label)
        Text(
            stringResource(if (failed) R.string.home_error_body else R.string.home_empty_body),
            style = Kavita.type.callout,
            color = Kavita.colors.labelSecondary,
            textAlign = TextAlign.Center,
        )
        if (failed) PrimaryButton(stringResource(R.string.home_retry), onRetry, Modifier.padding(top = 8.dp))
    }
}
