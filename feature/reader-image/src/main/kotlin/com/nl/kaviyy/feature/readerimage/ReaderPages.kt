package com.nl.kaviyy.feature.readerimage

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.designsystem.component.LocalCoverImageLoader
import com.nl.kaviyy.core.network.pageUrl
import kotlinx.coroutines.flow.drop

private val WEBTOON_MIN_HEIGHT = 320.dp

/** Single and double page modes: a horizontal pager over spreads (one page each in single mode). */
@Composable
internal fun PagedSpreads(
    chapter: ReaderChapter,
    state: ImageReaderUiState,
    actions: ImageReaderActions,
) {
    val spreads = state.spreads
    // A new chapter or layout gets a fresh pager.
    key(chapter.id, state.layout) {
        val pager = rememberPagerState(initialPage = spreads.indexOfPage(state.page)) { spreads.size }
        val onPage by rememberUpdatedState(actions.onPage)
        val current by rememberUpdatedState(spreads)
        var zoomed by remember { mutableStateOf(false) }
        LaunchedEffect(state.page) {
            val target = spreads.indexOfPage(state.page)
            if (pager.currentPage != target) pager.scrollToPage(target)
        }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.drop(1).collect {
                current.getOrNull(it)?.let { s ->
                    onPage(s.first())
                }
            }
        }
        val zones = TapZones(state.rightToLeft, actions)
        HorizontalPager(
            pager,
            Modifier.fillMaxSize().readerActions(actions),
            reverseLayout = state.rightToLeft,
            beyondViewportPageCount = 1,
            // Pan instead of paging while a page is zoomed.
            userScrollEnabled = !zoomed && state.settings.swipeToTurn,
            key = { spreads[it].first() },
        ) { index ->
            // Pairs read in the reading direction; "Double (Manga)" swaps them.
            val pages = spreads[index].let { if (state.rightToLeft != state.reversedPairs) it.reversed() else it }
            ZoomBox(zones, onZoomed = { zoomed = it }, Modifier.fillMaxSize()) {
                if (pages.size == 1 && state.settings.scaling == SCALING_FIT_WIDTH) {
                    // Tall pages scroll vertically at full width.
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PageImage(
                            chapter,
                            pages[0],
                            state,
                            Modifier.fillMaxWidth().then(chapter.shapeOf(pages[0])),
                            Alignment.TopCenter,
                            ContentScale.FillWidth,
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pages.forEach { page ->
                            PageImage(
                                chapter,
                                page,
                                state,
                                Modifier.weight(1f).fillMaxHeight(),
                                if (pages.size ==
                                    1
                                ) {
                                    Alignment.Center
                                } else if (page ==
                                    pages.first()
                                ) {
                                    Alignment.CenterEnd
                                } else {
                                    Alignment.CenterStart
                                },
                                contentScale(state.settings.scaling),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ponytail: Original is centered and cropped at 1x (pinch to pan around); add 2D scrolling if readers ask.
private fun contentScale(scaling: Int): ContentScale =
    when (scaling) {
        SCALING_FIT_HEIGHT -> ContentScale.FillHeight
        SCALING_FIT_WIDTH -> ContentScale.FillWidth
        SCALING_ORIGINAL -> ContentScale.None
        else -> ContentScale.Fit
    }

/** Known proportions keep layouts from jumping while pages load. */
private fun ReaderChapter.shapeOf(page: Int): Modifier =
    pageSizes.getOrNull(page)?.let { Modifier.aspectRatio(it.width.toFloat() / it.height) }
        ?: Modifier.heightIn(min = WEBTOON_MIN_HEIGHT)

/** Warms the image cache with the next pages (and the next chapter's first ones near the end). */
@Composable
internal fun Prefetch(state: ImageReaderUiState) {
    val context = LocalContext.current
    val loader = LocalCoverImageLoader.current ?: return
    val server = state.server ?: return
    val targets = state.prefetch()
    LaunchedEffect(targets) {
        targets.forEach { (chapter, page) ->
            loader.enqueue(ImageRequest.Builder(context).data(server.pageUrl(chapter, page)).build())
        }
    }
}

/** Webtoon: one continuous vertical strip; any tap toggles the chrome. */
@Composable
internal fun WebtoonPages(
    chapter: ReaderChapter,
    state: ImageReaderUiState,
    actions: ImageReaderActions,
) {
    key(chapter.id) {
        val list = rememberLazyListState(initialFirstVisibleItemIndex = state.page)
        val onPage by rememberUpdatedState(actions.onPage)
        LaunchedEffect(state.page) { if (list.firstVisibleItemIndex != state.page) list.scrollToItem(state.page) }
        LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.drop(1).collect { onPage(it) } }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { actions.onToggleChrome() } }
                .readerActions(actions),
            state = list,
        ) {
            // ponytail: no pinch zoom in webtoon (fit width already); add a zoomable column if asked.
            items(chapter.pages, key = { it }) { page ->
                PageImage(
                    chapter,
                    page,
                    state,
                    Modifier.fillMaxWidth().then(chapter.shapeOf(page)),
                    Alignment.TopCenter,
                    ContentScale.FillWidth,
                )
            }
        }
    }
}

@Composable
private fun PageImage(
    chapter: ReaderChapter,
    page: Int,
    state: ImageReaderUiState,
    modifier: Modifier,
    alignment: Alignment,
    scale: ContentScale = ContentScale.Fit,
) = Box(modifier) {
    val url = state.server?.pageUrl(chapter.id, page)
    val loader = LocalCoverImageLoader.current
    if (url != null && loader != null) {
        AsyncImage(
            url,
            stringResource(R.string.reader_page_image, page + 1),
            loader,
            Modifier.fillMaxSize(),
            alignment = alignment,
            contentScale = scale,
        )
    }
}

/** Tap zones have no visible targets: expose page turns and the chrome toggle to TalkBack/Switch Access. */
@Composable
private fun Modifier.readerActions(actions: ImageReaderActions): Modifier {
    val next = stringResource(R.string.reader_next_page)
    val previous = stringResource(R.string.reader_previous_page)
    val toggle = stringResource(R.string.reader_toggle_controls)
    return semantics {
        customActions =
            listOf(
                CustomAccessibilityAction(next) { actions.onNextPage().let { true } },
                CustomAccessibilityAction(previous) { actions.onPreviousPage().let { true } },
                CustomAccessibilityAction(toggle) { actions.onToggleChrome().let { true } },
            )
    }
}
