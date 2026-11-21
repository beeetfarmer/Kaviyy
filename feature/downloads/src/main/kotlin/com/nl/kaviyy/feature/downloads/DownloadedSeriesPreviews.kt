package com.nl.kaviyy.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.download.DownloadState

@Composable
internal fun DownloadedSeriesPreview(
    appearance: Appearance,
    selecting: Boolean = false,
) = KavitaTheme(appearance = appearance) {
    val chapters = previewItems.filter { it.row.seriesId == 11 && it.state == DownloadState.COMPLETED }
    DownloadedSeriesScreen(
        DownloadedSeriesUiState(
            previewServer,
            "Ember Road",
            chapters.sortedBy { it.chapter?.sortOrder },
            selecting = selecting,
            selected = if (selecting) setOf(1) else emptySet(),
            loading = false,
        ),
        DownloadedSeriesActions(),
    )
}

@Preview(name = "Series downloads · light")
@Composable
private fun SeriesLight() = DownloadedSeriesPreview(Appearance.Light)

@Preview(name = "Series downloads · dark")
@Composable
private fun SeriesDark() = DownloadedSeriesPreview(Appearance.Dark)
