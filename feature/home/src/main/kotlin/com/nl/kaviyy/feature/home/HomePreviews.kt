package com.nl.kaviyy.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nl.kaviyy.core.data.Home
import com.nl.kaviyy.core.data.HomeItem
import com.nl.kaviyy.core.data.HomeRail
import com.nl.kaviyy.core.data.StreamType
import com.nl.kaviyy.core.database.ChapterEntity
import com.nl.kaviyy.core.database.CollectionEntity
import com.nl.kaviyy.core.database.LibraryEntity
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult

private val previewServer =
    Server(
        ServerId(1),
        "Home Server",
        (ServerUrl.parse("https://kavita.home.lan") as ServerUrlResult.Valid).url,
        username = "Aarav Mehta",
    )

private fun chapter(
    id: Int,
    seriesId: Int,
    range: String,
    read: Int,
) = ChapterEntity(1, id, 1, seriesId, range = range, pages = 20, pagesRead = read, format = 0, volumeTitle = "5")

internal val previewHome =
    Home(
        continueReading = HomeItem(1, 1, "Ember Road", 200, 124, chapter = chapter(49, 1, "49", 6)),
        rails =
            listOf(
                HomeRail(
                    -1,
                    StreamType.ON_DECK,
                    "",
                    listOf(
                        HomeItem(1, 1, "Ember Road", 200, 124, chapter = chapter(49, 1, "49", 6)),
                        HomeItem(2, 1, "Salt & Static", 100, 30, chapter = chapter(12, 2, "12", 2)),
                        HomeItem(3, 1, "The Quiet Orchard", 80, 60),
                    ),
                ),
                HomeRail(
                    -2,
                    StreamType.RECENTLY_UPDATED,
                    "",
                    listOf(
                        HomeItem(4, 1, "Paper Lanterns", 0, 0, newChapters = 3),
                        HomeItem(5, 1, "North Wind", 0, 0, newChapters = 1),
                        HomeItem(6, 1, "Glass Harbor", 0, 0, newChapters = 12),
                    ),
                ),
            ),
        collections =
            listOf(
                CollectionEntity(1, 1, "Weekend Reads", itemCount = 12, position = 0),
                CollectionEntity(1, 2, "Award Winners", itemCount = 5, position = 1),
            ),
        libraries = listOf(LibraryEntity(1, 1, "Manga", 0), LibraryEntity(1, 2, "Books", 2)),
    )

internal fun previewState(home: Home? = previewHome) = HomeUiState(server = previewServer, home = home)

/** Offline: two downloaded series; Ember Road (continue card, On Deck) is downloaded, the rest dimmed. */
internal val previewOffline =
    previewState(
        previewHome.copy(
            downloaded = listOf(HomeItem(1, 1, "Ember Road", 200, 124), HomeItem(7, 1, "Moss & Iron", 40, 0)),
        ),
    ).copy(offline = true)

@Composable
internal fun HomePreview(
    appearance: Appearance,
    state: HomeUiState = previewState(),
) = KavitaTheme(appearance = appearance) {
    HomeScreen(state, "Friday, 20 November", HomeActions(), onRetry = {})
}

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun HomeLightPreview() = HomePreview(Appearance.Light)

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun HomeDarkPreview() = HomePreview(Appearance.Dark)

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun HomeOfflineLightPreview() = HomePreview(Appearance.Light, previewOffline)

@Preview(heightDp = 915, widthDp = 412)
@Composable
private fun HomeOfflineDarkPreview() = HomePreview(Appearance.Dark, previewOffline)
