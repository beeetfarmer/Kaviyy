package com.nl.kaviyy.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.designsystem.theme.fixedScale

private const val TAB_ANIM_MS = 220

/**
 * Floating pill tab bar + separate round Search button, 16 from the sides and 28 above the nav bar.
 * Deviation: no backdrop blur (CSS `backdrop-filter` has no Compose equivalent); the 86% fill stays.
 */
@Composable
fun FloatingTabBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    searchLabel: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = glass()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Width left for the five cells: the bar minus side insets, the gap and Search, minus the bar's own padding.
        val barWidth = maxWidth - KavitaSpacing.tabBarSideInset * 2 - 10.dp - KavitaSpacing.tabBarHeight - 8.dp
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = KavitaSpacing.tabBarSideInset,
                        end = KavitaSpacing.tabBarSideInset,
                        bottom = KavitaSpacing.tabBarBottomInset,
                    ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(
                            1f,
                        ).height(KavitaSpacing.tabBarHeight)
                        .then(glass)
                        .padding(4.dp)
                        .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val labelSize = tabLabelSize(items, cellWidth = barWidth / items.size)
                items.forEachIndexed { index, item ->
                    val cell = Modifier.weight(1f).height(56.dp)
                    TabCell(item, index == selectedIndex, labelSize, { onSelect(index) }, cell)
                }
            }
            SearchButton(searchLabel, onSearch, glass)
        }
    }
}

/** [derived] DESIGN.md §tablet: from 840dp the tab bar becomes a floating rail on the left, Search on top. */
@Composable
fun FloatingTabRail(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    searchLabel: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = glass()
    Column(
        modifier.systemBarsPadding().padding(start = KavitaSpacing.tabBarSideInset),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        SearchButton(searchLabel, onSearch, glass)
        Column(
            Modifier
                .width(KavitaSpacing.tabBarHeight)
                .then(glass)
                .padding(4.dp)
                .selectableGroup(),
        ) {
            val labelSize = tabLabelSize(items, cellWidth = KavitaSpacing.tabBarHeight - 8.dp)
            items.forEachIndexed { index, item ->
                val cell = Modifier.fillMaxWidth().height(64.dp)
                TabCell(item, index == selectedIndex, labelSize, { onSelect(index) }, cell)
            }
        }
    }
}

/** Width the rail takes from the content, inset included. */
val TabRailWidth = KavitaSpacing.tabBarHeight + KavitaSpacing.tabBarSideInset * 2

@Composable
private fun glass(): Modifier {
    val colors = Kavita.colors
    val ring = if (colors.isDark) colors.hairline else Color.Black.copy(alpha = 0.10f)
    return Modifier
        .cssShadow(KavitaShapes.pill, 10.dp, 30.dp, colors.tabBarShadow.alpha)
        .clip(KavitaShapes.pill)
        .background(colors.tabBar)
        .border(0.5.dp, ring, KavitaShapes.pill)
}

@Composable
private fun TabCell(
    item: TabItem,
    selected: Boolean,
    labelSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = Kavita.colors
    val bg by animateColorAsState(
        if (selected) colors.accentContainer else Color.Transparent,
        tween(TAB_ANIM_MS),
        label = "tab",
    )
    val fg = if (selected) colors.accent else colors.tabInactive
    Column(
        modifier =
            modifier
                .clip(
                    KavitaShapes.pill,
                ).background(bg)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(if (selected) item.selectedIcon else item.icon, null, Modifier.size(24.dp), tint = fg)
        BasicText(
            item.label,
            Modifier.padding(horizontal = TAB_LABEL_INSET),
            style = Kavita.type.tabLabel.copy(fontSize = labelSize, textAlign = TextAlign.Center),
            color = { fg },
            maxLines = 1,
        )
    }
}

private val TAB_LABEL_INSET = 5.dp

/**
 * One label size for every tab: the largest (up to the design's 11sp) at which the longest label fits its cell
 * inside the pill's rounded ends. Per-cell auto-size made "Downloads" smaller than its neighbours, and a fixed size
 * clipped it on narrow phones or with a large system font. The floor ignores font scale so it can't overflow.
 */
@Composable
private fun tabLabelSize(
    items: List<TabItem>,
    cellWidth: Dp,
): TextUnit {
    val measurer = rememberTextMeasurer()
    val style = Kavita.type.tabLabel
    val density = LocalDensity.current
    val floor = 7.sp.fixedScale()
    return remember(items, cellWidth, density, style) {
        val available = with(density) { (cellWidth - TAB_LABEL_INSET * 2).toPx() }
        val widest = items.maxOf { measurer.measure(it.label, style, maxLines = 1, softWrap = false).size.width }
        if (widest <= available) {
            style.fontSize
        } else {
            (style.fontSize.value * available / widest * SAFETY).coerceAtLeast(floor.value).sp
        }
    }
}

private const val SAFETY = 0.97f // glyph widths don't scale exactly linearly with size

@Composable
private fun SearchButton(
    label: String,
    onClick: () -> Unit,
    glass: Modifier,
) = Box(
    modifier = Modifier.size(KavitaSpacing.tabBarHeight).then(glass).clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Icon(KavitaIcons.Search, label, Modifier.size(24.dp), tint = Kavita.colors.tabInactive)
}

data class TabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)
