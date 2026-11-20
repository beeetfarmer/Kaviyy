package com.nl.kaviyy.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.CommunityRating
import com.nl.kaviyy.core.data.NamedRef
import com.nl.kaviyy.core.data.Review
import com.nl.kaviyy.core.data.SeriesDetail
import com.nl.kaviyy.core.data.SeriesMetadata
import com.nl.kaviyy.core.data.SeriesReviews
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.SeriesEntity
import com.nl.kaviyy.core.database.VolumeEntity
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
                volumes =
                    listOf(
                        VolumeEntity(1, 5, 7, "5", 5f, 5f, pages = 212, pagesRead = 131),
                        VolumeEntity(1, 4, 7, "4", 4f, 4f, pages = 198, pagesRead = 198),
                        VolumeEntity(1, 6, 7, "6", 6f, 6f, pages = 220, pagesRead = 0),
                    ),
                chapters =
                    listOf(
                        ChapterEntity(
                            1,
                            48,
                            5,
                            7,
                            range = "48",
                            titleName = "Crossing",
                            pages = 34,
                            pagesRead = 34,
                            format = 0,
                        ),
                        ChapterEntity(
                            1,
                            49,
                            5,
                            7,
                            range = "49",
                            titleName = "The Ash Ledger",
                            pages = 32,
                            pagesRead = 18,
                            format = 0,
                            volumeTitle = "5",
                        ),
                        ChapterEntity(
                            1,
                            50,
                            5,
                            7,
                            range = "50",
                            titleName = "Smoke Signals",
                            pages = 30,
                            pagesRead = 0,
                            format = 0,
                        ),
                        ChapterEntity(
                            1,
                            90,
                            6,
                            7,
                            range = "Artbook: Roads",
                            pages = 64,
                            pagesRead = 0,
                            isSpecial = true,
                            format = 0,
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

internal val previewReviews =
    previewSeries.copy(
        sheet = SeriesSheet.Reviews,
        detail =
            previewSeries.detail?.copy(
                series = previewSeries.detail.series?.copy(userRating = 5f),
                metadata = previewSeries.detail.metadata?.copy(webLinks = listOf("https://anilist.co/manga/1")),
            ),
        reviews =
            ReviewsState.Loaded(
                SeriesReviews(
                    reviews =
                        listOf(
                            Review(
                                "Tomas",
                                "A slow burn",
                                "The lantern chapters are the best thing in the genre.",
                                4.5f,
                                0,
                                0,
                                null,
                                mine = false,
                            ),
                        ),
                    ratings = listOf(CommunityRating(1, 82, null)),
                ),
            ),
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

/** Tall preview so the Volumes list is visible below the summary. */
@Preview(widthDp = 412, heightDp = 1400)
@Composable
private fun SeriesVolumesPreview() = SeriesPreview(Appearance.Light)
