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
import com.nl.kaviyy.core.data.HomeSectionPref
import com.nl.kaviyy.core.data.StreamType
import com.nl.kaviyy.core.designsystem.component.CoverCard
import com.nl.kaviyy.core.designsystem.component.RailHeader
import com.nl.kaviyy.core.designsystem.component.coverColorFor
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.network.readingListCoverUrl

/** Local rail ids for sections that aren't server streams (server ids are positive, built-ins -1…-3). */
internal const val WANT_TO_READ_RAIL = Int.MIN_VALUE + 1

/** A reading list on its Home rail. */
internal data class Shelf(
    val id: Int,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
)

/** [derived] One user-chosen Home section (You → Home Screen); empty sections take no space. */
internal fun LazyListScope.homeSection(
    pref: HomeSectionPref,
    state: HomeUiState,
    actions: HomeActions,
    coverUrl: (Int) -> String?,
    isDownloaded: (Int) -> Boolean,
) {
    val home = state.home ?: return
    when (pref.section) {
        HomeSection.ContinueReading -> {
            continueCard(home.continueReading, state, actions, coverUrl)
        }

        HomeSection.ReadingLists -> {
            readingLists(state, actions)
        }

        else -> {
            seriesRails(pref, home, actions).filter { it.first.items.isNotEmpty() }.forEach { (rail, a) ->
                item(key = "rail-${rail.streamId}") { Rail(rail, coverUrl, a, state::isDimmed, isDownloaded) }
            }
        }
    }
}

/** The series rails of [section], each with the actions its "See All" uses. */
private fun seriesRails(
    pref: HomeSectionPref,
    home: Home,
    actions: HomeActions,
): List<Pair<HomeRail, HomeActions>> =
    when (pref.section) {
        HomeSection.OnDeck -> listOfNotNull(home.rail(StreamType.ON_DECK))
        HomeSection.RecentlyUpdated -> listOfNotNull(home.rail(StreamType.RECENTLY_UPDATED))
        HomeSection.RecentlyAdded -> listOfNotNull(home.rail(StreamType.NEWLY_ADDED))
        HomeSection.SmartFilters -> home.rails.filter { it.type == StreamType.SMART_FILTER }
        else -> emptyList()
    }.map { it to actions } +
        when (pref.section) {
            HomeSection.Collection -> {
                listOfNotNull(home.itemRail(pref)?.to(actions.copy(onSeeAll = { actions.onOpenCollection(pref.id) })))
            }

            HomeSection.Library -> {
                listOfNotNull(home.itemRail(pref)?.to(actions.copy(onSeeAll = { actions.onOpenLibrary(pref.id) })))
            }

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
