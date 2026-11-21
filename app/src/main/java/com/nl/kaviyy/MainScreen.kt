package com.nl.kaviyy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nl.kaviyy.core.data.FilterScope
import com.nl.kaviyy.core.designsystem.component.FloatingTabBar
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.TabItem
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.feature.filter.FilterSheet
import com.nl.kaviyy.feature.home.HomeActions
import com.nl.kaviyy.feature.home.HomeRoute
import com.nl.kaviyy.feature.library.LibraryRoute
import com.nl.kaviyy.feature.lists.AllCollectionsRoute
import com.nl.kaviyy.feature.lists.AllReadingListsRoute
import com.nl.kaviyy.feature.lists.CollectionRoute
import com.nl.kaviyy.feature.lists.ListsRoute
import com.nl.kaviyy.feature.lists.ReadingListRoute
import com.nl.kaviyy.feature.lists.WantToReadActions
import com.nl.kaviyy.feature.lists.WantToReadRoute
import com.nl.kaviyy.feature.profile.AccountRoute
import com.nl.kaviyy.feature.profile.AllStatsRoute
import com.nl.kaviyy.feature.profile.AuthKeysRoute
import com.nl.kaviyy.feature.profile.DevicesRoute
import com.nl.kaviyy.feature.profile.ReaderPrefsRoute
import com.nl.kaviyy.feature.profile.ScrobblingRoute
import com.nl.kaviyy.feature.profile.ThemeRoute
import com.nl.kaviyy.feature.profile.YouActions
import com.nl.kaviyy.feature.profile.YouRoute
import com.nl.kaviyy.feature.readerepub.EpubReaderRoute
import com.nl.kaviyy.feature.readerimage.ImageReaderRoute
import com.nl.kaviyy.feature.search.SearchDestination
import com.nl.kaviyy.feature.search.SearchRoute
import com.nl.kaviyy.feature.series.BrowseRoute
import com.nl.kaviyy.feature.series.BrowseTarget
import com.nl.kaviyy.feature.series.SeriesRoute
import kotlinx.serialization.Serializable

private enum class Tab { Home, Library, Lists, Downloads, You }

/** Root of the tab area: whichever tab is selected. */
@Serializable
private data object TabsDestination

/** Series detail, pushed over the tabs (the tab bar stays, DESIGN.md §7 screen 04). */
@Serializable
private data class SeriesDestination(
    val seriesId: Int,
)

/** People/genre/tag page; [field] is a `FilterField` name. */
@Serializable
private data class BrowseDestination(
    val field: String,
    val id: Int,
    val name: String,
)

/** Search (screen 05): replaces the tab bar with its own bottom search bar. */
@Serializable
private data object SearchRouteDestination

/** Image reader (screen 06), full screen without the tab bar. */
@Serializable
private data class EpubDestination(
    val seriesId: Int,
    val chapterId: Int,
)

@Serializable
private data class ReaderDestination(
    val seriesId: Int,
    val chapterId: Int,
)

/** Want to Read list (screen 08's card), Library-style grid. */
@Serializable
private data object WantToReadDestination

@Serializable
private data object CollectionsDestination

@Serializable
private data object ReadingListsDestination

@Serializable
private data object AllStatsDestination

@Serializable
private data object ThemeDestination

@Serializable
private data object ReaderPrefsDestination

@Serializable
private data object DevicesDestination

@Serializable
private data object ScrobblingDestination

@Serializable
private data object AccountDestination

@Serializable
private data object AuthKeysDestination

@Serializable
private data class ReadingListDestination(
    val readingListId: Int,
)

@Serializable
private data class CollectionDestination(
    val collectionId: Int,
)

private const val NO_LIBRARY = -1

/** [filterFor] value: the filter sheet is open for the Want to Read list. */
private const val WANT_TO_READ_FILTER = -2

