package com.nl.kaviyy.feature.readerepub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.data.Highlight
import com.nl.kaviyy.core.data.ReaderChapter
import com.nl.kaviyy.core.designsystem.theme.Literata
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication

/** The canvas example: "42% · page 112 of 268", Paper theme. */
internal val previewBook =
    EpubReaderUiState(
        chapter =
            ReaderChapter(
                201,
                9,
                3,
                2,
                "The Lantern Keeper",
                null,
                null,
                "Chapter 7 · The Ash Ledger",
                false,
                0,
                3,
            ),
        publication = Publication(Manifest(metadata = Metadata())),
        loading = false,
        progression = 0.42f,
        position = 112,
        positions = 268,
    )

/** Stand-in for the WebView navigator in previews and screenshots. */
@Composable
internal fun PreviewText(state: EpubReaderUiState) =
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(4) {
            Text(
                "The lantern never went out. Mara carried it across the burning frontier, letter after letter, " +
                    "and every message she delivered changed the road behind her.",
                style = TextStyle(fontFamily = Literata, fontSize = 18.sp, lineHeight = 30.sp),
                color = state.settings.theme.colors.foreground,
            )
        }
    }

@Composable
internal fun BookPreview(
    state: EpubReaderUiState = previewBook,
    actions: EpubReaderActions = EpubReaderActions(),
) = EpubReaderScreen(state, actions) { PreviewText(state) }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun BookPaperPreview() = BookPreview()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun BookNightPreview() = BookPreview(previewBook.copy(settings = BookSettings(theme = BookTheme.Night)))

internal val previewHighlights =
    listOf(
        Highlight("a", 0, "The lantern never went out.", 0, null),
        Highlight("b", 0, "every message she delivered changed the road behind her", 2, "The theme of the whole book."),
    )

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun BookHighlightPreview() =
    BookPreview(
        previewBook.copy(
            chromeVisible = false,
            highlights = previewHighlights,
            highlightTarget =
                HighlightTarget(
                    previewHighlights[0].text,
                    top = 420f,
                    bottom = 480f,
                    uuid = "a",
                    color = 0,
                ),
        ),
    )

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun BookHighlightNightPreview() =
    BookPreview(
        previewBook.copy(
            settings = BookSettings(theme = BookTheme.Night),
            chromeVisible = false,
            highlightTarget = HighlightTarget(previewHighlights[0].text, top = 420f, bottom = 480f),
        ),
    )
