package com.nl.kaviyy.feature.series

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.ReadTarget
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.CoverImage
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.StatusChip
import com.nl.kaviyy.core.designsystem.component.StatusChipStyle
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.seriesCoverUrl

private val HERO_HEIGHT = 236.dp
private val COVER_TOP = 104.dp
private const val SUMMARY_LINES = 4
private const val HERO_BUTTON_ALPHA = 0.22f
private const val COVER_RING_ALPHA = 0.15f

data class SeriesActions(
    val onBack: () -> Unit = {},
    val onRead: (chapterId: Int?) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onTab: (SeriesTab) -> Unit = {},
    val onSheet: (SeriesSheet?) -> Unit = {},
    val onOpenSeries: (seriesId: Int) -> Unit = {},
    val onWantToRead: () -> Unit = {},
    val onMark: (ReadTarget, read: Boolean) -> Unit = { _, _ -> },
)

@Composable
fun SeriesRoute(
    onBack: () -> Unit,
    onRead: (seriesId: Int, chapterId: Int?) -> Unit,
    onOpenSeries: (seriesId: Int) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SeriesScreen(
        state,
        SeriesActions(
            onBack = onBack,
            onRead = { onRead(viewModel.seriesId, it) },
            onRetry = viewModel::refresh,
            onTab = viewModel::selectTab,
            onSheet = viewModel::showSheet,
            onOpenSeries = onOpenSeries,
            onWantToRead = viewModel::toggleWantToRead,
            onMark = viewModel::setRead,
        ),
    )
}

/** Screen 04 (DESIGN.md §7): hero, cover, title, chips, actions, summary. Tabs: M8.2. */
@Composable
fun SeriesScreen(
    state: SeriesUiState,
    actions: SeriesActions,
    modifier: Modifier = Modifier,
) {
    val series = state.detail?.series
    val heroColor = series?.heroColor() ?: Kavita.colors.fillSegmented
    Box(modifier.fillMaxSize().background(Kavita.colors.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box {
                Box(Modifier.fillMaxWidth().height(HERO_HEIGHT).background(heroColor))
                if (series != null) {
                    Body(series, state, actions, Modifier.padding(top = COVER_TOP))
                }
            }
            if (series == null) Placeholder(state, actions.onRetry)
        }
        TopBar(series, state, actions)
        val detail = state.detail
        if (state.sheet != null && detail != null) SeriesDetailSheet(state.sheet, detail, state.server, actions)
    }
}

