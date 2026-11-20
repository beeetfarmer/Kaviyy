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
import com.nl.kaviyy.core.designsystem.component.FloatingTabBar
import com.nl.kaviyy.core.designsystem.component.LargeTitleHeader
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SecondaryButton
import com.nl.kaviyy.core.designsystem.component.TabItem
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.feature.home.HomeActions
import com.nl.kaviyy.feature.home.HomeRoute

private enum class Tab { Home, Library, Lists, Downloads, You }

/** Signed-in shell: tab content + FloatingTabBar (DESIGN.md §6; Downloads is the derived fifth tab). */
@Composable
internal fun MainScreen(
    server: Server,
    onServerPill: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteLocalData: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(Tab.Home.ordinal) }
    val tabs =
        listOf(
            TabItem(stringResource(R.string.tab_home), KavitaIcons.Home),
            TabItem(stringResource(R.string.tab_library), KavitaIcons.Library),
            TabItem(stringResource(R.string.tab_lists), KavitaIcons.Bookmark, KavitaIcons.BookmarkFilled),
            TabItem(stringResource(R.string.tab_downloads), KavitaIcons.Download),
            TabItem(stringResource(R.string.tab_you), KavitaIcons.User),
        )
    Box(Modifier.fillMaxSize().background(Kavita.colors.background)) {
        when (Tab.entries[selected]) {
            Tab.Home -> {
                HomeRoute(
                    HomeActions(
                        onServerPill = onServerPill,
                        onProfile = { selected = Tab.You.ordinal },
                        onSeeAll = { selected = Tab.Library.ordinal },
                    ),
                )
            }

            Tab.You -> {
                YouPlaceholder(server, tabs[selected].label, onSignOut, onDeleteLocalData)
            }

            else -> {
                ComingSoon(tabs[selected].label)
            }
        }
        FloatingTabBar(
            items = tabs,
            selectedIndex = selected,
            onSelect = { selected = it },
            searchLabel = stringResource(R.string.tab_search),
            onSearch = {}, // M9
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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

/** Stand-in for You (M14): keeps Sign Out and Delete Local Data reachable. */
@Composable
private fun YouPlaceholder(
    server: Server,
    title: String,
    onSignOut: () -> Unit,
    onDeleteLocalData: () -> Unit,
) {
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = KavitaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(KavitaSpacing.sectionGapProfile),
    ) {
        LargeTitleHeader(title, Modifier.padding(top = KavitaSpacing.topBelowStatusBar))
        Text(
            stringResource(R.string.signed_in_body, server.name),
            style = Kavita.type.callout,
            color = Kavita.colors.labelSecondary,
        )
        PrimaryButton(stringResource(R.string.sign_out), onSignOut, Modifier.fillMaxWidth())
        // [derived] Final home: You › Privacy & security › Delete local data (DESIGN.md §10). Two taps.
        SecondaryButton(
            stringResource(if (confirmWipe) R.string.delete_local_data_confirm else R.string.delete_local_data),
            { if (confirmWipe) onDeleteLocalData() else confirmWipe = true },
            Modifier.fillMaxWidth(),
        )
    }
}
