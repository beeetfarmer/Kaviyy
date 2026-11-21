package com.nl.kaviyy.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.data.Home
import com.nl.kaviyy.core.data.HomeItem
import com.nl.kaviyy.core.data.HomeRail
import com.nl.kaviyy.core.data.HomeSection
import com.nl.kaviyy.core.data.StreamType
import com.nl.kaviyy.core.designsystem.component.CoverCard
import com.nl.kaviyy.core.designsystem.component.RailHeader
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.collectionCoverUrl
import com.nl.kaviyy.core.network.libraryCoverUrl
import com.nl.kaviyy.core.network.readingListCoverUrl

/** Local rail ids for sections that aren't server streams (server ids are positive, built-ins -1…-3). */
internal const val WANT_TO_READ_RAIL = Int.MIN_VALUE + 1

/** A collection, reading list or library on a Home rail. */
internal data class Shelf(
    val id: Int,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
)

/** [derived] One user-chosen Home section (You → Home Screen); empty sections take no space. */
internal fun LazyListScope.homeSection(
    section: HomeSection,
    state: HomeUiState,
    actions: HomeActions,
    coverUrl: (Int) -> String?,
    isDownloaded: (Int) -> Boolean,
) {
    val home = state.home ?: return
    when (section) {
        HomeSection.ContinueReading -> {
            continueCard(home.continueReading, state, actions, coverUrl)
        }

        HomeSection.Collections -> {
            collections(state, actions)
        }

        HomeSection.ReadingLists -> {
            readingLists(state, actions)
        }

        HomeSection.Libraries -> {
            libraries(state, actions)
        }

        else -> {
            seriesRails(section, home, actions).filter { it.first.items.isNotEmpty() }.forEach { (rail, a) ->
                item(key = "rail-${rail.streamId}") { Rail(rail, coverUrl, a, state::isDimmed, isDownloaded) }
            }
        }
    }
}

/** The series rails of [section], each with the actions its "See All" uses. */
private fun seriesRails(
    section: HomeSection,
    home: Home,
    actions: HomeActions,
): List<Pair<HomeRail, HomeActions>> =
    when (section) {
        HomeSection.OnDeck -> listOfNotNull(home.rail(StreamType.ON_DECK))
        HomeSection.RecentlyUpdated -> listOfNotNull(home.rail(StreamType.RECENTLY_UPDATED))
        HomeSection.RecentlyAdded -> listOfNotNull(home.rail(StreamType.NEWLY_ADDED))
        HomeSection.SmartFilters -> home.rails.filter { it.type == StreamType.SMART_FILTER }
        else -> emptyList()
    }.map { it to actions } +
        when (section) {
            HomeSection.WantToRead -> {
                listOf(
                    HomeRail(WANT_TO_READ_RAIL, 0, "", home.wantToRead) to
                        actions.copy(onSeeAll = { actions.onSeeWantToRead() }),
                )
            }

            HomeSection.Downloaded -> {
                listOf(
                    HomeRail(DOWNLOADED_RAIL, 0, "", home.downloaded) to
                        actions.copy(onSeeAll = { actions.onSeeDownloads() }),
                )
            }

            else -> {
                emptyList()
            }
        }

private fun LazyListScope.continueCard(
    item: HomeItem?,
    state: HomeUiState,
    actions: HomeActions,
    coverUrl: (Int) -> String?,
) {
    if (item == null) return
    item(key = "continue") {
        val modifier =
            Modifier
                .padding(horizontal = KavitaSpacing.screenHorizontal)
                .alpha(if (state.isDimmed(item.seriesId)) DIMMED else 1f)
        ContinueCard(item, coverUrl(item.seriesId), actions, modifier)
    }
}

private fun LazyListScope.collections(
    state: HomeUiState,
    actions: HomeActions,
) {
    val list = state.collections
    if (list.isEmpty()) return
    item(key = "collections") {
        ShelfRail(
            stringResource(R.string.home_collections),
            list.map {
                Shelf(
                    it.id,
                    it.title,
                    pluralStringResource(R.plurals.home_series_count, it.itemCount, it.itemCount),
                    state.server?.collectionCoverUrl(it.id),
                )
            },
            actions.onOpenCollection,
            actions.onSeeAllCollections,
        )
    }
}

private fun LazyListScope.readingLists(
    state: HomeUiState,
    actions: HomeActions,
) {
    val list = state.readingLists
    if (list.isEmpty()) return
    item(key = "reading-lists") {
        ShelfRail(
            stringResource(R.string.home_reading_lists),
            list.map {
                val n = it.list.itemCount
                Shelf(
                    it.list.id,
                    it.list.title,
                    pluralStringResource(R.plurals.home_items_count, n, n),
                    state.server?.readingListCoverUrl(it.list.id),
                )
            },
            actions.onOpenReadingList,
            actions.onSeeAllReadingLists,
        )
    }
}

private fun LazyListScope.libraries(
    state: HomeUiState,
    actions: HomeActions,
) {
    val list = state.libraries
    if (list.isEmpty()) return
    item(key = "libraries") {
        ShelfRail(
            stringResource(R.string.home_libraries),
            list.map {
                Shelf(
                    it.id,
                    it.name,
                    stringResource(libraryType(it.type)),
                    state.server?.libraryCoverUrl(it.id),
                )
            },
            actions.onOpenLibrary,
            onSeeAll = null,
        )
    }
}

/** Kavita `LibraryType` (0 Manga, 1 Comic, 2 Book, 3 Image, 4 LightNovel, 5 ComicVine). */
@Suppress("MagicNumber")
internal fun libraryType(type: Int) =
    when (type) {
        0 -> R.string.home_library_manga
        1, 5 -> R.string.home_library_comic
        2 -> R.string.home_library_book
        3 -> R.string.home_library_image
        4 -> R.string.home_library_light_novel
        else -> R.string.home_library_other
    }

@Composable
private fun ShelfRail(
    title: String,
    shelves: List<Shelf>,
    onOpen: (Int) -> Unit,
    onSeeAll: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RailHeader(
            title,
            Modifier.padding(horizontal = KavitaSpacing.screenHorizontal),
            actionLabel = onSeeAll?.let { stringResource(R.string.home_see_all) },
            onAction = { onSeeAll?.invoke() },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = KavitaSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(KavitaSpacing.railGap),
        ) {
            items(shelves, key = { it.id }) { shelf ->
                CoverCard(
                    title = shelf.title,
                    subtitle = shelf.subtitle,
                    color = coverColorFor(shelf.id.toLong() * 31 + title.length),
                    onClick = { onOpen(shelf.id) },
                    imageUrl = shelf.imageUrl,
                )
            }
        }
    }
}
