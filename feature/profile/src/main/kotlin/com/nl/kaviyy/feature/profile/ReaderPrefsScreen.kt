package com.nl.kaviyy.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nl.kaviyy.core.designsystem.component.CircleIconButton
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedSwitchRow
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.SectionHeader
import com.nl.kaviyy.core.designsystem.component.SegmentedControl
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.ReadingProfileDto

/** Segment index → Kavita enum value. */
internal val FIT_VALUES = listOf(3, 1, 0, 2) // Automatic, FitToWidth, FitToHeight, Original
internal val LAYOUT_VALUES = listOf(1, 2, 3) // Single, Double, DoubleReversed
internal val PDF_SCROLL_VALUES = listOf(0, 1, 3) // Vertical, Horizontal, Page

/** Kavita `ReaderMode` (1 = Up/Down, not offered: this app's reader pages left/right) and `BookPageLayoutMode`. */
private const val MODE_PAGED = 0
private const val MODE_WEBTOON = 2
private const val BOOK_SCROLL = 0
private const val BOOK_ONE_COLUMN = 1
private const val BOOK_TWO_COLUMNS = 2

@Composable
fun ReaderPrefsRoute(
    onBack: () -> Unit,
    viewModel: ReaderPrefsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderPrefsScreen(state, onBack, viewModel::change)
}

/** [derived] Mirrors Kavita's reader preferences (the Default reading profile), grouped by reader. */
@Composable
fun ReaderPrefsScreen(
    state: ReaderPrefsUiState,
    onBack: () -> Unit,
    onChange: ((ReadingProfileDto) -> ReadingProfileDto) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .fillMaxSize()
        .background(Kavita.colors.background)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = KavitaSpacing.screenHorizontal),
    verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
) {
    CircleIconButton(KavitaIcons.ChevronLeft, stringResource(R.string.you_back), onBack, Modifier.padding(top = 8.dp))
    LargeTitleHeader(stringResource(R.string.prefs_title))
    val s = state.settings
    when {
        s != null -> Prefs(s, onChange)
        state.failed -> Note(R.string.prefs_offline)
        else -> Note(R.string.you_loading)
    }
    if (state.saveFailed) Note(R.string.prefs_save_failed)
    Spacer(Modifier.height(KavitaSpacing.contentBottomPadding))
}

@Composable
private fun Note(
    @StringRes text: Int,
) = Text(stringResource(text), style = Kavita.type.callout, color = Kavita.colors.labelSecondary)

/**
 * Only what this app's readers use is shown up front, and only where it applies: in Webtoon (continuous, full width)
 * direction, page layout, fit and swipe do nothing, so they're hidden. Settings only Kavita's web reader uses stay
 * editable (they're the same account profile) in a section that says so.
 */
@Composable
private fun Prefs(
    s: ReadingProfileDto,
    onChange: ((ReadingProfileDto) -> ReadingProfileDto) -> Unit,
) {
    Section(R.string.prefs_images) {
        // Kavita's Up/Down (1) pages like Paged here; picking a mode writes 0 or 2.
        val webtoon = s.readerMode == MODE_WEBTOON
        Choice(R.string.prefs_mode, listOf(R.string.prefs_paged, R.string.prefs_webtoon), if (webtoon) 1 else 0) { i ->
            onChange { it.copy(readerMode = if (i == 1) MODE_WEBTOON else MODE_PAGED) }
        }
        if (webtoon) {
            Footnote(R.string.prefs_webtoon_note)
        } else {
            PagedImagePrefs(s, onChange)
        }
        GroupedList {
            GroupedSwitchRow(stringResource(R.string.prefs_auto_close), s.autoCloseMenu, { v ->
                onChange { it.copy(autoCloseMenu = v) }
            })
        }
        Footnote(R.string.prefs_images_note)
    }
    Section(R.string.prefs_books) {
        val scroll = s.bookReaderLayoutMode == BOOK_SCROLL
        Choice(R.string.prefs_layout, listOf(R.string.prefs_scroll, R.string.prefs_paged), if (scroll) 0 else 1) { i ->
            // Keep a two-column choice made on the web when it stays paged.
            onChange {
                it.copy(
                    bookReaderLayoutMode =
                        when {
                            i == 0 -> BOOK_SCROLL
                            it.bookReaderLayoutMode == BOOK_SCROLL -> BOOK_ONE_COLUMN
                            else -> it.bookReaderLayoutMode
                        },
                )
            }
        }
        Footnote(R.string.prefs_books_note)
    }
    Section(R.string.prefs_web_only) {
        Footnote(R.string.prefs_web_only_note)
        WebOnlyPrefs(s, onChange)
    }
}

