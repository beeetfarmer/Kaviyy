package com.nl.kaviyy.feature.readerimage

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme

/** The canvas example: Ember Road, Vol. 5 · Chapter 49, page 14 of 32, right to left. */
internal val previewReader =
    ImageReaderUiState(
        chapter =
            ReaderChapter(
                id = 49,
                seriesId = 7,
                volumeId = 5,
                libraryId = 1,
                seriesName = "Ember Road",
                volumeNumber = "5",
                chapterNumber = "49",
                title = null,
                isSpecial = false,
                pages = 32,
                format = 1,
            ),
        page = 13,
        rightToLeft = true,
        loading = false,
        nextChapterId = 50,
        previousChapterId = 48,
    )

@Composable
internal fun ReaderPreview(
    state: ImageReaderUiState = previewReader,
    actions: ImageReaderActions = ImageReaderActions(),
) = KavitaTheme(appearance = Appearance.Dark) { ImageReaderScreen(state, actions) }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReaderRtlPreview() = ReaderPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReaderLtrPreview() = ReaderPreview(previewReader.copy(rightToLeft = false, nextChapterId = null))