@Composable
private fun TopBar(
    series: SeriesEntity?,
    state: SeriesUiState,
    actions: SeriesActions,
) {
    val context = LocalContext.current
    val chooser = stringResource(R.string.series_share_chooser)
    val translucent = Color.White.copy(alpha = HERO_BUTTON_ALPHA)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(
            KavitaIcons.ChevronLeft,
            stringResource(R.string.series_back),
            actions.onBack,
            containerColor = translucent,
            contentColor = Color.White,
        )
        Spacer(Modifier.weight(1f))
        val server = state.server
        if (series != null && server != null) {
            // Kavita's web page for the series, so the link opens for anyone with access to this server.
            val link = server.url.resolve("library/${series.libraryId}/series/${series.id}")
            val share = {
                val send =
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, link)
                        .putExtra(Intent.EXTRA_SUBJECT, series.name)
                context.startActivity(Intent.createChooser(send, chooser))
            }
            CircleIconButton(
                KavitaIcons.Share,
                stringResource(R.string.series_share),
                share,
                containerColor = translucent,
                contentColor = Color.White,
            )
            CircleIconButton(
                KavitaIcons.More,
                stringResource(R.string.series_more_actions),
                { actions.onSheet(SeriesSheet.Actions) },
                containerColor = translucent,
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun Body(
    series: SeriesEntity,
    state: SeriesUiState,
    actions: SeriesActions,
    modifier: Modifier = Modifier,
) {
    val meta = state.detail?.metadata
    Column(
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = KavitaSpacing.screenHorizontal,
            ).padding(bottom = KavitaSpacing.contentBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverImage(
            title = series.name,
            color = coverColorFor(series.id.toLong()),
            imageUrl = state.server?.seriesCoverUrl(series.id),
            shape = KavitaShapes.coverLarge,
            titleSize = Kavita.type.seriesTitle.fontSize * 0.85f,
            modifier =
                Modifier
                    .size(136.dp, 204.dp)
                    .shadow(24.dp, KavitaShapes.coverLarge)
                    .border(1.dp, Color.White.copy(alpha = COVER_RING_ALPHA), KavitaShapes.coverLarge),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                series.name,
                style = Kavita.type.seriesTitle,
                color = Kavita.colors.label,
                textAlign = TextAlign.Center,
            )
            val byline =
                listOfNotNull(
                    meta
                        ?.writers
                        ?.take(2)
                        ?.joinToString { it.name }
                        ?.ifEmpty { null },
                    state.libraryName,
                )
            if (byline.isNotEmpty()) {
                Text(
                    byline.joinToString(" · "),
                    style = Kavita.type.callout,
                    color = Kavita.colors.labelSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Chips(series, state)
        ActionRow(series, state, actions)
        if (state.syncPending) {
            Text(
                stringResource(R.string.series_sync_pending),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        } else if (state.error != null) {
            Text(
                stringResource(R.string.series_refresh_failed),
                style = Kavita.type.footnote,
                color = Kavita.colors.labelSecondary,
            )
        }
        Summary(plainSummary(meta?.summary))
        state.detail?.let { SeriesTabs(it, state.server, state.tab, state.related, actions) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(
    series: SeriesEntity,
    state: SeriesUiState,
) {
    val meta = state.detail?.metadata ?: return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusChip(stringResource(publicationStatusLabel(meta.publicationStatus)), style = StatusChipStyle.Accent)
        if (meta.releaseYear > 0) StatusChip(meta.releaseYear.toString())
        ageRatingLabel(meta.ageRating)?.let { StatusChip(stringResource(it)) }
        if (series.userRating > 0f) {
            StatusChip(
                stringResource(R.string.series_rating, series.userRating),
                leadingIcon = KavitaIcons.Star,
                leadingIconTint = Kavita.colors.rating,
            )
        }
        if (series.pagesRead < series.pages || series.pages == 0) {
            when (val h = hoursLeft(meta.hoursLeft)) {
                null -> Unit
                0 -> StatusChip(stringResource(R.string.series_under_hour_left))
                else -> StatusChip(stringResource(R.string.series_hours_left, h))
            }
        }
    }
}

@Composable
private fun ActionRow(
    series: SeriesEntity,
    state: SeriesUiState,
    actions: SeriesActions,
) {
    val onRead = actions.onRead
    val next = state.continueChapter
    val label =
        when (val a = readAction(series, next)) {
            ReadAction.Start -> {
                stringResource(R.string.series_start)
            }

            ReadAction.ReadAgain -> {
                stringResource(R.string.series_read_again)
            }

            is ReadAction.Continue -> {
                when {
                    a.chapter != null -> stringResource(R.string.series_continue_chapter, a.chapter)
                    a.volume != null -> stringResource(R.string.series_continue_volume, a.volume)
                    else -> stringResource(R.string.series_continue)
                }
            }
        }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryButton(label, { onRead(next?.id) }, Modifier.weight(1f), leadingIcon = KavitaIcons.Play)
        // Toggle: M8.3. Download: M15 (behind the Download role).
        WantToRead(state.detail?.metadata?.wantToRead == true, actions.onWantToRead)
    }
}

/** 50dp round toggle: filled bookmark on accentContainer when on. */
@Composable
private fun WantToRead(
    on: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(if (on) R.string.series_want_to_read_remove else R.string.series_want_to_read_add)
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (on) Kavita.colors.accentContainer else Kavita.colors.surface)
            .border(0.5.dp, Kavita.colors.hairline, CircleShape)
            .toggleable(on, role = Role.Switch) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) KavitaIcons.BookmarkFilled else KavitaIcons.Bookmark,
            label,
            Modifier.size(20.dp),
            tint = Kavita.colors.accent,
        )
    }
}

@Composable
private fun Summary(text: String) {
    if (text.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text,
            style = Kavita.type.callout,
            color = Kavita.colors.labelBody,
            maxLines = if (expanded) Int.MAX_VALUE else SUMMARY_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                stringResource(if (expanded) R.string.series_less else R.string.series_more),
                style = Kavita.type.callout.copy(fontWeight = Kavita.type.bodyStrong.fontWeight),
                color = Kavita.colors.accent,
                modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Placeholder(
    state: SeriesUiState,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(KavitaSpacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.refreshing) {
            Text(
                stringResource(R.string.series_loading),
                style = Kavita.type.callout,
                color = Kavita.colors.labelSecondary,
            )
        } else {
            Text(
                stringResource(R.string.series_load_failed),
                style = Kavita.type.callout,
                color = Kavita.colors.labelSecondary,
            )
            PrimaryButton(stringResource(R.string.series_retry), onRetry)
        }
    }
}

/** Server cover colour when Kavita extracted one, else the placeholder palette. */
private fun SeriesEntity.heroColor(): Color =
    primaryColor?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: coverColorFor(id.toLong())