@Composable
private fun PagedImagePrefs(
    s: ReadingProfileDto,
    onChange: ((ReadingProfileDto) -> ReadingProfileDto) -> Unit,
) {
    Choice(R.string.prefs_direction, listOf(R.string.prefs_ltr, R.string.prefs_rtl), s.readingDirection) { i ->
        onChange { it.copy(readingDirection = i) }
    }
    Choice(
        R.string.prefs_page_layout,
        listOf(R.string.prefs_single, R.string.prefs_double, R.string.prefs_double_manga),
        LAYOUT_VALUES.indexOf(s.layoutMode).coerceAtLeast(0),
    ) { i -> onChange { it.copy(layoutMode = LAYOUT_VALUES[i]) } }
    Choice(
        R.string.prefs_fit,
        listOf(
            R.string.prefs_fit_auto,
            R.string.prefs_fit_width,
            R.string.prefs_fit_height,
            R.string.prefs_fit_original,
        ),
        FIT_VALUES.indexOf(s.scalingOption).coerceAtLeast(0),
    ) { i -> onChange { it.copy(scalingOption = FIT_VALUES[i]) } }
    GroupedList {
        GroupedSwitchRow(stringResource(R.string.prefs_swipe), s.swipeToPaginate, { v ->
            onChange { it.copy(swipeToPaginate = v) }
        })
    }
}

@Composable
private fun WebOnlyPrefs(
    s: ReadingProfileDto,
    onChange: ((ReadingProfileDto) -> ReadingProfileDto) -> Unit,
) {
    Choice(
        R.string.prefs_book_direction,
        listOf(R.string.prefs_ltr, R.string.prefs_rtl),
        s.bookReaderReadingDirection,
    ) { i ->
        onChange { it.copy(bookReaderReadingDirection = i) }
    }
    Choice(R.string.prefs_pdf_theme, listOf(R.string.theme_dark, R.string.theme_light), s.pdfTheme) { i ->
        onChange { it.copy(pdfTheme = i) }
    }
    Choice(
        R.string.prefs_pdf_scroll,
        listOf(R.string.prefs_vertical, R.string.prefs_horizontal, R.string.prefs_paged),
        PDF_SCROLL_VALUES.indexOf(s.pdfScrollMode).coerceAtLeast(0),
    ) { i -> onChange { it.copy(pdfScrollMode = PDF_SCROLL_VALUES[i]) } }
    GroupedList {
        GroupedSwitchRow(stringResource(R.string.prefs_two_columns), s.bookReaderLayoutMode == BOOK_TWO_COLUMNS, { v ->
            onChange { it.copy(bookReaderLayoutMode = if (v) BOOK_TWO_COLUMNS else BOOK_ONE_COLUMN) }
        }, divider = true)
        GroupedSwitchRow(stringResource(R.string.prefs_tap_to_turn), s.bookReaderTapToPaginate, { v ->
            onChange { it.copy(bookReaderTapToPaginate = v) }
        }, divider = true)
        GroupedSwitchRow(stringResource(R.string.prefs_emulate_book), s.emulateBook, { v ->
            onChange { it.copy(emulateBook = v) }
        })
    }
}

@Composable
private fun Footnote(
    @StringRes text: Int,
) = Text(
    stringResource(text),
    style = Kavita.type.footnote,
    color = Kavita.colors.labelSecondary,
    modifier = Modifier.padding(horizontal = 16.dp),
)

@Composable
private fun Section(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionHeader(stringResource(title))
    content()
}

@Composable
private fun Choice(
    @StringRes label: Int,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        stringResource(label),
        style = Kavita.type.footnote,
        color = Kavita.colors.labelSecondary,
        modifier = Modifier.padding(start = 16.dp),
    )
    SegmentedControl(options.map { stringResource(it) }, selected.coerceIn(0, options.lastIndex), onSelect)
}
