package com.nl.kaviyy.feature.readerepub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.data.Highlight
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.ReaderWindowEffects
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.result.AppError
import kotlin.math.roundToInt

private val TOP_BUTTON = 44.dp
private const val PERCENT = 100

data class EpubReaderActions(
    val onClose: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggleChrome: () -> Unit = {},
    val onShowSettings: (Boolean) -> Unit = {},
    val onSettings: (BookSettings) -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onShowToc: (Boolean) -> Unit = {},
    val onGo: (TocEntry) -> Unit = {},
    val onGoHighlight: (Highlight) -> Unit = {},
    val onPickColor: (Int) -> Unit = {},
    val onNote: () -> Unit = {},
    val onDismissHighlight: () -> Unit = {},
    val onSaveNote: (String) -> Unit = {},
    val onCloseNote: () -> Unit = {},
)

@Composable
fun EpubReaderRoute(
    onClose: () -> Unit,
    viewModel: EpubReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val handle = remember { EpubNavigatorHandle() }
    EpubReaderScreen(
        state,
        EpubReaderActions(
            onClose = onClose,
            onRetry = viewModel::retry,
            onToggleChrome = viewModel::toggleChrome,
            onShowSettings = viewModel::showSettings,
            onSettings = viewModel::updateSettings,
            onNext = handle::next,
            onPrevious = handle::previous,
            onShowToc = viewModel::showToc,
            onGo = { entry ->
                viewModel.showToc(false)
                handle.go(entry.link)
            },
            onGoHighlight = { h ->
                viewModel.showToc(false)
                state.publication?.locatorOf(h)?.let(handle::go)
            },
            onPickColor = { color ->
                handle.clearSelection()
                viewModel.pickColor(color)
            },
            onNote = {
                handle.clearSelection()
                viewModel.editNote()
            },
            onDismissHighlight = {
                handle.clearSelection()
                viewModel.dismissHighlight()
            },
            onSaveNote = viewModel::saveNote,
            onCloseNote = viewModel::closeNote,
        ),
    ) {
        state.publication?.let { book ->
            val decorations = remember(book, state.highlights) { book.decorations(state.highlights) }
            EpubNavigator(
                book,
                state.settings,
                state.locator,
                handle,
                {
                    // A tap away from the toolbar dismisses it rather than toggling the chrome.
                    if (state.highlightTarget != null) viewModel.dismissHighlight() else viewModel.toggleChrome()
                },
                viewModel::onLocator,
                HighlightCallbacks(decorations, viewModel::onSelection, viewModel::onHighlightTapped),
                Modifier.fillMaxSize(),
            )
        }
    }
}

/** Screen 07 (DESIGN.md §7): the book in its reader theme, chrome over it, "Aa" sheet. [book] is the navigator slot. */
@Composable
fun EpubReaderScreen(
    state: EpubReaderUiState,
    actions: EpubReaderActions,
    modifier: Modifier = Modifier,
    book: @Composable () -> Unit = {},
) {
    val theme = state.settings.theme
    KavitaTheme(appearance = if (theme == BookTheme.Night) Appearance.Dark else Appearance.Light) {
        Box(modifier.fillMaxSize().background(theme.colors.background)) {
            val error = state.error
            when {
                error != null -> {
                    Message(
                        stringResource(error.message()),
                        actions.onRetry.takeIf {
                            error !=
                                AppError.Forbidden
                        },
                    )
                }

                state.publication == null -> {
                    Message(stringResource(R.string.epub_loading), null)
                }

                else -> {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        book()
                        state.highlightTarget?.let { HighlightOverlay(it, actions) }
                    }
                }
            }
            ReaderWindowEffects(
                state.settings.keepScreenOn,
                state.settings.brightness,
                immersive = !state.chromeVisible,
            )
            if (state.chromeVisible) Chrome(state, actions)
            if (state.settingsOpen) BookSettingsSheet(state.settings, actions)
            if (state.tocOpen) TocSheet(state, actions)
            state.noteEditor?.let { NoteSheet(it, actions) }
        }
    }
}

private fun AppError.message() =
    if (this ==
        AppError.Forbidden
    ) {
        R.string.epub_no_download
    } else {
        R.string.epub_load_failed
    }

@Composable
private fun BoxScope.Chrome(
    state: EpubReaderUiState,
    actions: EpubReaderActions,
) {
    val colors = state.settings.theme.colors
    Row(
        Modifier
            .align(
                Alignment.TopCenter,
            ).fillMaxWidth()
            .background(colors.chrome)
            .statusBarsPadding()
            .padding(16.dp, 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            KavitaIcons.ChevronLeft,
            stringResource(R.string.epub_close),
            actions.onClose,
            containerColor = colors.track,
            contentColor = colors.foreground,
            modifier = Modifier.size(TOP_BUTTON),
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val chapter = state.chapter
            Text(
                chapter?.seriesName.orEmpty(),
                style = Kavita.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            (state.section ?: chapter?.title)?.let {
                Text(
                    it,
                    style = Kavita.type.caption.copy(fontSize = 11.sp),
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.toc.isNotEmpty()) {
            CircleIconButton(
                KavitaIcons.ListIcon,
                stringResource(R.string.epub_toc),
                { actions.onShowToc(true) },
                containerColor = colors.track,
                contentColor = colors.foreground,
                modifier = Modifier.size(TOP_BUTTON),
            )
        }
        val label = stringResource(R.string.epub_settings)
        Box(
            Modifier
                .size(TOP_BUTTON)
                .clip(KavitaShapes.pill)
                .background(colors.track)
                .clickable(role = Role.Button) { actions.onShowSettings(true) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.epub_settings_glyph),
                Modifier.clearAndSetSemantics {},
                style = Kavita.type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = colors.foreground,
            )
        }
    }
    if (state.publication != null) {
        ProgressPill(state, actions, Modifier.align(Alignment.BottomCenter))
    }
}

/** Bottom pill 64: prev · "42% · page 112 of 268" + 4dp progress · next. */
@Composable
private fun ProgressPill(
    state: EpubReaderUiState,
    actions: EpubReaderActions,
    modifier: Modifier,
) {
    val colors = state.settings.theme.colors
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .height(64.dp)
            .clip(KavitaShapes.pill)
            .background(colors.chrome)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            KavitaIcons.ChevronLeft,
            stringResource(R.string.epub_previous),
            actions.onPrevious,
            containerColor = colors.track,
            contentColor = colors.foreground,
            modifier = Modifier.size(TOP_BUTTON),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val percent = (state.progression * PERCENT).roundToInt()
            val position = state.position
            Text(
                if (position != null && state.positions > 0) {
                    stringResource(R.string.epub_progress, percent, position, state.positions)
                } else {
                    stringResource(R.string.epub_progress_percent, percent)
                },
                style = Kavita.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.foreground,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(KavitaShapes.pill)
                    .background(colors.track),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(
                            state.progression.coerceIn(0f, 1f),
                        ).height(4.dp)
                        .clip(KavitaShapes.pill)
                        .background(Kavita.colors.accent),
                )
            }
        }
        CircleIconButton(
            KavitaIcons.ChevronRight,
            stringResource(R.string.epub_next),
            actions.onNext,
            containerColor = colors.track,
            contentColor = colors.foreground,
            modifier = Modifier.size(TOP_BUTTON),
        )
    }
}

@Composable
private fun Message(
    text: String,
    onRetry: (() -> Unit)?,
) = Column(
    Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(text, style = Kavita.type.callout, color = Kavita.colors.labelSecondary, textAlign = TextAlign.Center)
    onRetry?.let { SecondaryButton(stringResource(R.string.epub_retry), it) }
}
