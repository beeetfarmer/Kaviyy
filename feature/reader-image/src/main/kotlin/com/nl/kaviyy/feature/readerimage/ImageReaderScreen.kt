package com.nl.kaviyy.feature.readerimage

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.designsystem.component.LocalCoverImageLoader
import com.nl.kaviyy.core.designsystem.component.ReaderWindowEffects
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.theme.ImageReaderColors
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.network.pageUrl
import kotlinx.coroutines.flow.drop

data class ImageReaderActions(
    val onClose: () -> Unit = {},
    val onPage: (Int) -> Unit = {},
    val onNextPage: () -> Unit = {},
    val onPreviousPage: () -> Unit = {},
    val onNextChapter: () -> Unit = {},
    val onPreviousChapter: () -> Unit = {},
    val onToggleChrome: () -> Unit = {},
    val onToggleDirection: () -> Unit = {},
    val onLayout: (ReaderLayout) -> Unit = {},
    /** Toggle the bookmark on a page. */
    val onBookmark: (Int) -> Unit = {},
    val onShowSettings: (Boolean) -> Unit = {},
    val onSettings: (ReaderSettings) -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun ImageReaderRoute(
    onClose: () -> Unit,
    onOpenBook: (seriesId: Int, chapterId: Int) -> Unit = { _, _ -> },
    viewModel: ImageReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Every chapter opens here first; EPUBs move on to their own reader.
    val book = state.chapter?.takeIf { it.isEpub }
    LaunchedEffect(book) { book?.let { onOpenBook(it.seriesId, it.id) } }
    ImageReaderScreen(
        state,
        ImageReaderActions(
            onClose = onClose,
            onPage = viewModel::goTo,
            onNextPage = viewModel::nextPage,
            onPreviousPage = viewModel::previousPage,
            onNextChapter = viewModel::nextChapter,
            onPreviousChapter = viewModel::previousChapter,
            onToggleChrome = viewModel::toggleChrome,
            onToggleDirection = viewModel::toggleDirection,
            onLayout = viewModel::setLayout,
            onBookmark = viewModel::toggleBookmark,
            onShowSettings = viewModel::showSettings,
            onSettings = viewModel::updateSettings,
            onRetry = viewModel::retry,
        ),
    )
}

/** Screen 06 (DESIGN.md §7): black ground, page fills the width, tap zones, chrome over the page. */
@Composable
fun ImageReaderScreen(
    state: ImageReaderUiState,
    actions: ImageReaderActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(state.settings.backgroundColor())) {
        val chapter = state.chapter
        when {
            chapter != null && !chapter.isImage -> Message(stringResource(R.string.reader_unsupported), null)
            chapter != null && state.layout == ReaderLayout.Webtoon -> WebtoonPages(chapter, state, actions)
            chapter != null -> PagedSpreads(chapter, state, actions)
            state.error != null -> Message(stringResource(R.string.reader_load_failed), actions.onRetry)
        }
        if (chapter?.isImage == true) Prefetch(state)
        ReaderWindowEffects(state.settings.keepScreenOn, state.settings.brightness, immersive = !state.chromeVisible)
        if (state.chromeVisible) ReaderChrome(state, actions)
        if (state.settingsOpen) ReaderSettingsSheet(state.settings, actions)
    }
}

/** Left/right thirds turn pages (mirrored for right-to-left), the middle toggles the chrome. */
internal class TapZones(
    private val rightToLeft: Boolean,
    private val actions: ImageReaderActions,
) {
    fun onTap(
        x: Float,
        width: Float,
    ) {
        val third = width / ZONES
        when {
            x < third -> if (rightToLeft) actions.onNextPage() else actions.onPreviousPage()
            x > width - third -> if (rightToLeft) actions.onPreviousPage() else actions.onNextPage()
            else -> actions.onToggleChrome()
        }
    }

    override fun equals(other: Any?) = other is TapZones && other.rightToLeft == rightToLeft && other.actions == actions

    override fun hashCode() = 31 * rightToLeft.hashCode() + actions.hashCode()

    private companion object {
        const val ZONES = 3
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
    Text(text, style = Kavita.type.callout, color = ImageReaderColors.secondary, textAlign = TextAlign.Center)
    onRetry?.let { SecondaryButton(stringResource(R.string.reader_retry), it) }
}