/** Signed-in shell: tab content + FloatingTabBar (DESIGN.md §6; Downloads is the derived fifth tab). */
@Composable
internal fun MainScreen(
    onServerPill: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteLocalData: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(Tab.Home.ordinal) }
    // Library id the filter sheet is open for (or WANT_TO_READ_FILTER); null = closed.
    var filterFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val tabs =
        listOf(
            TabItem(stringResource(R.string.tab_home), KavitaIcons.Home),
            TabItem(stringResource(R.string.tab_library), KavitaIcons.Library),
            TabItem(stringResource(R.string.tab_lists), KavitaIcons.Bookmark, KavitaIcons.BookmarkFilled),
            TabItem(stringResource(R.string.tab_downloads), KavitaIcons.Download),
            TabItem(stringResource(R.string.tab_you), KavitaIcons.User),
        )
    val nav = rememberNavController()
    val openSeries: (Int) -> Unit = { nav.navigate(SeriesDestination(it)) }
    val browse: (BrowseTarget) -> Unit = { nav.navigate(BrowseDestination(it.field.name, it.id, it.name)) }
    Box(Modifier.fillMaxSize().background(Kavita.colors.background)) {
        NavHost(nav, startDestination = TabsDestination) {
            composable<TabsDestination> {
                TabContent(
                    Tab.entries[selected],
                    tabs[selected].label,
                    openSeries,
                    onSelect = {
                        selected =
                            it.ordinal
                    },
                    onServerPill,
                    onDeleteLocalData,
                    TabNav(
                        onOpenWantToRead = { nav.navigate(WantToReadDestination) },
                        onOpenCollection = { nav.navigate(CollectionDestination(it)) },
                        onSeeAllCollections = { nav.navigate(CollectionsDestination) },
                        onOpenReadingList = { nav.navigate(ReadingListDestination(it)) },
                        onSeeAllReadingLists = { nav.navigate(ReadingListsDestination) },
                        onAllStats = { nav.navigate(AllStatsDestination) },
                        onTheme = { nav.navigate(ThemeDestination) },
                        onReaderPrefs = { nav.navigate(ReaderPrefsDestination) },
                        onDevices = { nav.navigate(DevicesDestination) },
                        onScrobbling = { nav.navigate(ScrobblingDestination) },
                        onAccount = { nav.navigate(AccountDestination) },
                    ),
                ) { filterFor = it }
            }
            composable<SeriesDestination> {
                SeriesRoute(
                    onBack = { nav.popBackStack() },
                    onRead = {
                        seriesId,
                        chapterId,
                        ->
                        chapterId?.let { nav.navigate(ReaderDestination(seriesId, it)) }
                    },
                    onOpenSeries = openSeries,
                    onBrowse = browse,
                )
            }
            composable<SearchRouteDestination> {
                SearchRoute(onHome = { nav.popBackStack(TabsDestination, inclusive = false) }, onOpen = { d ->
                    when (d) {
                        is SearchDestination.Series -> openSeries(d.id)
                        is SearchDestination.Browse -> browse(BrowseTarget(d.field, d.id, d.name))
                    }
                })
            }
            composable<ReaderDestination> { entry ->
                ImageReaderRoute(
                    onClose = { nav.popBackStack() },
                    onOpenBook = { seriesId, chapterId ->
                        nav.navigate(EpubDestination(seriesId, chapterId)) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    },
                )
            }
            composable<EpubDestination> { EpubReaderRoute(onClose = { nav.popBackStack() }) }
            composable<BrowseDestination> { BrowseRoute(onBack = { nav.popBackStack() }, onOpenSeries = openSeries) }
            composable<AllStatsDestination> { AllStatsRoute(onBack = { nav.popBackStack() }) }
            composable<ThemeDestination> { ThemeRoute(onBack = { nav.popBackStack() }) }
            composable<ReaderPrefsDestination> { ReaderPrefsRoute(onBack = { nav.popBackStack() }) }
            composable<DevicesDestination> { DevicesRoute(onBack = { nav.popBackStack() }) }
            composable<AccountDestination> {
                AccountRoute(
                    onBack = { nav.popBackStack() },
                    onAuthKeys = { nav.navigate(AuthKeysDestination) },
                    onSignOut = onSignOut,
                )
            }
            composable<AuthKeysDestination> {
                AuthKeysRoute(onBack = { nav.popBackStack() })
            }
            composable<ScrobblingDestination> {
                ScrobblingRoute(
                    onBack = { nav.popBackStack() },
                    onOpenSeries = openSeries,
                )
            }
            composable<ReadingListsDestination> {
                AllReadingListsRoute(
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(ReadingListDestination(it)) },
                )
            }
            composable<ReadingListDestination> {
                ReadingListRoute(onBack = { nav.popBackStack() }, onRead = {
                    s,
                    c,
                    ->
                    nav.navigate(ReaderDestination(s, c))
                })
            }
            composable<CollectionsDestination> {
                AllCollectionsRoute(onBack = { nav.popBackStack() }, onOpenCollection = {
                    nav.navigate(CollectionDestination(it))
                })
            }
            composable<CollectionDestination> {
                CollectionRoute(
                    onBack = { nav.popBackStack() },
                    onOpenSeries = openSeries,
                )
            }
            composable<WantToReadDestination> {
                WantToReadRoute(
                    WantToReadActions(
                        onBack = { nav.popBackStack() },
                        onOpenSeries = openSeries,
                        onOpenFilter = { filterFor = WANT_TO_READ_FILTER },
                    ),
                )
            }
        }
        filterFor?.let { id ->
            if (id == WANT_TO_READ_FILTER) {
                FilterSheet(null, onDismiss = { filterFor = null }, listScope = FilterScope.WantToRead)
            } else {
                FilterSheet(id.takeIf { it != NO_LIBRARY }, onDismiss = { filterFor = null })
            }
        }
        val entry by nav.currentBackStackEntryAsState()
        val fullScreen =
            entry?.destination?.let {
                it.hasRoute<SearchRouteDestination>() || it.hasRoute<ReaderDestination>() ||
                    it.hasRoute<EpubDestination>()
            } ==
                true
        if (!fullScreen) {
            FloatingTabBar(
                items = tabs,
                selectedIndex = selected,
                onSelect = {
                    selected = it
                    nav.popBackStack(TabsDestination, inclusive = false)
                },
                searchLabel = stringResource(R.string.tab_search),
                onSearch = { nav.navigate(SearchRouteDestination) { launchSingleTop = true } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private data class TabNav(
    val onOpenWantToRead: () -> Unit,
    val onOpenCollection: (Int) -> Unit,
    val onSeeAllCollections: () -> Unit,
    val onOpenReadingList: (Int) -> Unit,
    val onSeeAllReadingLists: () -> Unit,
    val onAllStats: () -> Unit,
    val onTheme: () -> Unit,
    val onReaderPrefs: () -> Unit,
    val onDevices: () -> Unit,
    val onScrobbling: () -> Unit,
    val onAccount: () -> Unit,
)

@Suppress("LongParameterList")
@Composable
private fun TabContent(
    tab: Tab,
    label: String,
    onOpenSeries: (Int) -> Unit,
    onSelect: (Tab) -> Unit,
    onServerPill: () -> Unit,
    onDeleteLocalData: () -> Unit,
    lists: TabNav,
    onOpenFilter: (Int) -> Unit,
) {
    when (tab) {
        Tab.Home -> {
            HomeRoute(
                HomeActions(
                    onServerPill = onServerPill,
                    onProfile = { onSelect(Tab.You) },
                    onSeeAll = { onSelect(Tab.Library) },
                    onSeeDownloads = { onSelect(Tab.Downloads) },
                    onOpenSeries = onOpenSeries,
                ),
            )
        }

        Tab.Library -> {
            LibraryRoute(onOpenSeries = onOpenSeries, onOpenFilter = { onOpenFilter(it ?: NO_LIBRARY) })
        }

        Tab.Lists -> {
            ListsRoute(
                lists.onOpenWantToRead,
                lists.onOpenCollection,
                lists.onSeeAllCollections,
                lists.onOpenReadingList,
                lists.onSeeAllReadingLists,
            )
        }

        Tab.You -> {
            YouRoute(
                YouActions(
                    onAllStats = lists.onAllStats,
                    onTheme = lists.onTheme,
                    onReaderPrefs = lists.onReaderPrefs,
                    onServers = onServerPill,
                    onDevices = lists.onDevices,
                    onScrobbling = lists.onScrobbling,
                    onAccount = lists.onAccount,
                    onDeleteLocalData = onDeleteLocalData,
                ),
            )
        }

        else -> {
            ComingSoon(label)
        }
    }
}

/** Tabs whose milestone hasn't landed yet. */
@Composable
private fun ComingSoon(title: String) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = KavitaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
    ) {
        LargeTitleHeader(title, Modifier.padding(top = KavitaSpacing.topBelowStatusBar))
        Text(stringResource(R.string.coming_soon), style = Kavita.type.callout, color = Kavita.colors.labelSecondary)
    }
}
