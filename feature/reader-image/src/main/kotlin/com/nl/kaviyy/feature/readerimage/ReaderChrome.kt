package com.nl.kaviyy.feature.readerimage

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.ImageReaderColors
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import kotlin.math.roundToInt

internal val TOP_BUTTON = 44.dp
private val CHAPTER_BUTTON = 56.dp
private const val CHAPTER_BUTTON_ALPHA = 0.10f

@Composable
internal fun BoxScope.ReaderChrome(
    state: ImageReaderUiState,
    actions: ImageReaderActions,
) {
    TopBar(state, actions, Modifier.align(Alignment.TopCenter))
    if (state.chapter?.isImage == true && state.pages > 0) {
        BottomBar(state, actions, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopBar(
    state: ImageReaderUiState,
    actions: ImageReaderActions,
    modifier: Modifier = Modifier,
) = Row(
    modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    CircleIconButton(
        KavitaIcons.ChevronLeft,
        stringResource(R.string.reader_close),
        actions.onClose,
        containerColor = ImageReaderColors.chrome,
        contentColor = ImageReaderColors.content,
        modifier = Modifier.size(TOP_BUTTON),
    )
    val chapter = state.chapter
    if (chapter != null) {
        Column(
            Modifier
                .weight(1f)
                .height(TOP_BUTTON)
                .clip(KavitaShapes.pill)
                .background(ImageReaderColors.chrome),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                chapter.seriesName,
                style = Kavita.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = ImageReaderColors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle(chapter)?.let {
                Text(
                    it,
                    style = Kavita.type.caption.copy(fontSize = 11.sp),
                    color = ImageReaderColors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (chapter?.isImage == true && state.pages > 0) BookmarkButton(state, actions)
    CircleIconButton(
        KavitaIcons.Sliders,
        stringResource(R.string.reader_settings),
        { actions.onShowSettings(true) },
        containerColor = ImageReaderColors.chrome,
        contentColor = ImageReaderColors.content,
        modifier = Modifier.size(TOP_BUTTON),
    )
}

@Composable
private fun BottomBar(
    state: ImageReaderUiState,
    actions: ImageReaderActions,
    modifier: Modifier = Modifier,
) = Column(
    modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        LayoutGroup(state.layout, actions.onLayout)
        // Direction has no meaning for a vertical strip.
        if (state.layout != ReaderLayout.Webtoon) DirectionToggle(state.rightToLeft, actions.onToggleDirection)
    }
    Scrubber(state, actions)
}

/** Canvas radiogroup: selected = white pill with black text. */
@Composable
private fun LayoutGroup(
    selected: ReaderLayout,
    onSelect: (ReaderLayout) -> Unit,
) = Row(
    Modifier
        .height(40.dp)
        .clip(KavitaShapes.pill)
        .background(ImageReaderColors.chrome)
        .padding(3.dp)
        .selectableGroup(),
) {
    ReaderLayout.entries.forEach { layout ->
        val on = layout == selected
        Box(
            Modifier
                .fillMaxHeight()
                .clip(KavitaShapes.pill)
                .background(if (on) Color.White else Color.Transparent)
                .selectable(on, role = Role.RadioButton) { onSelect(layout) }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(layout.label()),
                style = Kavita.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = if (on) Color.Black else ImageReaderColors.content,
            )
        }
    }
}

private fun ReaderLayout.label(): Int =
    when (this) {
        ReaderLayout.Single -> R.string.reader_layout_single
        ReaderLayout.Double -> R.string.reader_layout_double
        ReaderLayout.Webtoon -> R.string.reader_layout_webtoon
    }

@Composable
private fun DirectionToggle(
    rightToLeft: Boolean,
    onToggle: () -> Unit,
) = Row(
    Modifier
        .height(40.dp)
        .clip(KavitaShapes.pill)
        .background(ImageReaderColors.chrome)
        .toggleable(rightToLeft, role = Role.Switch) { onToggle() }
        .padding(horizontal = 14.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        if (rightToLeft) KavitaIcons.ArrowRtl else KavitaIcons.ArrowLtr,
        null,
        Modifier.size(16.dp),
        tint = ImageReaderColors.content,
    )
    Text(
        stringResource(if (rightToLeft) R.string.reader_rtl else R.string.reader_ltr),
        style = Kavita.type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = ImageReaderColors.content,
    )
}

@Composable
private fun Scrubber(
    state: ImageReaderUiState,
    actions: ImageReaderActions,
) = Row(
    Modifier
        .fillMaxWidth()
        .height(72.dp)
        .clip(KavitaShapes.pill)
        .background(ImageReaderColors.chrome)
        .padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Canvas: Previous chapter on the left, Next on the right in both directions (the slider follows the pager).
    val back =
        ChapterNav(
            KavitaIcons.SkipPrevious,
            R.string.reader_previous_chapter,
            state.previousChapterId != null,
            actions.onPreviousChapter,
        )
    val forward =
        ChapterNav(
            KavitaIcons.SkipNext,
            R.string.reader_next_chapter,
            state.nextChapterId != null,
            actions.onNextChapter,
        )
    ChapterButton(back)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.reader_page_of, state.page + 1, state.pages),
                style = Kavita.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = ImageReaderColors.content,
            )
            Text(
                stringResource(R.string.reader_pages_left, state.pages - state.page - 1),
                style = Kavita.type.caption,
                color = ImageReaderColors.secondary,
            )
        }
        PageSlider(state, actions.onPage)
    }
    ChapterButton(forward)
}

private data class ChapterNav(
    val icon: ImageVector,
    val label: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ChapterButton(nav: ChapterNav) =
    CircleIconButton(
        nav.icon,
        stringResource(nav.label),
        nav.onClick,
        containerColor = Color.White.copy(alpha = CHAPTER_BUTTON_ALPHA),
        contentColor = ImageReaderColors.content,
        enabled = nav.enabled,
        modifier = Modifier.size(CHAPTER_BUTTON),
    )

/** 6dp track, accent fill, 20dp white thumb (canvas). Tap or drag; the page commits when the finger lifts. */
@Composable
private fun PageSlider(
    state: ImageReaderUiState,
    onPage: (Int) -> Unit,
) {
    val last = (state.pages - 1).coerceAtLeast(1)
    var dragging by remember { mutableFloatStateOf(-1f) }
    val fraction = (if (dragging >= 0f) dragging else state.page / last.toFloat()).coerceIn(0f, 1f)
    val label = stringResource(R.string.reader_page_slider)
    val rtl = state.rightToLeft && state.layout != ReaderLayout.Webtoon

    fun fractionAt(
        x: Float,
        width: Int,
    ) = (x / width).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }

    fun commit(f: Float) = onPage((f * last).roundToInt())
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(THUMB)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(state.page.toFloat(), 0f..last.toFloat(), last - 1)
                setProgress { onPage(it.roundToInt()).let { true } }
            }.pointerInput(rtl, last) { detectTapGestures { commit(fractionAt(it.x, size.width)) } }
            .pointerInput(rtl, last) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = fractionAt(it.x, size.width) },
                    onDragEnd = {
                        commit(dragging)
                        dragging = -1f
                    },
                    onDragCancel = { dragging = -1f },
                ) { change, _ -> dragging = fractionAt(change.position.x, size.width) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - THUMB
        val offset = travel * (if (rtl) 1f - fraction else fraction)
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(KavitaShapes.pill)
                .background(ImageReaderColors.track),
        ) {
            Box(
                Modifier
                    .align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(THUMB / 2 + travel * fraction)
                    .height(6.dp)
                    .clip(KavitaShapes.pill)
                    .background(ImageReaderColors.accent),
            )
        }
        Box(
            Modifier
                .offset(
                    x = offset,
                ).size(THUMB)
                .shadow(3.dp, KavitaShapes.pill)
                .background(Color.White, KavitaShapes.pill),
        )
    }
}

private val THUMB = 20.dp

/** "Vol. 5 · Chapter 49"; specials show their title. */
@Composable
internal fun subtitle(chapter: ReaderChapter): String? {
    if (chapter.isSpecial) return chapter.title ?: stringResource(R.string.reader_special)
    val volume = chapter.volumeNumber?.let { stringResource(R.string.reader_volume, it) }
    val number = chapter.chapterNumber?.let { stringResource(R.string.reader_chapter, it) }
    return when {
        volume != null && number != null -> stringResource(R.string.reader_dot, volume, number)
        else -> volume ?: number ?: chapter.title
    }
}
