package com.nl.kaviyy.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.NamedRef
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesMetadata
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult

/** The canvas example (Ember Road). */
internal val previewSeries =
    SeriesUiState(
        server = Server(ServerId(1), "Home", (ServerUrl.parse("https://kavita.example") as ServerUrlResult.Valid).url),
        detail =
            SeriesDetail(
                series =
                    SeriesEntity(
                        1,
                        7,
                        1,
                        "Ember Road",
                        "ember road",
                        format = 0,
                        pages = 1000,
                        pagesRead = 620,
                        userRating = 4.6f,
                        primaryColor = "#B4441B",
                    ),
                volumes = emptyList(),
                chapters =
                    listOf(
                        ChapterEntity(
                            1,
                            49,
                            5,
                            7,
                            range = "49",
                            pages = 32,
                            pagesRead = 18,
                            format = 0,
                            volumeTitle = "5",
                        ),
                    ),
                metadata =
                    SeriesMetadata(
                        summary =
                            "<p>A courier with a lantern that never goes out carries letters across a burning " +
                                "frontier, and learns every message changes the road behind her.</p>",
                        releaseYear = 2019,
                        ageRating = 8,
                        writers = listOf(NamedRef(1, "Mira Okafor")),
                        hoursLeft = 38f,
                        wantToRead = true,
                        continueChapterId = 49,
                    ),
            ),
        libraryName = "Manga",
        refreshing = false,
    )

@Composable
internal fun SeriesPreview(
    appearance: Appearance,
    state: SeriesUiState = previewSeries,
) = KavitaTheme(appearance = appearance) { SeriesScreen(state, SeriesActions()) }

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SeriesLightPreview() = SeriesPreview(Appearance.Light)

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun SeriesDarkPreview() = SeriesPreview(Appearance.Dark)
